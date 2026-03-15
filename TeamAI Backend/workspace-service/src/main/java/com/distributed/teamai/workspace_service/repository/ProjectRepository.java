package com.distributed.teamai.workspace_service.repository;

import com.distributed.teamai.common_lib.enums.ProjectRole;
import com.distributed.teamai.workspace_service.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

        @Query("""
                        select p as project, pm.projectRole as role from Project p
                            join ProjectMember pm on p.id = pm.id.projectId
                                                where pm.id.userId = :userId and p.deletedAt is null and pm.acceptedAt is not null
                                            order by p.updatedAt desc
                        """)
        List<ProjectWithRole> findAllAccessibleByUser(@Param("userId") Long userId);

        @Query("""
                        select p from Project p
                                                where p.id = :projectId and p.deletedAt is null and exists (
                                                                select 1 from ProjectMember pm
                                                                            where pm.id.userId = :userId and pm.id.projectId = p.id and pm.acceptedAt is not null
                                                            )
                        """)
        Optional<Project> findAccessibleProjectById(@Param("projectId") Long Id, @Param("userId") Long userId);

        @Query("""
                        select p as project, pm.projectRole as role from Project p
                            join ProjectMember pm on p.id = pm.id.projectId
                                                where p.id = :projectId and p.deletedAt is null and pm.id.userId = :userId and pm.acceptedAt is not null
                        """)
        Optional<ProjectWithRole> findAccessibleProjectByIdWithRole(@Param("projectId") Long Id,
                        @Param("userId") Long userId);

        interface ProjectWithRole {
                Project getProject();

                ProjectRole getRole();
        }
}
