package com.distributed.teamai.intelligence_service.service;

public interface UsageService {
    void recordTokenUsage(Long userId, int actualToken);
    void checkDailyTokenUsage();
}
