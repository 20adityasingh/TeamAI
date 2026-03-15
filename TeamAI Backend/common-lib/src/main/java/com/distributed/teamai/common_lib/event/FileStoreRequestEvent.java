package com.distributed.teamai.common_lib.event;

public record FileStoreRequestEvent(
        Long userId,
        Long projectId,
        String sagaId,
        String filePath,
        String fileContent
) {
}
