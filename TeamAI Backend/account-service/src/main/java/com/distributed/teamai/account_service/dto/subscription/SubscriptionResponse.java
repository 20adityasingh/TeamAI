package com.distributed.teamai.account_service.dto.subscription;

import com.distributed.teamai.common_lib.dto.PlanDto;
import java.time.Instant;

public record SubscriptionResponse(
        PlanDto plan,
        String status,
        Instant currentPeriodEnd,
        Long tokenUsedThisCycle
) {
}
