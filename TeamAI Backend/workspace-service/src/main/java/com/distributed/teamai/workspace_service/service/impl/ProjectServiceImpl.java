package com.distributed.teamai.workspace_service.service.impl;

import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.enums.ProjectRole;
import com.distributed.teamai.common_lib.error.BadRequestException;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.workspace_service.client.AccountClient;
import com.distributed.teamai.workspace_service.dto.project.ProjectRequest;
import com.distributed.teamai.workspace_service.dto.project.ProjectResponse;
import com.distributed.teamai.workspace_service.dto.project.ProjectSummaryResponse;
import com.distributed.teamai.workspace_service.entity.Project;
import com.distributed.teamai.workspace_service.entity.ProjectMember;
import com.distributed.teamai.workspace_service.entity.ProjectMemberId;
import com.distributed.teamai.workspace_service.mapper.ProjectMapper;
import com.distributed.teamai.workspace_service.repository.ProjectMemberRepository;
import com.distributed.teamai.workspace_service.repository.ProjectRepository;
import com.distributed.teamai.workspace_service.service.ProjectService;
import com.distributed.teamai.workspace_service.service.ProjectTemplateService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    ProjectRepository projectRepository;
    ProjectMapper projectMapper;
    ProjectMemberRepository projectMemberRepository;
    AuthUtils authUtils;
    ProjectTemplateService projectTemplateService;
    AccountClient accountService;

    @Override
    public ProjectResponse createProject(ProjectRequest request) {

        if (!canCreateNewProject()) {
            throw new BadRequestException("You can not create new project with current PLAN, upgrade your PLAN.");
        }

        Long ownerId = authUtils.getCurrentUserId();

        Project project = Project.builder()
                .name(request.name())
                .build();

        projectRepository.save(project);

        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), ownerId);

        ProjectMember projectMember = ProjectMember.builder()
                .projectRole(ProjectRole.OWNER)
                .acceptedAt(Instant.now())
                .invitedAt(Instant.now())
                .id(projectMemberId)
                .project(project)
                .build();

        projectMemberRepository.save(projectMember);

        // Flush to ensure createdAt/updatedAt are populated before returning
        projectRepository.flush();

        // Template initialization is optional - don't fail project creation if MinIO is
        // unavailable
        try {
            projectTemplateService.initializeProjectFromTemplate(project.getId());
        } catch (Exception e) {
            // Log the error but don't fail the project creation
            // The project will be created without template files
            log.warn("Failed to initialize project from template: {}", e.getMessage());
        }

        return projectMapper.toProjectResponse(project);
    }

    @Override
    public List<ProjectSummaryResponse> getUserProjects() {
        Long userId = authUtils.getCurrentUserId();
        var projectWithRoles = projectRepository.findAllAccessibleByUser(userId);

        return projectWithRoles.stream().map(projectWithRole -> projectMapper
                .toProjectSummaryResponse(projectWithRole.getProject(), projectWithRole.getRole())).toList();
    }

    @Override
    @PreAuthorize("@Security.canViewProject(#id)")
    public ProjectSummaryResponse getUserProjectById(Long id) {
        Long userId = authUtils.getCurrentUserId();
        var projectWithRole = projectRepository.findAccessibleProjectByIdWithRole(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        return projectMapper.toProjectSummaryResponse(projectWithRole.getProject(), projectWithRole.getRole());
    }

    @Override
    @PreAuthorize("@Security.canEditProject(#id)")
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(id, userId);

        project.setName(request.name());
        project = projectRepository.save(project);

        return projectMapper.toProjectResponse(project);
    }

    @Override
    @PreAuthorize("@Security.canDeleteProject(#id)")
    public void softDelete(Long id) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(id, userId);

        project.setDeletedAt(Instant.now());
        projectRepository.save(project);
    }

    /// INTERNAL FUNCTION
    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
    }

    private boolean canCreateNewProject() {

        Long userId = authUtils.getCurrentUserId();

        if(userId == null) {
            throw new BadRequestException("User not found.");
        }

        PlanDto plan = accountService.getCurrentSubscriptionPlan();

        int maxAllowedProjects = plan.maxProjects();
        int ownedProjectsCount = projectMemberRepository.countProjectOwnedByUser(userId);

        return ownedProjectsCount < maxAllowedProjects;

    }
}
