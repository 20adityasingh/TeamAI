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


    private static final java.util.regex.Pattern TAG_SPLIT_PATTERN = java.util.regex.Pattern.compile(
            "(?=<thought|<tool|<message|<file|thought>|tool>|message>|file>)",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern TAG_START_PATTERN = java.util.regex.Pattern.compile(
            "<?(thought|tool|message|file)(?:\\s+([^>]*))?>",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)=\"([^\"]+)\""
    );

    /**
     * Regex to catch leaked tag names followed by > that might appear in free-text blocks.
     */
    private static final Pattern LEAKED_TAG_CLEANUP = Pattern.compile(
            "(?i)</?(thought|message|file|tool)>?"
    );

    public List<ChatEvent> parserChatEvents(String fullResponse, ChatMessage chatMessage) {
        log.info("Parsing chat events from response length: {}", fullResponse != null ? fullResponse.length() : 0);
        List<ChatEvent> events = new ArrayList<>();
        if (fullResponse == null || fullResponse.isEmpty()) return events;

        String[] parts = TAG_SPLIT_PATTERN.split(fullResponse);
        int orderCount = 1;

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;

            java.util.regex.Matcher startMatcher = TAG_START_PATTERN.matcher(trimmedPart);
            if (startMatcher.find() && startMatcher.start() == 0) {
                String tagName = startMatcher.group(1).toLowerCase();
                String attributes = startMatcher.group(2);
                String fullOpenTag = startMatcher.group(0);

                // Content is everything after the opening tag
                String content = trimmedPart.substring(fullOpenTag.length());
                
                // Remove closing tag if present
                String closingTag = "</" + tagName + ">";
                if (content.toLowerCase().endsWith(closingTag.toLowerCase())) {
                    content = content.substring(0, content.length() - closingTag.length());
                } else {
                    // Cleanup any partial closing tags at the very end
                    content = content.replaceAll("</?[a-z]*$", "");
                }

                Map<String, String> mapAttr = extractAttributes(attributes);

                ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                        .status(ChatEventStatus.COMPLETED)
                        .chatMessage(chatMessage)
                        .content(content.trim())
                        .sequenceOrder(orderCount++);

                switch (tagName) {
                    case "thought" -> builder.chatType(ChatEventType.THOUGHT);
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
                }
                events.add(builder.build());
            } else {
                // Untagged free-text block - aggressively clean leaked markers
                String cleanContent = LEAKED_TAG_CLEANUP.matcher(trimmedPart).replaceAll("").trim();
                
                if (!cleanContent.isEmpty()) {
                    events.add(ChatEvent.builder()
                            .chatType(ChatEventType.MESSAGE)
                            .status(ChatEventStatus.COMPLETED)
                            .chatMessage(chatMessage)
                            .content(cleanContent)
                            .sequenceOrder(orderCount++)
                            .build());
                }
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
