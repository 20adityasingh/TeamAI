package com.distributed.teamai.intelligence_service.dto.chat;

import com.distributed.teamai.common_lib.enums.MessageRole;

import java.time.Instant;
import java.util.List;

public record ChatResponse(

        Long id,

        String content,

        MessageRole role,

        List<ChatEventResponse> events,

        Integer tokenUsed,

        Instant createdAt

) {
}
