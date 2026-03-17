import React, { useState } from 'react';
import { ChevronDown, Loader2 } from 'lucide-react';

interface CollapsibleSectionProps {
  icon: React.ReactNode;
  label: string;
  subtitle?: string;
  children: React.ReactNode;
  defaultExpanded?: boolean;
  isLoading?: boolean;
  statusBadge?: React.ReactNode;
}

export const CollapsibleSection: React.FC<CollapsibleSectionProps> = ({
  icon,
  label,
  subtitle,
  children,
  defaultExpanded = false,
  isLoading = false,
  statusBadge
}) => {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);

  return (
    <div className="group flex flex-col w-full my-1.5 transition-all duration-300">
      {/* Header Bar */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className={`flex items-center justify-between w-full p-2.5 rounded-xl border transition-all duration-200 
          ${isExpanded 
            ? 'bg-muted/40 border-border/60 shadow-sm' 
            : 'bg-muted/20 border-transparent hover:bg-muted/40 hover:border-border/40'
          }`}
      >
        <div className="flex items-center gap-3 overflow-hidden ml-1">
          <div className={`shrink-0 transition-transform duration-300 ${isLoading ? 'animate-pulse' : 'group-hover:scale-110'}`}>
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin text-primary" /> : icon}
          </div>
          
          <div className="flex flex-col items-start transition-all duration-200 overflow-hidden">
            <div className="flex items-center gap-2">
              <span className="text-[13px] font-medium text-foreground/80 whitespace-nowrap">
                {label}
              </span>
              {statusBadge && (
                <div className="animate-in fade-in zoom-in duration-300">
                  {statusBadge}
                </div>
              )}
            </div>
            {subtitle && (
              <span className="text-[11px] text-muted-foreground/60 truncate max-w-[200px]">
                {subtitle}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 mr-1">
          <div className={`p-1 rounded-full transition-colors duration-200 ${isExpanded ? 'bg-primary/10 text-primary' : 'text-muted-foreground/40'}`}>
            <ChevronDown 
              className={`w-3.5 h-3.5 transition-transform duration-300 ease-out ${isExpanded ? 'rotate-180' : 'rotate-0'}`} 
            />
          </div>
        </div>
      </button>

      {/* Content Area */}
      <div 
        className={`grid transition-all duration-300 ease-in-out ${
          isExpanded ? 'grid-rows-[1fr] opacity-100 mt-2' : 'grid-rows-[0fr] opacity-0 mt-0'
        }`}
      >
        <div className="overflow-hidden">
          <div className="px-4 py-3 rounded-xl bg-muted/10 border border-border/30 ml-8 text-[13px] leading-relaxed text-foreground/70 animate-in slide-in-from-top-1 duration-300">
             {children}
          </div>
        </div>
      </div>
    </div>
  );
};
