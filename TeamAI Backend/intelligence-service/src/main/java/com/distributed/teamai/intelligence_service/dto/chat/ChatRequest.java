package com.distributed.teamai.intelligence_service.dto.chat;

public record ChatRequest(
        String message,
        Long projectId
) {
}
