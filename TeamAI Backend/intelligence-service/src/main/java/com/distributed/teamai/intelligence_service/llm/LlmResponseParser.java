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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LlmResponseParser {


    // Aggressive split: split before <, before /, or before naked tag names if they follow a boundary
    private static final java.util.regex.Pattern TAG_SPLIT_PATTERN = java.util.regex.Pattern.compile(
            "(?=<|(?<=\\w)/|(?<=[>/])(?i)(thought|message|tool|file))",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern TAG_START_PATTERN = java.util.regex.Pattern.compile(
            "^<?(thought|tool|message|file)(?:\\s+([^>]*))?>",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)=\"([^\"]+)\""
    );

    /**
     * Regex to catch leaked tag fragments at the start or end of blocks.
     */
    private static final Pattern LEAKED_TAG_CLEANUP = Pattern.compile(
            "(?i)</?(thought|message|file|tool)>?"
    );

    public List<ChatEvent> parserChatEvents(String fullResponse, ChatMessage chatMessage) {
        log.info("Parsing chat events from response length: {}", fullResponse != null ? fullResponse.length() : 0);
        List<ChatEvent> rawEvents = new ArrayList<>();
        if (fullResponse == null || fullResponse.isEmpty()) return rawEvents;

        String[] parts = TAG_SPLIT_PATTERN.split(fullResponse);

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;

            java.util.regex.Matcher startMatcher = TAG_START_PATTERN.matcher(trimmedPart);
            if (startMatcher.find()) {
                String tagName = startMatcher.group(1).toLowerCase();
                String attributes = startMatcher.group(2);
                String fullOpenTag = startMatcher.group(0);

                // Content is after the tag start
                String content = trimmedPart.substring(fullOpenTag.length());
                
                // Remove any closing tag fragment
                content = content.replaceAll("(?i)</" + tagName + ">", "");
                content = content.replaceAll("(?i)</?[a-z]*$", ""); // Clean trailing partials

                Map<String, String> mapAttr = extractAttributes(attributes);

                ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                        .status(ChatEventStatus.COMPLETED)
                        .chatMessage(chatMessage)
                        .content(content.trim());

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
                rawEvents.add(builder.build());
            } else {
                // Untagged free-text block - clean leaked markers
                String cleanContent = LEAKED_TAG_CLEANUP.matcher(trimmedPart).replaceAll("").trim();
                
                if (!cleanContent.isEmpty()) {
                    rawEvents.add(ChatEvent.builder()
                            .chatType(ChatEventType.MESSAGE)
                            .status(ChatEventStatus.COMPLETED)
                            .chatMessage(chatMessage)
                            .content(cleanContent)
                            .build());
                }
            }
        }

        // Merge adjacent same-type events (especially THOUGHT and MESSAGE)
        List<ChatEvent> mergedEvents = new ArrayList<>();
        for (ChatEvent event : rawEvents) {
            if (!mergedEvents.isEmpty()) {
                ChatEvent last = mergedEvents.get(mergedEvents.size() - 1);
                boolean sameType = last.getChatType() == event.getChatType();
                boolean mergeable = sameType && (event.getChatType() == ChatEventType.THOUGHT || event.getChatType() == ChatEventType.MESSAGE);
                // For file/tool, merge only if same metadata/path (unlikely to happen in same stream but safer)
                boolean sameMetadata = Objects.equals(last.getFilePath(), event.getFilePath()) && Objects.equals(last.getMetadata(), event.getMetadata());
                
                if (mergeable && sameMetadata) {
                    last.setContent(last.getContent() + "\n" + event.getContent());
                    continue;
                }
            }
            mergedEvents.add(event);
        }

        // Assign sequence orders
        for (int i = 0; i < mergedEvents.size(); i++) {
            mergedEvents.get(i).setSequenceOrder(i + 1);
        }

        log.info("Parsed and merged into {} events", mergedEvents.size());
        return mergedEvents;
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
