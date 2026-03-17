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
    const events: ChatEvent[] = [];
    if (!streamBuffer) return events;

    // 1. Split by anything that LOOKS like a tag start, including missing brackets
    // e.g., <thought, <message, <tool, <file, thought>, message>, tool>, file>
    const parts = streamBuffer.split(/(?=<thought|<message|<tool|<file|thought>|message>|tool>|file>)/i);

    const eventsToReturn: ChatEvent[] = [];

    parts.forEach((part) => {
      const trimmedPart = part.trim();
      if (!trimmedPart) return;

      const startMatch = trimmedPart.match(/^<?(thought|message|tool|file)(?:\s+([^>]*))?>/i);

      if (startMatch) {
        const tagName = startMatch[1].toLowerCase();
        const attributes = startMatch[2] || "";
        const fullOpenTag = startMatch[0];

        let content = trimmedPart.substring(fullOpenTag.length).trim();

        const closingTag = `</${tagName}>`;
        if (content.toLowerCase().endsWith(closingTag.toLowerCase())) {
          content = content.substring(0, content.length - closingTag.length).trim();
        }

        const mapAttr = extractAttributes(attributes);

        switch (tagName) {
          case "thought":
            eventsToReturn.push({ type: ChatEventType.THOUGHT, content });
            break;
          case "message":
            eventsToReturn.push({ type: ChatEventType.MESSAGE, content });
            break;
          case "tool":
            eventsToReturn.push({ type: ChatEventType.TOOL_LOG, content, metadata: mapAttr.args });
            break;
          case "file":
            eventsToReturn.push({ type: ChatEventType.FILE_EDIT, content, filePath: mapAttr.path });
            break;
        }
      } else {
        const cleanContent = trimmedPart
          .replace(/<\/?(thought|message|tool|file)>?/gi, "")
          .trim();

        if (cleanContent) {
          eventsToReturn.push({ type: ChatEventType.MESSAGE, content: cleanContent });
        }
      }
    });

    return eventsToReturn;
  }, [streamBuffer]);
};