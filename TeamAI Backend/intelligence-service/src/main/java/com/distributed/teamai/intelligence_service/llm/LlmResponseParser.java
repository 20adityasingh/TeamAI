package com.distributed.teamai.intelligence_service.llm;

import com.distributed.teamai.common_lib.enums.ChatEventStatus;
import com.distributed.teamai.common_lib.enums.ChatEventType;
import com.distributed.teamai.intelligence_service.entity.ChatEvent;
import com.distributed.teamai.intelligence_service.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LlmResponseParser {


    private static final Pattern GENERIC_TAG_PATTERN = Pattern.compile(
            "<(message|file|tool)([^>]*)>([\\s\\S]*?)(?:</\\1>|$)",
            Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern TAG_CLEANUP_PATTERN = java.util.regex.Pattern.compile(
            "</?(message|file|tool)[^>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)=\"([^\"]+)\""
    );

    public List<ChatEvent> parserChatEvents(String fullResponse, ChatMessage chatMessage) {
        log.info("Parsing chat events from response length: {}", fullResponse != null ? fullResponse.length() : 0);
        List<ChatEvent> events = new ArrayList<>();
        if (fullResponse == null || fullResponse.isEmpty()) return events;

        int orderCount = 1;
        int lastIndex = 0;

        Matcher matcher = GENERIC_TAG_PATTERN.matcher(fullResponse);

        while (matcher.find()) {
            // 1. Capture and Clean Preamble
            String preamble = fullResponse.substring(lastIndex, matcher.start()).trim();
            String cleanPreamble = TAG_CLEANUP_PATTERN.matcher(preamble).replaceAll("").trim();
            
            if (!cleanPreamble.isEmpty()) {
                events.add(ChatEvent.builder()
                        .chatType(ChatEventType.MESSAGE)
                        .status(ChatEventStatus.COMPLETED)
                        .chatMessage(chatMessage)
                        .content(cleanPreamble)
                        .sequenceOrder(orderCount++)
                        .build());
            }

            // 2. Capture Tag Content
            String tagName = matcher.group(1).toLowerCase();
            String attributes = matcher.group(2);
            String content = matcher.group(3).trim();
            lastIndex = matcher.end();

            Map<String, String> mapAttr = extractAttributes(attributes);

            ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                    .status(ChatEventStatus.COMPLETED)
                    .chatMessage(chatMessage)
                    .content(content)
                    .sequenceOrder(orderCount++);

            switch (tagName) {
                case "message" -> builder.chatType(ChatEventType.MESSAGE);
                case "file" -> {
                    builder.chatType(ChatEventType.FILE_EDIT);
                    builder.status(ChatEventStatus.PENDING);
                    builder.filePath(mapAttr.get("path"));
                }
                case "tool" -> {
                    builder.chatType(ChatEventType.TOOL_LOG);
                    builder.metadata(mapAttr.get("args"));
                }
                default -> {
                    continue;
                }
            }

            events.add(builder.build());
        }

        // 3. Capture any trailing text AFTER the last tag
        if (lastIndex < fullResponse.length()) {
            String trailing = fullResponse.substring(lastIndex).trim();
            String cleanTrailing = TAG_CLEANUP_PATTERN.matcher(trailing).replaceAll("").trim();
            
            if (!cleanTrailing.isEmpty()) {
                events.add(ChatEvent.builder()
                        .chatType(ChatEventType.MESSAGE)
                        .status(ChatEventStatus.COMPLETED)
                        .chatMessage(chatMessage)
                        .content(cleanTrailing)
                        .sequenceOrder(orderCount++)
                        .build());
            }
        }

        log.info("Parsed {} events from response", events.size());
        return events;
    }


    private Map<String, String> extractAttributes(String attributeString) {
        Map<String, String> attributes = new HashMap<>();
        if (attributeString == null) return attributes;

        Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributeString);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

}
