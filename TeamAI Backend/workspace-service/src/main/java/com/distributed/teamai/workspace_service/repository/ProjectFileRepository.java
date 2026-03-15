package com.distributed.teamai.workspace_service.repository;


import com.distributed.teamai.workspace_service.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {
    Optional<ProjectFile> findByProjectIdAndPath(Long project_id, String path);

    List<ProjectFile> findByProjectId(Long projectId);
}
