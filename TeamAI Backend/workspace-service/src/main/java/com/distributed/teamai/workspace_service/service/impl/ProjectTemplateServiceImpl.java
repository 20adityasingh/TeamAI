package com.distributed.teamai.workspace_service.service.impl;

import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.workspace_service.entity.Project;
import com.distributed.teamai.workspace_service.entity.ProjectFile;
import com.distributed.teamai.workspace_service.repository.ProjectFileRepository;
import com.distributed.teamai.workspace_service.repository.ProjectRepository;
import com.distributed.teamai.workspace_service.service.ProjectTemplateService;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    private final MinioClient minioClient;
    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;

    @Value("${minio.bucket-name}")
    private String TARGET_BUCKET;

    private static final String TEMPLATE_BUCKET = "starter-projects";
    private static final String TEMPLATE_NAME = "react-vite-tailwind-daisyui-starter-main";

    @Override
    public void initializeProjectFromTemplate(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("ProjectID", projectId.toString()));

        try {
            // Ensure target bucket exists
            boolean targetBucketExists = minioClient
                    .bucketExists(BucketExistsArgs.builder().bucket(TARGET_BUCKET).build());
            if (!targetBucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(TARGET_BUCKET).build());
                log.info("Created target bucket: {}", TARGET_BUCKET);
            }

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(TEMPLATE_BUCKET)
                            .prefix(TEMPLATE_NAME + "/")
                            .recursive(true)
                            .build());

            List<ProjectFile> filesToSave = new ArrayList<>(); // for metadata in postgres db
            int fileCount = 0;

            for (Result<Item> result : results) {
                Item item = result.get();
                String sourceKey = item.objectName();

                // Skip directory entries (they end with / or have isDir flag)
                if (item.isDir() || sourceKey.endsWith("/")) {
                    log.info("Skipping directory: {}", sourceKey);
                    continue;
                }

                String cleanPath = sourceKey.replaceFirst(TEMPLATE_NAME + "/", "");

                // Skip empty paths
                if (cleanPath.isEmpty()) {
                    continue;
                }

                String destKey = projectId + "/" + cleanPath;

                log.info("Copying {} to {}", sourceKey, destKey);

                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(TARGET_BUCKET)
                                .object(destKey)
                                .source(
                                        CopySource.builder()
                                                .bucket(TEMPLATE_BUCKET)
                                                .object(sourceKey)
                                                .build())
                                .build());

                ProjectFile pf = ProjectFile.builder()
                        .project(project)
                        .path(cleanPath)
                        .minioObjectKey(destKey)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                filesToSave.add(pf);
                fileCount++;
            }

            if (!filesToSave.isEmpty()) {
                projectFileRepository.saveAll(filesToSave);
                log.info("Successfully initialized project {} from template with {} files", projectId, fileCount);
            } else {
                log.warn("No files found in template bucket {} with prefix {}", TEMPLATE_BUCKET, TEMPLATE_NAME);
            }

        } catch (Exception e) {
            log.error("Failed to initialize project from template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize project from template", e);
        }

    }
}
