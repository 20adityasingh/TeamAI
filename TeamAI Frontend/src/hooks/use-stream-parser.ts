import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

const TAG_START_REGEX = /<(thought|tool|message|file)(?:\s+[^>]*)?>/gi;
const ATTR_REGEX = /(?:path|args)="([^"]+)"/i;
const extractAttributes = (attrString: string) => {
  const attrs: { [key: string]: string } = {};
  const regex = /(\w+)="([^"]*)"/g;
  let match;
  while ((match = regex.exec(attrString)) !== null) {
    attrs[match[1]] = match[2];
  }
  return attrs;
};

export const useStreamParser = (streamBuffer: string) => {
  return useMemo(() => {
    // Greedy regex scanner for the frontend
    const EVENT_SCANNER = /<?\/?(thought|message|tool|file)(?:\s+([^>]*))?>?(.*?)(?=(?:<?\/?(?:thought|message|tool|file)(?:\s|\b|>))|$)/gsi;

    const rawEvents: ChatEvent[] = [];
    let match;

    while ((match = EVENT_SCANNER.exec(streamBuffer)) !== null) {
      const tagName = match[1].toLowerCase();
      const attributes = match[2] || "";
      let content = match[3].trim();

      // Cleanup fragments
      content = content.replace(new RegExp(`</?${tagName}>?`, "gi"), "").trim();
      content = content.replace(/<\/?[a-z]+>?$/i, "").trim();

      const mapAttr = extractAttributes(attributes);

      switch (tagName) {
        case "thought":
          rawEvents.push({ type: ChatEventType.THOUGHT, content });
          break;
        case "message":
          rawEvents.push({ type: ChatEventType.MESSAGE, content });
          break;
        case "tool":
          rawEvents.push({ type: ChatEventType.TOOL_LOG, content, metadata: mapAttr.args });
          break;
        case "file":
          rawEvents.push({ type: ChatEventType.FILE_EDIT, content, filePath: mapAttr.path });
          break;
      }
    }

    // Fallback: If no tags matched yet there is content
    if (rawEvents.length === 0 && streamBuffer.trim().length > 0) {
      rawEvents.push({ type: ChatEventType.MESSAGE, content: streamBuffer.trim() });
    }

    // Merge adjacent events of the same type (collapses Thinking bars)
    const mergedEvents: ChatEvent[] = [];
    rawEvents.forEach((event) => {
      if (
        mergedEvents.length > 0 &&
        mergedEvents[mergedEvents.length - 1].type === event.type &&
        (event.type === ChatEventType.THOUGHT || event.type === ChatEventType.MESSAGE)
      ) {
        // Only merge if they don't have different targets
        mergedEvents[mergedEvents.length - 1].content += (mergedEvents[mergedEvents.length - 1].content ? "\n" : "") + event.content;
      } else {
        mergedEvents.push(event);
      }
    });

    return mergedEvents;
  }, [streamBuffer]);
};