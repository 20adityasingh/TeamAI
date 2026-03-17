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
    // Aggressive split: split before <, before /, or before naked tag names if they follow a boundary
    const parts = streamBuffer.split(/(?=<|(?<=\w)\/|(?<=[>/])(?:thought|message|tool|file))/i);

    const rawEvents: ChatEvent[] = [];

    parts.forEach((part) => {
      const trimmedPart = part.trim();
      if (!trimmedPart) return;

      const startMatch = trimmedPart.match(/^<?(thought|message|tool|file)(?:\s+([^>]*))?>/i);

      if (startMatch) {
        const tagName = startMatch[1].toLowerCase();
        const attributes = startMatch[2] || "";
        const fullOpenTag = startMatch[0];

        let content = trimmedPart.substring(fullOpenTag.length).trim();

        // Strip closing tag fragments
        content = content.replace(new RegExp(`</${tagName}>`, "gi"), "").trim();
        content = content.replace(/<\/?[a-z]*$/i, "").trim();

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
      } else {
        const cleanContent = trimmedPart
          .replace(/<\/?(thought|message|tool|file)>?/gi, "")
          .trim();

        if (cleanContent) {
          rawEvents.push({ type: ChatEventType.MESSAGE, content: cleanContent });
        }
      }
    });

    // Merge adjacent events of the same type (especially THOUGHT and MESSAGE)
    const mergedEvents: ChatEvent[] = [];
    rawEvents.forEach((event) => {
      if (
        mergedEvents.length > 0 &&
        mergedEvents[mergedEvents.length - 1].type === event.type &&
        (event.type === ChatEventType.THOUGHT || event.type === ChatEventType.MESSAGE)
      ) {
        mergedEvents[mergedEvents.length - 1].content += "\n" + event.content;
      } else {
        mergedEvents.push(event);
      }
    });

    return mergedEvents;
  }, [streamBuffer]);
};