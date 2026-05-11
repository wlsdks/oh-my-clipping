import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userFriendlyMessage } from "../../../shared/lib/httpError";
import { cn } from "@/utils/cn";
import type { KeywordEntityItem } from "../../../shared/types/admin";

interface EntityPanelProps {
  days: number;
  categoryId?: string;
  onKeywordClick?: (keyword: string) => void;
}

type CategoryFilter = "ALL" | "PERSON" | "ORG" | "TECH" | "LOCATION" | "TOPIC";

const CATEGORY_CONFIG: Record<string, { icon: string; label: string; className: string }> = {
  PERSON: { icon: "\u{1F464}", label: "\uC778\uBB3C", className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]" },
  ORG: { icon: "\u{1F3E2}", label: "\uAE30\uC5C5", className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]" },
  TECH: { icon: "\u{1F4BB}", label: "\uAE30\uC220", className: "bg-primary/10 text-primary" },
  LOCATION: { icon: "\u{1F4CD}", label: "\uC9C0\uC5ED", className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]" },
  TOPIC: { icon: "\u{1F4CC}", label: "\uD1A0\uD53D", className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]" },
};

const FILTER_TABS: { key: CategoryFilter; label: string }[] = [
  { key: "ALL", label: "\uC804\uCCB4" },
  { key: "PERSON", label: "\u{1F464} \uC778\uBB3C" },
  { key: "ORG", label: "\u{1F3E2} \uAE30\uC5C5" },
  { key: "TECH", label: "\u{1F4BB} \uAE30\uC220" },
  { key: "LOCATION", label: "\u{1F4CD} \uC9C0\uC5ED" },
  { key: "TOPIC", label: "\u{1F4CC} \uD1A0\uD53D" },
];

export function EntityPanel({ days, categoryId, onKeywordClick }: EntityPanelProps) {
  const [filter, setFilter] = useState<CategoryFilter>("ALL");

  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.keywordEntities({ days, categoryId }),
    queryFn: () => newsIntelligenceService.getKeywordEntities({ days, categoryId: categoryId || undefined }),
  });

  const items: KeywordEntityItem[] = data?.items ?? [];
  const filtered = filter === "ALL" ? items : items.filter((it) => it.category === filter);

  if (isLoading) {
    return (
      <section className="panel">
        <div className="panel-head">
          <h3>키워드 엔티티</h3>
        </div>
        <p className="text-sm text-muted-foreground p-4">불러오는 중...</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="panel">
        <div className="panel-head">
          <h3>키워드 엔티티</h3>
        </div>
        <p className="text-sm text-destructive p-4">
          {userFriendlyMessage(error, "\uD0A4\uC6CC\uB4DC \uC5D4\uD2F0\uD2F0 \uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC5B4\uC694")}
        </p>
      </section>
    );
  }

  if (items.length === 0) {
    return (
      <section className="panel">
        <div className="panel-head">
          <h3>키워드 엔티티</h3>
        </div>
        <EmptyState
          title="분류된 엔티티가 아직 없어요"
          description="뉴스가 수집되면 인물/기업/기술 등으로 자동 분류돼요."
          className="bg-muted rounded-xl py-8"
        />
      </section>
    );
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h3>키워드 엔티티</h3>
        <small>{items.length}개 키워드</small>
      </div>

      {/* 탭 필터 */}
      <div className="flex flex-wrap gap-1.5 mb-4">
        {FILTER_TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setFilter(tab.key)}
            aria-label={`${tab.label} 필터`}
            aria-pressed={filter === tab.key}
            className={cn(
              "px-3.5 py-1.5 text-[13px] rounded-full border-none cursor-pointer transition-all duration-150",
              filter === tab.key
                ? "bg-primary text-primary-foreground font-semibold"
                : "bg-secondary text-muted-foreground hover:bg-secondary/70"
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* 카드 그리드 */}
      {filtered.length === 0 ? (
        <p className="text-[13px] text-muted-foreground text-center py-6">
          해당 분류에 키워드가 없어요
        </p>
      ) : (
        <div className="grid grid-cols-3 gap-2.5">
          {filtered.map((item) => {
            const cfg = CATEGORY_CONFIG[item.category] ?? CATEGORY_CONFIG.TOPIC;
            return (
              <div
                key={`${item.keyword}-${item.category}`}
                onClick={() => onKeywordClick?.(item.keyword)}
                className={cn(
                  "p-4 bg-muted rounded-[14px] transition-all duration-200",
                  onKeywordClick
                    ? "cursor-pointer hover:-translate-y-px hover:shadow-md"
                    : "cursor-default"
                )}
              >
                <div className="flex justify-between items-start mb-2">
                  <span className="text-sm font-semibold text-foreground leading-snug">
                    {item.keyword}
                  </span>
                  <span className="text-[13px] font-semibold text-muted-foreground whitespace-nowrap ml-2">
                    {item.count}건
                  </span>
                </div>
                <span
                  className={cn(
                    "inline-block px-2.5 py-0.5 text-[11px] font-semibold rounded-full",
                    cfg.className
                  )}
                >
                  {cfg.icon} {cfg.label}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
