package com.distributed.teamai.account_service.dto.subscription;

public record PlanLimitsResponse(
        String planName,
        Integer maxTokensPerDay,
        Integer maxProjects,
        Integer unlimitedAi
) {
}
