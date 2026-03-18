package com.distributed.teamai.intelligence_service.service.impl;

import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.error.TokenLimitExceededException;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.intelligence_service.client.AccountClient;
import com.distributed.teamai.intelligence_service.entity.UsageLog;
import com.distributed.teamai.intelligence_service.repository.UsageRepository;
import com.distributed.teamai.intelligence_service.service.UsageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class UsageServiceImpl implements UsageService {

    UsageRepository usageRepository;
    AuthUtils authUtils;
    AccountClient accountClient;

    private static final int FREE_PLAN_DAILY_TOKEN_LIMIT = 10_000;

    @Override
    public void recordTokenUsage(Long userId, int actualToken) {

        LocalDate today = LocalDate.now();

        UsageLog todayLog = usageRepository.findByUserIdAndDate(userId, today)
                .orElseGet(() -> UsageLog.builder()
                        .userId(userId)
                        .date(today)
                        .tokensUsed(0)
                        .build());

        todayLog.setTokensUsed(todayLog.getTokensUsed() + actualToken);

        usageRepository.save(todayLog);

    }

    @Override
    public void checkDailyTokenUsage() {
        Long userId = authUtils.getCurrentUserId();
        checkDailyTokenUsage(userId);
    }

    @Override
    public void checkDailyTokenUsage(Long userId) {
        if (userId == null) {
            log.warn("checkDailyTokenUsage called with null userId. Skipping usage check.");
            return;
        }

        PlanDto plan = accountClient.getCurrentSubscriptionPlan(userId);

        int maxTokensPerDay = plan != null && plan.maxTokensPerDay() != null
                ? plan.maxTokensPerDay()
                : FREE_PLAN_DAILY_TOKEN_LIMIT;

        LocalDate today = LocalDate.now();

        UsageLog todayLog = usageRepository.findByUserIdAndDate(userId, today)
                .orElseGet(() -> UsageLog.builder()
                        .userId(userId)
                        .date(today)
                        .tokensUsed(0)
                        .build());

        if (todayLog.getTokensUsed() >= maxTokensPerDay) {
            throw new TokenLimitExceededException(
                    "Daily token limit reached. Please upgrade your plan or wait until tomorrow.");
        }

    }
}
