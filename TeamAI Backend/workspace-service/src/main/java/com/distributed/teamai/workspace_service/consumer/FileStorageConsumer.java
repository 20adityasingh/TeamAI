package com.distributed.teamai.workspace_service.consumer;

import com.distributed.teamai.common_lib.event.FileStoreRequestEvent;
import com.distributed.teamai.common_lib.event.FileStoreResponseEvent;
import com.distributed.teamai.workspace_service.entity.ProcessedEvent;
import com.distributed.teamai.workspace_service.repository.ProcessedEventRepository;
import com.distributed.teamai.workspace_service.service.ProjectFileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class FileStorageConsumer {

    ProjectFileService projectFileService;
    KafkaTemplate<String, Object> kafkaTemplate;
    ProcessedEventRepository processedEventRepository;

    @Transactional
    @KafkaListener(topics = "file-store-requests-event", groupId = "workspace-group")
    public void consumeFileStorageEvent(FileStoreRequestEvent requestEvent) {

        if (processedEventRepository.existsBySagaId(requestEvent.sagaId())) {
            log.info("Duplicate event received with sagaId: {}, skipping processing.", requestEvent.sagaId());
            sendResponse(requestEvent, true, null);
            return;
        }

        try {
            log.info("Received file storage request for projectId: {}, filePath: {}", requestEvent.projectId(),
                    requestEvent.filePath());

            log.info("Saving file for projectId: {}, filePath: {}", requestEvent.projectId(), requestEvent.filePath());

            projectFileService.saveFile(requestEvent.projectId(), requestEvent.filePath(), requestEvent.fileContent());

            processedEventRepository.save(new ProcessedEvent(
                    requestEvent.sagaId(),
                    LocalDateTime.now()));

            sendResponse(requestEvent, true, null);
        } catch (Exception e) {
            log.error("Error processing file storage request for projectId: {}, filePath: {}, error: {}",
                    requestEvent.projectId(), requestEvent.filePath(), e.getMessage());
            sendResponse(requestEvent, false, e.getMessage());
        }

    }

    private void sendResponse(FileStoreRequestEvent requestEvent, boolean success, String errorMessage) {

        FileStoreResponseEvent responseEvent = FileStoreResponseEvent.builder()
                .sagaId(requestEvent.sagaId())
                .projectId(requestEvent.projectId())
                .success(success)
                .errorMessage(errorMessage)
                .build();

        kafkaTemplate.send("file-store-responses-event", responseEvent);
    }

}
