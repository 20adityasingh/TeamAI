package com.distributed.teamai.intelligence_service.service;

import reactor.core.publisher.Flux;


public interface AiGenerationService {
    Flux<String> streamResponse(String message, Long projectId);
}
