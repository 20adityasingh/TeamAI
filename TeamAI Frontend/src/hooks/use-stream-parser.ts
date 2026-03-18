import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

const extractAttributes = (attrString: string) => {
  const attrs: { [key: string]: string } = {};
  const regex = /(path|args)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))/gi;
  let match;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1].toLowerCase();
    attrs[key] = match[2] ?? match[3] ?? match[4] ?? '';
  }
  return attrs;
};

export const useStreamParser = (streamBuffer: string) => {
  return useMemo(() => {
    const OPEN_TAG_REGEX = /<\s*(thought|message|tool|file)\b([^>]*)>/gi;

    const rawEvents: ChatEvent[] = [];
    let cursor = 0;

    while (cursor < streamBuffer.length) {
      OPEN_TAG_REGEX.lastIndex = cursor;
      const openMatch = OPEN_TAG_REGEX.exec(streamBuffer);
      if (!openMatch) break;

      const tagName = openMatch[1].toLowerCase();
      const attributes = openMatch[2] || '';
      const contentStart = OPEN_TAG_REGEX.lastIndex;
      const closeTagRegex = new RegExp(`</\\s*${tagName}\\s*>`, 'i');
      const remaining = streamBuffer.slice(contentStart);
      const closeMatch = remaining.match(closeTagRegex);

      let contentEnd = streamBuffer.length;
      let nextCursor = streamBuffer.length;
      if (closeMatch && closeMatch.index !== undefined) {
        contentEnd = contentStart + closeMatch.index;
        nextCursor = contentEnd + closeMatch[0].length;
      } else {
        const nextOpenOffset = remaining.search(/<\s*(thought|message|tool|file)\b[^>]*>/i);
        if (nextOpenOffset !== -1) {
          contentEnd = contentStart + nextOpenOffset;
          nextCursor = contentEnd;
        }
      }

      let content = streamBuffer.slice(contentStart, contentEnd).trim();

      // Cleanup trailing tag fragments from partially generated output.
      content = content.replace(/<\/?[a-z]+>?$/i, '').trim();

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
          if (!mapAttr.path) {
            console.warn("Skipping file edit event due to missing path attribute.");
            break;
          }
          rawEvents.push({ type: ChatEventType.FILE_EDIT, content, filePath: mapAttr.path });
          break;
      }

      cursor = Math.max(nextCursor, openMatch.index + 1);
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