package com.distributed.teamai.workspace_service.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "processed_events")
@Data
public class ProcessedEvent {

    @Id
    private String sagaId;

    private LocalDateTime processedAt;

}
