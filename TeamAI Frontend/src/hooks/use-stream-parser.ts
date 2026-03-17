import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

const TAG_START_REGEX = /<(thought|tool|message|file)(?:\s+[^>]*)?>/gi;
const ATTR_REGEX = /(?:path|args)="([^"]+)"/i;

export const useStreamParser = (streamBuffer: string) => {
  return useMemo(() => {
    const events: ChatEvent[] = [];
    if (!streamBuffer) return events;

    // 1. Split buffer by tag starts while keeping them as delimiters
    // This allows us to treat each block as a potential event
    const parts = streamBuffer.split(/(?=<thought|<tool|<message|<file)/i);
    
    for (const part of parts) {
      const trimmedPart = part.trim();
      if (!trimmedPart) continue;

      // Reset regex for each part
      TAG_START_REGEX.lastIndex = 0;
      const match = TAG_START_REGEX.exec(trimmedPart);

      if (match) {
        const tagName = match[1].toLowerCase();
        const fullOpenTag = match[0];
        
        // Content is everything after the opening tag, 
        // minus the closing tag if it exists
        const closingTag = `</${tagName}>`;
        let content = trimmedPart.slice(fullOpenTag.length);
        
        if (content.toLowerCase().endsWith(closingTag.toLowerCase())) {
          content = content.slice(0, -closingTag.length);
        } else {
          // If it's not closed, cleanup any partial closing tag junk at the end
          content = content.replace(/<\/?[a-z]*$/i, '');
        }

        const attrMatch = ATTR_REGEX.exec(fullOpenTag);
        const attrValue = attrMatch ? attrMatch[1] : undefined;

        let type: ChatEventType = ChatEventType.MESSAGE;
        let filePath: string | undefined;
        let metadata: string | undefined;

        switch (tagName) {
          case 'thought': type = ChatEventType.THOUGHT; break;
          case 'tool': 
            type = ChatEventType.TOOL_LOG; 
            metadata = attrValue;
            break;
          case 'file':
            type = ChatEventType.FILE_EDIT;
            filePath = attrValue;
            break;
          case 'message':
            type = ChatEventType.MESSAGE;
            break;
        }

        events.push({
          type,
          content: content.trim(),
          filePath,
          metadata
        });
      } else {
        // Untagged free-text block (Preamble or Trailing)
        // Clean up any leaked tag artifacts
        const cleanContent = trimmedPart.replace(/<\/?(message|file|tool|thought)[^>]*>/gi, '').trim();
        if (cleanContent) {
          events.push({
            type: ChatEventType.MESSAGE,
            content: cleanContent
          });
        }
      }
    }

    return events;
  }, [streamBuffer]);
};