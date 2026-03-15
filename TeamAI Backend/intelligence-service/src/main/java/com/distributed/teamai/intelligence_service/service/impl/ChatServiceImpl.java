package com.distributed.teamai.intelligence_service.service.impl;

import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.intelligence_service.dto.chat.ChatResponse;
import com.distributed.teamai.intelligence_service.entity.ChatMessage;
import com.distributed.teamai.intelligence_service.entity.ChatSession;
import com.distributed.teamai.intelligence_service.entity.ChatSessionId;
import com.distributed.teamai.intelligence_service.mapper.ChatMapper;
import com.distributed.teamai.intelligence_service.repository.ChatMessageRepository;
import com.distributed.teamai.intelligence_service.repository.ChatSessionRepository;
import com.distributed.teamai.intelligence_service.service.ChatService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ChatServiceImpl implements ChatService {

    ChatMessageRepository chatMessageRepository;
    AuthUtils authUtils;
    ChatSessionRepository chatSessionRepository;
    ChatMapper chatMapper;

    @Override
    public List<ChatResponse> getProjectChatHistory(Long projectId) {

        Long userId = authUtils.getCurrentUserId();

        ChatSessionId chatSessionId = new ChatSessionId(projectId, userId);

        ChatSession chatSession = chatSessionRepository.getReferenceById(chatSessionId);

        List<ChatMessage> chatMessageList = chatMessageRepository.findByChatSession(chatSession);

        return chatMapper.toChatResponse(chatMessageList);
    }
}
