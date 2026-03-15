package com.distributed.teamai.workspace_service.dto.member;

import com.distributed.teamai.common_lib.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(


        @NotNull
        ProjectRole projectRole
) {
}
