package com.distributed.teamai.intelligence_service.llm.advisors;

import com.distributed.teamai.common_lib.dto.FileNode;
import com.distributed.teamai.intelligence_service.client.WorkspaceClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class FileTreeContextAdvisor implements StreamAdvisor {

    WorkspaceClient workspaceClient;

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {

        Map<String, Object> advisorParam = chatClientRequest.context();

        Long projectId = Long.parseLong(advisorParam.getOrDefault("projectId", 0).toString());

        ChatClientRequest augmentedChatClientRequest = augmentChatClientRequestWithFileTree(chatClientRequest ,projectId);

        return streamAdvisorChain.nextStream(augmentedChatClientRequest);
    }

    private ChatClientRequest augmentChatClientRequestWithFileTree(ChatClientRequest request  ,Long projectId) {

        List<Message> incomingMessage = request.prompt().getInstructions();

        Message systemMessage = incomingMessage.stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .findFirst()
                .orElse(null);

        List<Message> userMessage = incomingMessage.stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                .toList();

        List<Message> allMessage = new ArrayList<>();

        if(systemMessage != null){
            allMessage.add(systemMessage);
        }

        List<FileNode> nodes = workspaceClient.getFileTree(projectId).files();
        
        // Condensed File List (Flat paths)
        String filePathTree = "\n\n--- CURRENT FILE TREE ---\n" + 
                nodes.stream()
                .map(FileNode::path)
                .collect(Collectors.joining("\n"));
        
        allMessage.add(new SystemMessage(filePathTree));
        allMessage.addAll(userMessage);

        return request.mutate()
                .prompt(new Prompt(allMessage, request.prompt().getOptions()))
                .build();
    }

    @Override
    public String getName() {
        return "FileTreeContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
