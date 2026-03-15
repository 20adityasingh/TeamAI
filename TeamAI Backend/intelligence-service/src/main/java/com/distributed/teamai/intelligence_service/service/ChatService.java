package com.distributed.teamai.intelligence_service.service;

import com.distributed.teamai.intelligence_service.dto.chat.ChatResponse;

import java.util.List;

public interface ChatService {

    List<ChatResponse> getProjectChatHistory(Long projectId);

}
