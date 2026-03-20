import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { 
  Sparkles, 
  Database, 
  FileEdit,
  Terminal,
} from 'lucide-react';
import { ChatEvent, ChatEventType } from '@/lib/types';
import { CollapsibleSection } from './CollapsibleSection';

/**
 * Re-inserts newlines before markdown structural markers that were
 * stripped during SSE transport (Spring SSE drops \n tokens).
 * Only applied to MESSAGE content — never to code/file content.
 */
function repairMarkdown(text: string): string {
  return text
    // Ensure double newline before markdown headers (## ... ######)
    .replace(/([^\n])(#{1,6}\s)/g, '$1\n\n$2')
    // Ensure newline before numbered list items (1. 2. 3.)
    .replace(/([^\n])(\d+\.\s)/g, '$1\n$2')
    // Ensure newline before bullet list items (- or *)
    .replace(/([^\n])([-*]\s)/g, '$1\n$2')
    // Ensure newline before code fences
    .replace(/([^\n])(```)/g, '$1\n$2')
    // Ensure newline before bold section starts that look like labels ("**Name**:")
    .replace(/([.!?])\s*(\*\*[A-Z])/g, '$1\n\n$2');
}

export const ChatEventRenderer = ({ event, isLoading }: { event: ChatEvent, isLoading?: boolean }) => {
  switch (event.type) {
    case ChatEventType.THOUGHT:
      return (
        <CollapsibleSection
          icon={<Sparkles className="w-4 h-4 text-blue-400" />}
          label="Thinking"
          isLoading={isLoading}
        >
          <div className="italic text-muted-foreground/80">
            {event.content}
          </div>
        </CollapsibleSection>
      );

    case ChatEventType.TOOL_LOG:
      return (
        <CollapsibleSection
          icon={<Database className="w-4 h-4 text-amber-400" />}
          label={isLoading ? "Reading" : "Read"}
          subtitle={event.metadata}
          isLoading={isLoading}
        >
          <div className="font-mono text-[12px] bg-black/20 p-2 rounded border border-white/5">
            <div className="flex items-center gap-2 text-muted-foreground mb-1">
              <Terminal className="w-3 h-3" />
              <span>Tool output</span>
            </div>
            <pre className="whitespace-pre-wrap break-all opacity-80">
              {event.content}
            </pre>
          </div>
        </CollapsibleSection>
      );

    case ChatEventType.FILE_EDIT:
      const fileName = event.filePath?.split('/').pop() || 'file';
      return (
        <CollapsibleSection
          icon={<FileEdit className="w-4 h-4 text-emerald-400" />}
          label={isLoading ? "Editing" : "Edited"}
          subtitle={event.filePath}
          isLoading={isLoading}
          statusBadge={
            <span className="bg-emerald-500/10 text-emerald-500 text-[10px] px-1.5 py-0.5 rounded-full border border-emerald-500/20 font-mono">
              {fileName}
            </span>
          }
        >
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2 text-muted-foreground border-b border-white/5 pb-2 mb-2">
              <FileEdit className="w-3.5 h-3.5" />
              <span className="font-mono text-xs">{event.filePath}</span>
            </div>
            <div className="opacity-80 line-clamp-6 text-xs font-mono">
              {event.content}
            </div>
            <div className="text-[10px] text-muted-foreground mt-2 italic">
              * File content has been updated in the editor
            </div>
          </div>
        </CollapsibleSection>
      );

    case ChatEventType.MESSAGE:
      // Prevent rendering if content is empty or contains only whitespace/tags
      if (!event.content || !event.content.trim()) return null;

      return (
        <div className="prose prose-invert prose-sm max-w-none text-[#ececec] leading-relaxed mb-4 animate-in fade-in slide-in-from-bottom-2 duration-500">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {repairMarkdown(event.content)}
          </ReactMarkdown>
          {isLoading && <span className="inline-block w-1.5 h-4 ml-1 bg-primary animate-pulse align-middle" />}
        </div>
      );

    default:
      return null;
  }
};