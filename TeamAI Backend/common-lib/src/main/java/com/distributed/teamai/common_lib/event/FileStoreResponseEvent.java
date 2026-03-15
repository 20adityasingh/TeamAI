package com.distributed.teamai.common_lib.event;

import lombok.Builder;

@Builder
public record FileStoreResponseEvent(
        String sagaId,

        boolean success,

        String errorMessage,

        Long projectId
) {
}
