package com.distributed.teamai.workspace_service.service.impl;

import com.distributed.teamai.common_lib.dto.UserDto;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.workspace_service.client.AccountClient;
import com.distributed.teamai.workspace_service.dto.member.InviteMemberRequest;
import com.distributed.teamai.workspace_service.dto.member.MemberResponse;
import com.distributed.teamai.workspace_service.dto.member.PendingInviteResponse;
import com.distributed.teamai.workspace_service.dto.member.UpdateMemberRoleRequest;
import com.distributed.teamai.workspace_service.entity.Project;
import com.distributed.teamai.workspace_service.entity.ProjectMember;
import com.distributed.teamai.workspace_service.entity.ProjectMemberId;
import com.distributed.teamai.workspace_service.mapper.ProjectMemberMapper;
import com.distributed.teamai.workspace_service.repository.ProjectMemberRepository;
import com.distributed.teamai.workspace_service.repository.ProjectRepository;
import com.distributed.teamai.workspace_service.service.ProjectMemberService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.distributed.teamai.common_lib.enums.ProjectRole.OWNER;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {

    ProjectMemberRepository projectMemberRepository;
    ProjectRepository projectRepository;
    ProjectMemberMapper projectMemberMapper;
    AccountClient accountClient;
    AuthUtils authUtils;

    @Override
    @PreAuthorize("@Security.canViewMembers(#projectId)")
    public List<MemberResponse> getProjectMembers(Long projectId) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(projectId, userId);

        return projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .map(projectMemberMapper::toMemberResponseFromMember)
                .toList();
    }

    @Override
    @PreAuthorize("@Security.canManageMembers(#projectId)")
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(projectId, userId);

        UserDto invitee = accountClient.getUserByUsername(request.username())
                .orElseThrow(
                        () -> new ResourceNotFoundException("User", request.username())
                );

        if (invitee.id().equals(userId)) {
            throw new RuntimeException("Cannot invite yourself");
        }

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, invitee.id());

        if (projectMemberRepository.existsById(projectMemberId)) {
            throw new RuntimeException("Cannot invite once again");
        }

        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .build();

        projectMemberRepository.save(projectMember);

        return projectMemberMapper.toMemberResponseFromMember(projectMember);
    }

    @Override
    @PreAuthorize("@Security.canManageMembers(#projectId)")
    public MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(projectId, userId);

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);

        ProjectMember projectMember = projectMemberRepository.findById(projectMemberId).orElseThrow();

        projectMember.setProjectRole(request.projectRole());

        projectMemberRepository.save(projectMember);

        return projectMemberMapper.toMemberResponseFromMember(projectMember);
    }

    @Override
    @PreAuthorize("@Security.canManageMembers(#projectId)")
    public void removeProjectMember(Long projectId, Long memberId) {
        Long userId = authUtils.getCurrentUserId();

        Project project = getAccessibleProjectById(projectId, userId);

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);

        if (!projectMemberRepository.existsById(projectMemberId)) {
            throw new RuntimeException("Member is not available");
        }

        projectMemberRepository.deleteById(projectMemberId);
    }

    /// INTERNAL FUNCTION
    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId).orElseThrow();
    }

    // === Pending Invites ===

    @Override
    public List<PendingInviteResponse> getPendingInvites() {
        Long userId = authUtils.getCurrentUserId();

        return projectMemberRepository.findPendingInvitesByUserId(userId)
                .stream()
                .map(pm -> new PendingInviteResponse(
                        pm.getProject().getId(),
                        pm.getProject().getName(),
                        getProjectOwnerName(pm.getProject().getId()),
                        pm.getProjectRole(),
                        pm.getInvitedAt()))
                .toList();
    }

    @Override
    public void acceptInvite(Long projectId) {
        Long userId = authUtils.getCurrentUserId();

        ProjectMember pendingInvite = projectMemberRepository.findPendingInvite(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Invite not found or already accepted"));

        pendingInvite.setAcceptedAt(Instant.now());
        projectMemberRepository.save(pendingInvite);
    }

    @Override
    public void declineInvite(Long projectId) {
        Long userId = authUtils.getCurrentUserId();

        ProjectMember pendingInvite = projectMemberRepository.findPendingInvite(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        projectMemberRepository.delete(pendingInvite);
    }

    private String getProjectOwnerName(Long projectId) {
        return projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .filter(pm -> pm.getProjectRole() == OWNER)
                .findFirst()
                .map(pm -> accountClient.getUserById(pm.getId().getUserId()).name())
                .orElse("Unknown");
    }
}
