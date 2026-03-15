package com.distributed.teamai.workspace_service.controller;

import com.distributed.teamai.workspace_service.dto.member.PendingInviteResponse;
import com.distributed.teamai.workspace_service.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
public class InviteController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<List<PendingInviteResponse>> getPendingInvites() {
        return ResponseEntity.ok(projectMemberService.getPendingInvites());
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<Void> acceptInvite(@PathVariable Long projectId) {
        projectMemberService.acceptInvite(projectId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> declineInvite(@PathVariable Long projectId) {
        projectMemberService.declineInvite(projectId);
        return ResponseEntity.noContent().build();
    }
}
