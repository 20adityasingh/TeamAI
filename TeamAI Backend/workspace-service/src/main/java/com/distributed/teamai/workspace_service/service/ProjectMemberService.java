package com.distributed.teamai.workspace_service.service;

import com.distributed.teamai.workspace_service.dto.member.InviteMemberRequest;
import com.distributed.teamai.workspace_service.dto.member.MemberResponse;
import com.distributed.teamai.workspace_service.dto.member.PendingInviteResponse;
import com.distributed.teamai.workspace_service.dto.member.UpdateMemberRoleRequest;

import java.util.List;

public interface ProjectMemberService {
    List<MemberResponse> getProjectMembers(Long projectId);

    MemberResponse inviteMember(Long projectId, InviteMemberRequest request);

    MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request);

    void removeProjectMember(Long projectId, Long memberId);

    // Pending invites
    List<PendingInviteResponse> getPendingInvites();

    void acceptInvite(Long projectId);

    void declineInvite(Long projectId);
}
