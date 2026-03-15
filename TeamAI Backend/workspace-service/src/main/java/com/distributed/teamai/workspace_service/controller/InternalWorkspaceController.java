package com.distributed.teamai.workspace_service.controller;

import com.distributed.teamai.common_lib.dto.FileTreeDto;
import com.distributed.teamai.common_lib.enums.ProjectPermission;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.workspace_service.repository.ProjectMemberRepository;
import com.distributed.teamai.workspace_service.service.ProjectFileService;
import com.distributed.teamai.workspace_service.service.ProjectService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InternalWorkspaceController {

    ProjectService projectService;
    ProjectFileService projectFileService;
    AuthUtils authUtils;
    ProjectMemberRepository projectMemberRepository;

    @GetMapping("/projects/{projectId}/files/tree")
    FileTreeDto getFileTree(@PathVariable("projectId") Long projectId){
        return projectFileService.getFileTree(projectId);
    }

    @GetMapping("/projects/{projectId}/files/content")
    String getFileContent(@PathVariable("projectId") Long projectId, @RequestParam("filePath") String filePath) {
        return projectFileService.getFileContent(projectId, filePath);
    }

    @GetMapping("/projects/{projectId}/permissions")
    public boolean hasPermission(@PathVariable("projectId") Long projectId, @RequestParam("projectPermission") ProjectPermission projectPermission){
        Long userId = authUtils.getCurrentUserId();

        return projectMemberRepository.findRoleByUserIdAndProjectId(projectId, userId)
                .map(role -> role.getPermissions().contains(projectPermission))
                .orElse(false);
    }
}
