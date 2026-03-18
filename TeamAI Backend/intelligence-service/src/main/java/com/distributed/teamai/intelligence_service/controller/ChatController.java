package com.distributed.teamai.intelligence_service.controller;

import com.distributed.teamai.intelligence_service.dto.chat.ChatRequest;
import com.distributed.teamai.intelligence_service.dto.chat.ChatResponse;
import com.distributed.teamai.intelligence_service.service.AiGenerationService;
import com.distributed.teamai.intelligence_service.service.ChatService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ChatController {

        AiGenerationService aiGenerationService;
        ChatService chatService;

        @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Flux<ServerSentEvent<String>> streamChat(
                        @RequestBody ChatRequest request) {
                return aiGenerationService.streamResponse(request.message(), request.projectId())
                                .map(data -> ServerSentEvent.<String>builder()
                                                .data(data)
                                                .build());
        }


        @GetMapping(value = "/projects/{projectId}")
        public ResponseEntity<List<ChatResponse>> getChatHistory(
                        @PathVariable Long projectId) {
                return ResponseEntity.ok(chatService.getProjectChatHistory(projectId));
        }

}
