package com.distributed.teamai.workspace_service.security;

import com.distributed.teamai.common_lib.enums.ProjectPermission;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.workspace_service.repository.ProjectMemberRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import static com.distributed.teamai.common_lib.enums.ProjectPermission.*;


@Component("Security")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SecurityExpression {

    AuthUtils authUtils;
    ProjectMemberRepository projectMemberRepository;

    private boolean hasPermission(Long projectId, ProjectPermission projectPermission){
        Long userId = authUtils.getCurrentUserId();

        return projectMemberRepository.findRoleByUserIdAndProjectId(projectId, userId)
                .map(role -> role.getPermissions().contains(projectPermission))
                .orElse(false);
    }

    public boolean canViewProject(Long projectId){
        return hasPermission(projectId, VIEW);
    }

    public boolean canEditProject(Long projectId){
        return hasPermission(projectId, EDIT);
    }

    public boolean canDeleteProject(Long projectId){
        return hasPermission(projectId, DELETE);
    }

    public boolean canViewMembers(Long projectId){
        return hasPermission(projectId, VIEW_MEMBER);
    }

    public boolean canManageMembers(Long projectId){
        return hasPermission(projectId, MANAGE_MEMBER);
    }
}
