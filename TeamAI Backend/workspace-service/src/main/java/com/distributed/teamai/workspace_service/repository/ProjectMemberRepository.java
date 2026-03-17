package com.distributed.teamai.workspace_service.repository;

import com.distributed.teamai.common_lib.enums.ProjectRole;
import com.distributed.teamai.workspace_service.entity.ProjectMember;
import com.distributed.teamai.workspace_service.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

        List<ProjectMember> findByIdProjectId(Long ProjectId);

        @Query("""
                        select p from ProjectMember p
                                    where p.id.userId = :memberId and p.id.projectId = :projectId
                        """)
        ProjectMember findByIdUserId(@Param("projectId") Long projectId, @Param("memberId") Long memberId);

        @Query("""
                        select pm.projectRole from ProjectMember pm
                                    where pm.id.projectId = :projectId and pm.id.userId = :userId
                        """)
        Optional<ProjectRole> findRoleByUserIdAndProjectId(@Param("projectId") Long projectId,
                                                           @Param("userId") Long userId);

        @Query("""
                        select count(pm) from ProjectMember pm
                                    where pm.id.userId = :userId and pm.projectRole = 'OWNER'
                        """)
        int countProjectOwnedByUser(@Param("userId") Long userId);

        @Query("""
                        select pm.id.projectId from ProjectMember pm
                                    where pm.id.userId = :userId and pm.projectRole = 'OWNER'
                        """)
        List<Long> findAllOwnedProjectIdsByUser(@Param("userId") Long userId);

        // Pending invites - where acceptedAt is null
        @Query("""
                        select pm from ProjectMember pm
                                    where pm.id.userId = :userId and pm.acceptedAt is null
                                    order by pm.invitedAt desc
                        """)
        List<ProjectMember> findPendingInvitesByUserId(@Param("userId") Long userId);

        @Query("""
                        select pm from ProjectMember pm
                                    where pm.id.projectId = :projectId and pm.id.userId = :userId and pm.acceptedAt is null
                        """)
        Optional<ProjectMember> findPendingInvite(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
