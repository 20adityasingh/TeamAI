package com.distributed.teamai.workspace_service.service.impl;

import com.distributed.teamai.common_lib.dto.FileNode;
import com.distributed.teamai.common_lib.dto.FileTreeDto;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.workspace_service.entity.Project;
import com.distributed.teamai.workspace_service.entity.ProjectFile;
import com.distributed.teamai.workspace_service.mapper.ProjectFileMapper;
import com.distributed.teamai.workspace_service.repository.ProjectFileRepository;
import com.distributed.teamai.workspace_service.repository.ProjectRepository;
import com.distributed.teamai.workspace_service.service.ProjectFileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class ProjectFileServiceImpl implements ProjectFileService {

    ProjectRepository projectRepository;
    ProjectFileRepository projectFileRepository;
    MinioClient minioClient;
    ProjectFileMapper projectFileMapper;

    @Value("${minio.bucket-name}")
    @NonFinal
    String BUCKET_NAME;

    @Override
    public FileTreeDto getFileTree(Long projectId) {

        List<ProjectFile> projectFileList = projectFileRepository.findByProjectId(projectId);

        List<FileNode> projectFiles = projectFileMapper.toFileNodeList(projectFileList);

        return new FileTreeDto(projectFiles);

    }

    @Override
    public String getFileContent(Long projectId, String path) {

        String objectName = projectId + "/" + path;
        log.info("Attempting to get file content for object: {} in bucket: {}", objectName, BUCKET_NAME);

        try{
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            );
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read File Content for object {}: {}", objectName, e.getMessage());
            throw new RuntimeException("Failed to read File Content: "+e);
        }

    }

    @Transactional
    @Override
    public void saveFile(Long projectId, String filePath, String fileContent) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString())
        );

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String objectKey = projectId + "/" + cleanPath;

        try {
            byte[] contentByte = fileContent.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentByte);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .contentType(determineContentType(cleanPath))
                            .stream(inputStream, contentByte.length, -1)
                            .object(objectKey)
                            .build()
            );

            ProjectFile projectFile = projectFileRepository.findByProjectIdAndPath(projectId, cleanPath)
                    .orElseGet(() ->
                            ProjectFile.builder()
                                    .project(project)
                                    .path(cleanPath)
                                    .minioObjectKey(objectKey)
                                    .createdAt(Instant.now())
                                    .build()
                    );
            projectFile.setUpdatedAt(Instant.now());
            projectFileRepository.save(projectFile);

            log.info("Saved file: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to save file {}", objectKey, e);
            throw new RuntimeException("File save failed ", e);
        }

    }

    private String determineContentType (String path){
        String type = URLConnection.guessContentTypeFromName(path);

        if(type != null) return type;
        if(path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")) return "text/javascript";
        if(path.endsWith(".json")) return "application/json";
        if(path.endsWith(".css")) return "text/css";

        return "text/plain";
    }
}
