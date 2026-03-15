package com.distributed.teamai.intelligence_service.consumer;

import com.distributed.teamai.common_lib.enums.ChatEventStatus;
import com.distributed.teamai.common_lib.event.FileStoreResponseEvent;
import com.distributed.teamai.intelligence_service.repository.ChatEventRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class IntelligenceSagaResponseHandler {

    ChatEventRepository chatEventRepository;

    @Transactional
    @KafkaListener(topics = "file-store-responses-event", groupId = "intelligence-group")
    public void handleSagaResponse(FileStoreResponseEvent responseEvent) {

        chatEventRepository.findBySagaId(responseEvent.sagaId()).ifPresent(chatEvent -> {

            if(!ChatEventStatus.PENDING.equals(chatEvent.getStatus())) {
                log.warn("Received response for sagaId {} but chat event is not in PENDING status. Current status: {}",
                        responseEvent.sagaId(), chatEvent.getStatus());
                return;
            }

            if(responseEvent.success()){
                chatEvent.setStatus(ChatEventStatus.COMPLETED);
                log.info("Saga with id {} completed successfully. Updated chat event status to COMPLETED.", responseEvent.sagaId());
            }else {
                chatEvent.setStatus(ChatEventStatus.FAILED);
                log.info("Saga with id {} failed. Updated chat event status to FAILED.", responseEvent.sagaId());
            }

        });

    }

}