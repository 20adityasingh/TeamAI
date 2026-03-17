package com.distributed.teamai.common_lib.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlanDto(
        Long id,
        String name,
        Integer maxProjects,
        Integer maxTokensPerDay,
        Integer maxPreviews,
        Boolean unlimitedAi,
        @JsonProperty("Price")
        String Price
) {
}
