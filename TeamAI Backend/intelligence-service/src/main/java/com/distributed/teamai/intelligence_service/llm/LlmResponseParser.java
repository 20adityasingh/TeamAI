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


    // A greedy regex that finds tag-like starts and captures everything until the next potential tag start or end of string.
    // Handles smashed tags (thoughttool), missing brackets (thought>), and unclosed tags.
    private static final Pattern EVENT_SCANNER = Pattern.compile(
            "(?i)<?\\/?(thought|message|tool|file)(?:\\s+([^>]*))?>?(.*?)(?=(?:<?\\/?(?:thought|message|tool|file)(?:\\s|\\b|>))|$)",
            Pattern.DOTALL
    );

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)=\"([^\"]+)\""
    );

    public List<ChatEvent> parserChatEvents(String fullResponse, ChatMessage chatMessage) {
        log.info("Parsing chat events from response length: {}", fullResponse != null ? fullResponse.length() : 0);
        List<ChatEvent> rawEvents = new ArrayList<>();
        if (fullResponse == null || fullResponse.isEmpty()) return rawEvents;

        Matcher matcher = EVENT_SCANNER.matcher(fullResponse);
        while (matcher.find()) {
            String tagName = matcher.group(1).toLowerCase();
            String attributes = matcher.group(2);
            String content = matcher.group(3).trim();

            // Ignore empty fragments and noise
            if (content.isEmpty() && !tagName.equals("thought")) {
               // We keep empty thoughts to trigger the thinking bar, but skip others if totally empty
               if (!tagName.equals("message") && !tagName.equals("tool") && !tagName.equals("file")) continue;
            }

            // Cleanup any leaked closing tag fragments in the content
            content = content.replaceAll("(?i)</?" + tagName + ">?", "").trim();
            content = content.replaceAll("(?i)</?[a-z]+>?$", "").trim(); // Clean trailing noise

            Map<String, String> mapAttr = extractAttributes(attributes);

            ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                    .status(ChatEventStatus.COMPLETED)
                    .chatMessage(chatMessage)
                    .content(content);

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
                ChatEvent last = mergedEvents.get(mergedEvents.size() - 1);
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
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

}
