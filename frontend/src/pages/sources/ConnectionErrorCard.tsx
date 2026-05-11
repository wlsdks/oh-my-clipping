import { AlertTriangle, ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { translateRawMessage } from "@/shared/lib/httpError";
import { regionLabel } from "./sourceHelpers";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

interface ConnectionErrorCardProps {
  source: Source;
  category?: Category;
  isPending: boolean;
  onRetry: (id: string) => void;
  onOpenDetail: (source: Source) => void;
}

export function ConnectionErrorCard({
  source,
  category,
  isPending,
  onRetry,
  onOpenDetail,
}: ConnectionErrorCardProps) {
  const region = regionLabel(source.sourceRegion);
  const metaParts: string[] = [];
  if (category) metaParts.push(category.name);
  if (region) metaParts.push(region);
  metaParts.push(`${source.crawlFailCount}회 실패`);

  return (
    <div className="rounded-xl border border-l-[3px] border-l-[var(--status-danger-text)] bg-card p-4">
      <div className="flex items-start gap-3">
        <AlertTriangle
          size={18}
          className="mt-0.5 shrink-0 text-[var(--status-danger-text)]"
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <a
            href={source.url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-start gap-1 text-sm font-semibold text-foreground hover:underline"
          >
            {source.name}
            <ExternalLink size={12} className="mt-1 shrink-0 text-muted-foreground" />
          </a>
          <p className="text-xs text-muted-foreground mt-0.5">{metaParts.join(" · ")}</p>
          {source.lastCrawlError && (
            <p className="text-xs text-[var(--status-danger-text)] mt-1.5 line-clamp-1">
              {translateRawMessage(source.lastCrawlError, "연결 실패 원인을 확인하려면 자세히를 눌러주세요")}
            </p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button
            size="sm"
            variant="outline"
            className="h-8 text-xs"
            onClick={() => onOpenDetail(source)}
          >
            자세히
          </Button>
          <Button
            size="sm"
            className="h-8 text-xs"
            disabled={isPending}
            onClick={() => onRetry(source.id)}
          >
            재시도
          </Button>
        </div>
      </div>
    </div>
  );
}
