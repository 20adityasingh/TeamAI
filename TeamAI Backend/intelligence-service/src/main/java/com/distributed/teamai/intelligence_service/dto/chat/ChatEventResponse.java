package com.distributed.teamai.intelligence_service.dto.chat;

import com.distributed.teamai.common_lib.enums.ChatEventType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatEventResponse(
                Long id,

                @JsonProperty("type")
                ChatEventType chatType,

                Integer sequenceOrder,

                String content,

                String filePath,

                String metadata) {
}
