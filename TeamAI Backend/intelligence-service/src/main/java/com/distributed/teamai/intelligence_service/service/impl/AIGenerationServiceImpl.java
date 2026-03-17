package com.distributed.teamai.intelligence_service.service.impl;

import com.distributed.teamai.common_lib.enums.ChatEventStatus;
import com.distributed.teamai.common_lib.enums.ChatEventType;
import com.distributed.teamai.common_lib.enums.MessageRole;
import com.distributed.teamai.common_lib.event.FileStoreRequestEvent;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.intelligence_service.security.SecurityExpression;
import com.distributed.teamai.intelligence_service.client.WorkspaceClient;
import com.distributed.teamai.intelligence_service.entity.*;
import com.distributed.teamai.intelligence_service.llm.LlmResponseParser;
import com.distributed.teamai.intelligence_service.llm.Prompt;
import com.distributed.teamai.intelligence_service.llm.advisors.FileTreeContextAdvisor;
import com.distributed.teamai.intelligence_service.llm.tools.CodeGenerationTool;
import com.distributed.teamai.intelligence_service.repository.*;
import com.distributed.teamai.intelligence_service.service.AiGenerationService;
import com.distributed.teamai.intelligence_service.service.UsageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class AIGenerationServiceImpl implements AiGenerationService {

    ChatClient chatClient;
    AuthUtils authUtils;
    static Pattern FILE_TAG_PATTERN = Pattern.compile("<file path=\"([^\"]+)\">(.*?)</file>", Pattern.DOTALL);
    FileTreeContextAdvisor fileTreeContextAdvisor;
    LlmResponseParser llmResponseParser;
    ChatSessionRepository chatSessionRepository;
    ChatMessageRepository chatMessageRepository;
    ChatEventRepository chatEventRepository;
    SecurityExpression securityExpression;
    UsageService usageService;
    WorkspaceClient workspaceClient;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Flux<String> streamResponse(String message, Long projectId) {

        Long userId = authUtils.getCurrentUserId();
        usageService.checkDailyTokenUsage(userId);

        if (!securityExpression.canEditProject(projectId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied");
        }

        ChatSession chatSession = createChatSessionIfNotExist(userId, projectId);

        Map<String, Object> advisorParams = Map.of(
                "userId", userId,
                "projectId", projectId);

        CodeGenerationTool readFiles = new CodeGenerationTool(workspaceClient, projectId);

        StringBuilder fullResponseBuffer = new StringBuilder();
        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(0L);
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        return chatClient.prompt()
                .system(Prompt.CODE_GENERATION_SYSTEM_PROMPT)
                .user(message)
                .tools(readFiles)
                .advisors(
                        advisorSpec -> {
                            advisorSpec.params(advisorParams);
                            advisorSpec.advisors(fileTreeContextAdvisor);
                        })
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }

                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        log.info("Received streaming response for Project ID: {}: {}", projectId,
                                response.getResult().getOutput().getText());
                        String content = response.getResult().getOutput().getText();

                        if (content != null && !content.isEmpty()) {
                            if (endTime.get() == 0) {
                                endTime.set(System.currentTimeMillis());
                            }
                            fullResponseBuffer.append(content);
                        }
                    } else {
                        log.info("Received streaming response for Project ID: {} without results", projectId);
                    }

                })
                .doOnComplete(() -> {
                    Long duration = (endTime.get() - startTime.get()) / 1000;
                    Schedulers.boundedElastic().schedule(() -> {
                        try {
                            finalizeChats(userId, message, chatSession, fullResponseBuffer.toString(), duration,
                                    usageRef.get());
                        } catch (Exception e) {
                            log.error("Failed to finalize chats for project {}: {}", projectId, e.getMessage(), e);
                        }
                    });
                })
                .doOnError(error -> {
                    log.error("Error during streaming for Project ID: {}", projectId, error);
                })
                .mapNotNull(response -> {
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        return response.getResult().getOutput().getText();
                    }
                    return "";
                })
                .filter(Objects::nonNull)
                .retryWhen(reactor.util.retry.Retry.backoff(2, java.time.Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof org.springframework.ai.tool.execution.ToolExecutionException)
                        .doBeforeRetry(retrySignal -> log.warn("Retrying due to tool execution error, attempt {}",
                                retrySignal.totalRetries() + 1))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("Max retries exhausted for tool execution, returning error");
                            return new RuntimeException("AI tool execution failed after retries. Please try again.");
                        }))
                .onErrorResume(error -> {
                    log.error("Streaming failed for Project ID: {}. Root cause: ", projectId, error);
                    return Flux.just(
                            "\n\n⚠️ Sorry, I encountered an error while processing your request. Please try again.");
                });
    }

    private void finalizeChats(Long userId, String userMessage, ChatSession chatSession, String fullResponse,
            Long duration, Usage usage) {

        try {
            Long projectId = chatSession.getId().getProjectId();
            log.info("Finalizing chats for project {} and user {}", projectId, userId);

            usageService.checkDailyTokenUsage(userId);    

            if (usage != null) {
                int totalTokens = usage.getCompletionTokens() + usage.getPromptTokens();
                usageService.recordTokenUsage(userId, totalTokens);
            }

            chatMessageRepository.save(
                    ChatMessage.builder()
                            .content(userMessage)
                            .chatSession(chatSession)
                            .role(MessageRole.USER)
                            .tokensUsed(usage != null ? usage.getPromptTokens() : 0)
                            .build());

            ChatMessage assistantChatMessage = ChatMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .content(fullResponse)
                    .chatSession(chatSession)
                    .tokensUsed(usage != null ? usage.getCompletionTokens() : 0)
                    .build();

            assistantChatMessage = chatMessageRepository.save(assistantChatMessage);


        List<ChatEvent> events = llmResponseParser.parserChatEvents(fullResponse, assistantChatMessage);

        events.addFirst(ChatEvent.builder()
                .chatType(ChatEventType.THOUGHT)
                .status(ChatEventStatus.COMPLETED)
                .chatMessage(assistantChatMessage)
                .content("Thought for " + duration + "s")
                .sequenceOrder(0)
                .build());

        events.stream()
                .filter(e -> e.getChatType() == ChatEventType.FILE_EDIT)
                .forEach(e -> {
                    String sagaId = UUID.randomUUID().toString();
                    e.setSagaId(sagaId);
                    FileStoreRequestEvent fileStoreRequestEvent = new FileStoreRequestEvent(
                            projectId,
                            userId,
                            sagaId,
                            e.getFilePath(),
                            e.getContent()
                    );
                    log.info("Emitting FileStoreRequestEvent for file: {} in project: {}", e.getFilePath(), projectId);
                    kafkaTemplate.send("file-store-requests-event", "project-"+projectId ,fileStoreRequestEvent);
                });

            chatEventRepository.saveAll(events);
            log.info("Successfully saved chat messages and {} events for project {}", events.size(), projectId);

        } catch (Exception e) {
            log.error("Critical error in finalizeChats: {}", e.getMessage(), e);
            throw e; 
        }

    }


    private ChatSession createChatSessionIfNotExist(Long userId, Long projectId) {

        ChatSessionId chatSessionId = new ChatSessionId(projectId, userId);

        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);

        if (chatSession == null) {
            chatSession = ChatSession.builder()
                    .id(chatSessionId)
                    .build();

            chatSession = chatSessionRepository.save(chatSession);
        }

        return chatSession;

    }

}
