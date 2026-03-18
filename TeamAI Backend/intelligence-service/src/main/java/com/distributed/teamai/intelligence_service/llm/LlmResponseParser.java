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


    // Scan only opening tags to avoid mis-classifying closing fragments as new events.
    private static final Pattern OPENING_TAG_PATTERN = Pattern.compile(
            "<\\s*(thought|message|tool|file)\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern NEXT_OPENING_TAG_PATTERN = Pattern.compile(
            "<\\s*(thought|message|tool|file)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Supports path="...", path='...', and path=src/index.css style attributes.
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
            Pattern.CASE_INSENSITIVE
    );

    public List<ChatEvent> parserChatEvents(String fullResponse, ChatMessage chatMessage) {
        log.info("Parsing chat events from response length: {}", fullResponse != null ? fullResponse.length() : 0);
        List<ChatEvent> rawEvents = new ArrayList<>();
        if (fullResponse == null || fullResponse.isEmpty()) return rawEvents;

        fullResponse = normalizeMalformedTags(fullResponse);

        Matcher matcher = OPENING_TAG_PATTERN.matcher(fullResponse);
        int cursor = 0;

        while (cursor < fullResponse.length()) {
            matcher.region(cursor, fullResponse.length());
            if (!matcher.find()) {
                break;
            }

            String tagName = matcher.group(1).toLowerCase();
            String attributes = matcher.group(2);
            int contentStart = matcher.end();
            int contentEnd = findContentEnd(fullResponse, tagName, contentStart);
            String content = fullResponse.substring(contentStart, contentEnd).trim();

            // 1. ALWAYS calculate the next cursor position first to prevent infinite loops
            int nextCursor = contentEnd;
            Pattern closingTagPattern = Pattern.compile("</\\s*" + Pattern.quote(tagName) + "\\s*>", Pattern.CASE_INSENSITIVE);
            Matcher closingMatcher = closingTagPattern.matcher(fullResponse);
            if (closingMatcher.find(contentStart) && closingMatcher.start() == contentEnd) {
                nextCursor = closingMatcher.end();
            }
            int newCursorPosition = Math.max(nextCursor, matcher.end());

            // 2. Check if we should ignore this fragment
            boolean isIgnorableEmpty = content.isEmpty() && !tagName.equals("thought")
                    && !tagName.equals("message") && !tagName.equals("tool") && !tagName.equals("file");

            if (!isIgnorableEmpty) {

                // 3. FIX: Only clean up trailing tag fragments for non-file content
                // This prevents deleting valid JSX closing tags (like </div>) in your React code.
                if (!tagName.equals("file")) {
                    content = content.replaceAll("(?i)</?[a-z]+>?$", "").trim();
                }

                Map<String, String> mapAttr = extractAttributes(attributes);

                ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                        .status(ChatEventStatus.COMPLETED)
                        .chatMessage(chatMessage)
                        .content(content);

                boolean isValidEvent = true; // Use a flag instead of 'continue'

                switch (tagName) {
                    case "thought" -> builder.chatType(ChatEventType.THOUGHT);
                    case "message" -> builder.chatType(ChatEventType.MESSAGE);
                    case "file" -> {
                        String path = mapAttr.get("path");
                        if (path == null || path.isEmpty()) {
                            log.warn("Skipping file edit event due to missing path attribute.");
                            isValidEvent = false;
                        } else {
                            builder.chatType(ChatEventType.FILE_EDIT);
                            builder.status(ChatEventStatus.PENDING);
                            builder.filePath(path);

                            // STRIP MARKDOWN BACKTICKS
                            String cleanContent = content
                                    .replaceAll("^```[a-zA-Z]*\\n?", "") // Removes opening ```tsx
                                    .replaceAll("\\n?```$", "")          // Removes closing ```
                                    .trim();
                            builder.content(cleanContent);
                        }
                    }
                    case "tool" -> {
                        builder.chatType(ChatEventType.TOOL_LOG);
                        builder.metadata(mapAttr.get("args"));
                    }
                }

                if (isValidEvent) {
                    rawEvents.add(builder.build());
                }
            }

            // 4. Update the cursor safely
            cursor = newCursorPosition;
        }

        // Fallback: If no tags were found at all, treat the whole thing as one message
        if (rawEvents.isEmpty() && !fullResponse.trim().isEmpty()) {
            rawEvents.add(ChatEvent.builder()
                    .chatType(ChatEventType.MESSAGE)
                    .status(ChatEventStatus.COMPLETED)
                    .chatMessage(chatMessage)
                    .content(fullResponse.trim())
                    .build());
        }

        // Merge adjacent same-type events (collapses redundant Thinking bars and split messages)
        List<ChatEvent> mergedEvents = new ArrayList<>();
        for (ChatEvent event : rawEvents) {
            if (!mergedEvents.isEmpty()) {
                ChatEvent last = mergedEvents.getLast();
                boolean sameType = last.getChatType() == event.getChatType();
                boolean mergeable = sameType && (event.getChatType() == ChatEventType.THOUGHT || event.getChatType() == ChatEventType.MESSAGE);
                boolean sameTarget = Objects.equals(last.getFilePath(), event.getFilePath()) && Objects.equals(last.getMetadata(), event.getMetadata());

                if (mergeable && sameTarget) {
                    // Append content with space if it's thoughts or messages
                    String separator = (last.getContent().isEmpty() || event.getContent().isEmpty()) ? "" : "\n";
                    last.setContent(last.getContent() + separator + event.getContent());
                    continue;
                }
            }
            mergedEvents.add(event);
        }

        // Set sequence orders
        for (int i = 0; i < mergedEvents.size(); i++) {
            mergedEvents.get(i).setSequenceOrder(i + 1);
        }

        log.info("Successfully parsed and merged response into {} events", mergedEvents.size());
        return mergedEvents;
    }


    private Map<String, String> extractAttributes(String attributeString) {
        Map<String, String> attributes = new HashMap<>();
        if (attributeString == null) return attributes;

        Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributeString);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase();
            String value = firstNonNull(matcher.group(2), matcher.group(3), matcher.group(4));
            if (value != null) {
                attributes.put(key, value);
            }
        }
        return attributes;
    }

    private int findContentEnd(String fullResponse, String tagName, int contentStart) {
        Pattern closingTagPattern = Pattern.compile("</\\s*" + Pattern.quote(tagName) + "\\s*>", Pattern.CASE_INSENSITIVE);
        Matcher closingMatcher = closingTagPattern.matcher(fullResponse);
        if (closingMatcher.find(contentStart)) {
            return closingMatcher.start();
        }

        Matcher nextOpeningMatcher = NEXT_OPENING_TAG_PATTERN.matcher(fullResponse);
        if (nextOpeningMatcher.find(contentStart)) {
            return nextOpeningMatcher.start();
        }

        return fullResponse.length();
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeMalformedTags(String input) {
        String normalized = input;

        // Recover missing opening bracket patterns like "thought>" -> "<thought>".
        normalized = normalized.replaceAll(
                "(?i)(^|\\s)(thought|message|tool|file)>",
                "$1<$2>"
        );

        // Recover smashed boundary patterns like "</thoughttool ...>" -> "</thought><tool ...>".
        normalized = normalized.replaceAll(
                "(?i)</(thought|message|tool|file)(?=(thought|message|tool|file)\\b)",
                "</$1><$2"
        );

        return normalized;
    }

}
