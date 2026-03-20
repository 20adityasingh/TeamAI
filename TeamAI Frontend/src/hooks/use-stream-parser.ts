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
    // Strip any trailing incomplete tag fragment (e.g. "</though" or "<messag")
    // so partial tags don't leak into the rendered UI during live streaming.
    const sanitizedBuffer = streamBuffer.replace(/<[^>]*$/, '');
    const normalizedBuffer = normalizeMalformedTags(sanitizedBuffer);
    const OPEN_TAG_REGEX = /<\s*(thought|message|tool|file)\b([^>]*)>/gi;

    const rawEvents: ChatEvent[] = [];
    let cursor = 0;

    while (cursor < normalizedBuffer.length) {
      OPEN_TAG_REGEX.lastIndex = cursor;
      const openMatch = OPEN_TAG_REGEX.exec(normalizedBuffer);
      if (!openMatch) break;

      const tagName = openMatch[1].toLowerCase();
      const attributes = openMatch[2] || '';
      const contentStart = OPEN_TAG_REGEX.lastIndex;
      const closeTagRegex = new RegExp(`</\\s*${tagName}\\s*>`, 'i');
      const remaining = normalizedBuffer.slice(contentStart);
      const closeMatch = remaining.match(closeTagRegex);

      let contentEnd = normalizedBuffer.length;
      let nextCursor = normalizedBuffer.length;

      const nextOpenOffset = remaining.search(/<\s*(thought|message|tool|file)\b[^>]*>/i);
      const closeIndex = closeMatch && closeMatch.index !== undefined ? closeMatch.index : -1;

      const boundaries = [closeIndex, nextOpenOffset].filter(idx => idx !== -1);
      if (boundaries.length > 0) {
        const earliestBoundary = Math.min(...boundaries);
        contentEnd = contentStart + earliestBoundary;
        nextCursor = earliestBoundary === closeIndex 
          ? contentEnd + closeMatch![0].length 
          : contentEnd;
      }

      let content = normalizedBuffer.slice(contentStart, contentEnd).trim();

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
    if (rawEvents.length === 0 && normalizedBuffer.trim().length > 0) {
      rawEvents.push({ type: ChatEventType.MESSAGE, content: normalizedBuffer.trim() });
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

const normalizeMalformedTags = (input: string): string => {
  let normalized = input;

  // Recover missing opening bracket patterns like "thought>" -> "<thought>".
  normalized = normalized.replace(/(^|\s)(thought|message|tool|file)>/gi, '$1<$2>');

  // Recover smashed boundary patterns like "</thoughttool ...>" -> "</thought><tool ...>".
  normalized = normalized.replace(
    /<\/(thought|message|tool|file)(?=(thought|message|tool|file)\b)/gi,
    '</$1><$2',
  );

  return normalized;
};
