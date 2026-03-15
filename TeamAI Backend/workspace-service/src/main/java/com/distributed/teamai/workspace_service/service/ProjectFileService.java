package com.distributed.teamai.workspace_service.service;


import com.distributed.teamai.common_lib.dto.FileTreeDto;
import com.distributed.teamai.workspace_service.dto.project.FileContentResponse;

public interface ProjectFileService {
    FileTreeDto getFileTree(Long projectId);

    String getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);
}
