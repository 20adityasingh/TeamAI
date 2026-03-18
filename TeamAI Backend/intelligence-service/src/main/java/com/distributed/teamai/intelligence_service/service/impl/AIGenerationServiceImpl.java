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

import java.time.Duration;
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
    static Pattern FILE_FIX_INTENT_PATTERN = Pattern.compile(
            "(?is)(?:\\b(?:fix|edit|update|create|modify|change|rewrite|refactor|remove|add)\\b.*\\b(?:file|css|tsx|ts|js|json|html)\\b)|(?:\\bsrc/[^\\s]+)|(?:\\bindex\\.css\\b)"
    );

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

        Flux<String> aiStream = chatClient.prompt()
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
                    if (fullResponseBuffer.length() > 0 && endTime.get() > 0) {
                        Long duration = (endTime.get() - startTime.get()) / 1000;
                        log.info("Finalizing stream onComplete. Duration: {}s", duration);
                        String fullResponse = fullResponseBuffer.toString();
                        Schedulers.boundedElastic().schedule(() -> {
                            try {
                                finalizeChats(userId, message, chatSession, fullResponse, duration,
                                        usageRef.get());
                            } catch (Exception e) {
                                log.error("Failed to finalize chats for project {}: {}", projectId, e.getMessage(), e);
                            }
                        });
                    }
                })
                .doOnError(error -> {
                    log.error("Streaming error for Project ID: {}. Error: ", projectId, error);
                    if (fullResponseBuffer.length() > 0 && endTime.get() > 0) {
                        Long duration = (endTime.get() - startTime.get()) / 1000;
                        String fullResponse = fullResponseBuffer.toString();
                        Schedulers.boundedElastic().schedule(() -> {
                            try {
                                finalizeChats(userId, message, chatSession, fullResponse, duration,
                                        usageRef.get());
                            } catch (Exception e) {
                                log.error("Failed to finalize chats (on error) for project {}: {}", projectId, e.getMessage(), e);
                            }
                        });
                    }
                })
                .doFinally(signal -> {
                    log.info("Stream finalized for Project ID: {} with signal: {}", projectId, signal);
                    fullResponseBuffer.setLength(0);
                })
                .mapNotNull(response -> {
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        String text = response.getResult().getOutput().getText();
                        return text != null ? text : "";
                    }
                    return "";
                })
                .filter(text -> text != null && !text.isEmpty())
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

        return aiStream;
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

        if (shouldRetryForMissingFileEdit(userMessage, events)) {
            String retryResponse = generateFileEditRecoveryResponse(userMessage, projectId);
            if (retryResponse != null && !retryResponse.isBlank()) {
                List<ChatEvent> retryEvents = llmResponseParser.parserChatEvents(retryResponse, assistantChatMessage);
                boolean retryProducedFileEdit = retryEvents.stream()
                        .anyMatch(event -> event.getChatType() == ChatEventType.FILE_EDIT);

                if (retryProducedFileEdit) {
                    log.info("Recovery retry produced FILE_EDIT events for project {}", projectId);
                    events.addAll(retryEvents);
                    assistantChatMessage.setContent(fullResponse + "\n" + retryResponse);
                    assistantChatMessage.setTokensUsed(assistantChatMessage.getTokensUsed());
                    chatMessageRepository.save(assistantChatMessage);
                } else {
                    log.warn("Recovery retry did not produce FILE_EDIT events for project {}", projectId);
                }
            }
        }

        ensureTerminalMessageEvent(events, assistantChatMessage);
        resetSequenceOrders(events);

        events.stream()
                .filter(e -> e.getChatType() == ChatEventType.FILE_EDIT)
                .forEach(e -> {
                    String sagaId = UUID.randomUUID().toString();
                    e.setSagaId(sagaId);
                    FileStoreRequestEvent fileStoreRequestEvent = new FileStoreRequestEvent(
                            userId,
                            projectId,
                            sagaId,
                            e.getFilePath(),
                            e.getContent()
                    );
                    log.info("Emitting FileStoreRequestEvent for file: {} in project: {}", e.getFilePath(), projectId);
                    kafkaTemplate.send("file-store-requests-event", "project-" + projectId, fileStoreRequestEvent)
                            .whenComplete((result, ex) -> {
                                if (ex == null) {
                                    log.info("Successfully sent FileStoreRequestEvent for file: {}", e.getFilePath());
                                } else {
                                    log.error("Failed to send FileStoreRequestEvent for file: {}. Error: {}", e.getFilePath(), ex.getMessage());
                                }
                            });
                });

        log.info("Saving {} chat events to database for project {}", events.size(), projectId);
        chatEventRepository.saveAll(events);
        log.info("Successfully saved chat messages and events for project {}", projectId);

        } catch (Exception e) {
            log.error("Critical error in finalizeChats: {}", e.getMessage(), e);
            throw e; 
        }

    }

    private boolean shouldRetryForMissingFileEdit(String userMessage, List<ChatEvent> events) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        boolean fileFixIntent = FILE_FIX_INTENT_PATTERN.matcher(userMessage).find();
        boolean hasFileEdit = events.stream().anyMatch(event -> event.getChatType() == ChatEventType.FILE_EDIT);
        return fileFixIntent && !hasFileEdit;
    }

    private String generateFileEditRecoveryResponse(String userMessage, Long projectId) {
        try {
            Long userId = authUtils.getCurrentUserId();
            Map<String, Object> advisorParams = Map.of(
                    "userId", userId,
                    "projectId", projectId
            );
            CodeGenerationTool readFiles = new CodeGenerationTool(workspaceClient, projectId);
            String retryPrompt = userMessage + "\n\n" +
                    "Your previous response was malformed. Return valid tags only. " +
                    "If code changes are requested, include at least one <file path=\"...\"> block.";

            StringBuilder retryBuffer = new StringBuilder();

            chatClient.prompt()
                    .system(Prompt.CODE_GENERATION_SYSTEM_PROMPT)
                    .user(retryPrompt)
                    .tools(readFiles)
                    .advisors(advisorSpec -> {
                        advisorSpec.params(advisorParams);
                        advisorSpec.advisors(fileTreeContextAdvisor);
                    })
                    .stream()
                    .chatResponse()
                    .map(response -> {
                        if (response.getResults() != null && !response.getResults().isEmpty()) {
                            String text = response.getResult().getOutput().getText();
                            return text != null ? text : "";
                        }
                        return "";
                    })
                    .filter(text -> text != null && !text.isEmpty())
                    .doOnNext(retryBuffer::append)
                    .blockLast(Duration.ofSeconds(45));

            return retryBuffer.toString();
        } catch (Exception ex) {
            log.warn("Failed to generate recovery response for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private void ensureTerminalMessageEvent(List<ChatEvent> events, ChatMessage assistantChatMessage) {
        boolean hasMessageOrFile = events.stream().anyMatch(event ->
                event.getChatType() == ChatEventType.MESSAGE || event.getChatType() == ChatEventType.FILE_EDIT
        );

        if (!hasMessageOrFile) {
            events.add(ChatEvent.builder()
                    .chatType(ChatEventType.MESSAGE)
                    .status(ChatEventStatus.COMPLETED)
                    .chatMessage(assistantChatMessage)
                    .content("I hit malformed output while preparing the final response. Please retry once.")
                    .build());
        }
    }

    private void resetSequenceOrders(List<ChatEvent> events) {
        for (int i = 0; i < events.size(); i++) {
            events.get(i).setSequenceOrder(i + 1);
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
