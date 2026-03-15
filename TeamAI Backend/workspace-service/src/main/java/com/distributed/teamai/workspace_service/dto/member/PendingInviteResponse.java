package com.distributed.teamai.workspace_service.dto.member;


import com.distributed.teamai.common_lib.enums.ProjectRole;

import java.time.Instant;

public record PendingInviteResponse(
        Long projectId,
        String projectName,
        String inviterName,
        ProjectRole role,
        Instant invitedAt) {
}
