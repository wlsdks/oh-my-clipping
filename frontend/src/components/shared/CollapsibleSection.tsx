import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

interface CollapsibleSectionProps {
  title: string;
  description?: string;
  summary?: string;
  defaultOpen?: boolean;
  warning?: boolean;
  children: React.ReactNode;
}

export function CollapsibleSection({
  title,
  description,
  summary,
  defaultOpen = true,
  warning = false,
  children,
}: CollapsibleSectionProps) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <section className="rounded-lg border bg-card overflow-hidden">
      <button
        type="button"
        className="w-full flex items-center justify-between p-5 text-left hover:bg-muted/30 transition-colors"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
      >
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-sm">{title}</h3>
          {!isOpen && summary && (
            <p className="text-xs text-muted-foreground mt-0.5 truncate">{summary}</p>
          )}
          {isOpen && description && (
            <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
          )}
        </div>
        {isOpen ? (
          <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0 ml-3" />
        ) : (
          <ChevronRight className="h-4 w-4 text-muted-foreground shrink-0 ml-3" />
        )}
      </button>
      {isOpen && (
        <div className="px-5 pb-5 space-y-4 border-t">
          {warning && (
            <div className="mt-4 flex items-center gap-2 rounded-lg bg-[var(--status-warning-bg)] px-3 py-2 text-xs text-[var(--status-warning-text)]">
              잘못 변경하면 서비스에 영향을 줄 수 있어요
            </div>
          )}
          {children}
        </div>
      )}
    </section>
  );
}
