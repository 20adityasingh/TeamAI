import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

// Regex to capture supported tags: <tool>, <message>, <file>
// Lenient for streaming: supports missing closing tags for the very last item in the buffer
const TAG_REGEX = /<(tool|message|file)(?:[^>]*)>([\s\S]*?)(?:<\/\1>|$)/gi;
const ATTR_REGEX = /(?:path|args)="([^"]+)"/i;

export const useStreamParser = (streamBuffer: string) => {
  return useMemo(() => {
    const events: ChatEvent[] = [];
    if (!streamBuffer) return events;

    let lastIndex = 0;
    let match: RegExpExecArray | null;

    // Reset regex index
    TAG_REGEX.lastIndex = 0;

    while ((match = TAG_REGEX.exec(streamBuffer)) !== null) {
      // 1. Extract and push untagged text (Preamble)
      const preamble = streamBuffer.substring(lastIndex, match.index).trim();
      
      // Filter out partial leaking tags (e.g., if AI starts a tag but regex hasn't caught it yet)
      // or if it's just a closing tag leak from a previously matched block
      const cleanPreamble = preamble.replace(/<\/?(message|file|tool)[^>]*>/gi, '').trim();
      
      if (cleanPreamble) {
        events.push({
          type: ChatEventType.MESSAGE,
          content: cleanPreamble
        });
      }

      // 2. Extract Tag Content
      const [fullMatch, tagName, content] = match;
      const typeStr = tagName.toLowerCase();
      
      // Extract attributes from the opening tag part
      const openTagEndIndex = fullMatch.indexOf('>') + 1;
      const openTag = fullMatch.substring(0, openTagEndIndex);
      const attrMatch = ATTR_REGEX.exec(openTag);
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

      lastIndex = TAG_REGEX.lastIndex;
    }

    // 3. Extract Trailing Text
    const trailing = streamBuffer.substring(lastIndex).trim();
    // Again, filter out any partially formed tags at the very end
    const cleanTrailing = trailing.replace(/<[^>]*$/g, '').replace(/<\/?(message|file|tool)[^>]*>/gi, '').trim();
    
    if (cleanTrailing) {
      events.push({
        type: ChatEventType.MESSAGE,
        content: cleanTrailing
      });
    }

    return events;
  }, [streamBuffer]);
};