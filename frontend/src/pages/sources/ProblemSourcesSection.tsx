import { useState } from "react";
import { AlertTriangle, CheckCircle, ChevronDown } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { ConnectionErrorCard } from "./ConnectionErrorCard";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

const DEFAULT_VISIBLE = 5;

interface ProblemSourcesSectionProps {
  connectionErrors: Source[];
  categories: Category[];
  pendingIds: Set<string>;
  onRetry: (id: string) => void;
  onOpenDetail: (source: Source) => void;
}

export function ProblemSourcesSection({
  connectionErrors,
  categories,
  pendingIds,
  onRetry,
  onOpenDetail,
}: ProblemSourcesSectionProps) {
  const [expanded, setExpanded] = useState(false);
  const totalCount = connectionErrors.length;
  const categoryMap = new Map(categories.map((c) => [c.id, c]));

  if (totalCount === 0) {
    return (
      <div className="rounded-xl border bg-card p-6 text-center">
        <p className="text-sm text-muted-foreground flex items-center justify-center gap-1.5"><CheckCircle className="h-4 w-4 text-[var(--status-success-text)]" /> 모든 소스가 정상이에요</p>
      </div>
    );
  }

  // crawlFailCount 내림차순 정렬 (가장 심각한 것 먼저)
  const sorted = [...connectionErrors].sort(
    (a, b) => b.crawlFailCount - a.crawlFailCount,
  );
  const visible = expanded ? sorted : sorted.slice(0, DEFAULT_VISIBLE);
  const hiddenCount = totalCount - DEFAULT_VISIBLE;

  return (
    <section className="space-y-4">
      <h2 className="text-base font-semibold flex items-center gap-2">
        <AlertTriangle size={16} className="text-[var(--status-danger-text)]" />
        처리 필요 ({totalCount})
      </h2>

      {connectionErrors.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-medium text-muted-foreground">
            연결 실패 ({connectionErrors.length})
          </h3>
          <AnimatePresence initial={false}>
            {visible.map((source) => (
              <motion.div
                key={source.id}
                layout
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.2 }}
              >
                <ConnectionErrorCard
                  source={source}
                  category={categoryMap.get(source.categoryId)}
                  isPending={pendingIds.has(source.id)}
                  onRetry={onRetry}
                  onOpenDetail={onOpenDetail}
                />
              </motion.div>
            ))}
          </AnimatePresence>

          {/* 더 보기 / 접기 버튼 */}
          {hiddenCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-muted-foreground"
              onClick={() => setExpanded((v) => !v)}
            >
              {expanded ? (
                "접기"
              ) : (
                <>
                  <ChevronDown size={14} className="mr-1" />
                  {hiddenCount}개 더 보기
                </>
              )}
            </Button>
          )}
        </div>
      )}
    </section>
  );
}
