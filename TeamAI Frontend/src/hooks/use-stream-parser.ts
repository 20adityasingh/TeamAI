import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

// Regex to capture the three specific tags we support
// Matches: <tag attributes>content</tag>
// Note: This regex is designed to be lenient for streaming (doesn't require strict closing for the last item)
const PARSE_REGEX = /<(tool|message|file)(?:[^>]*)>([\s\S]*?)(?:<\/\1>|$)/gi;
const ATTR_REGEX = /(?:path|args)="([^"]+)"/i;

export const useStreamParser = (streamBuffer: string) => {
  return useMemo(() => {
    const events: ChatEvent[] = [];
    let match: RegExpExecArray | [any, any, any];

    // Track last index to capture untagged text
    let lastIndex = 0;

    while ((match = PARSE_REGEX.exec(streamBuffer)) !== null) {
      // Capture text BEFORE the tag as a MESSAGE event
      const preamble = streamBuffer.substring(lastIndex, match.index).trim();
      if (preamble) {
        events.push({
          type: ChatEventType.MESSAGE,
          content: preamble
        });
      }

      const [fullMatch, tagName, content] = match;
      const typeStr = tagName.toLowerCase();
      lastIndex = PARSE_REGEX.lastIndex;

      // ... rest of the logic ...
      const openTagMatch = streamBuffer.substring(match.index, match.index + fullMatch.indexOf('>') + 1);
      const attrMatch = ATTR_REGEX.exec(openTagMatch);
      const attrValue = attrMatch ? attrMatch[1] : undefined;

      let type: ChatEventType = ChatEventType.MESSAGE;
      let filePath: string | undefined;
      let metadata: string | undefined;

      if (typeStr === 'tool') {
        type = ChatEventType.TOOL_LOG;
        metadata = attrValue;
      } else if (typeStr === 'file') {
        type = ChatEventType.FILE_EDIT;
        filePath = attrValue;
      }

      events.push({
        type,
        content: content.trim(),
        filePath,
        metadata
      });
    }

    // Capture trailing text
    if (lastIndex < streamBuffer.length) {
      const trailing = streamBuffer.substring(lastIndex).trim();
      if (trailing) {
        events.push({
          type: ChatEventType.MESSAGE,
          content: trailing
        });
      }
    }

    return events;
  }, [streamBuffer]);
};