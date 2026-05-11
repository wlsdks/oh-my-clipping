import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userFriendlyMessage } from "../../../shared/lib/httpError";
import { cn } from "@/utils/cn";
import type { CompetitorTimelineItem } from "../../../shared/types/admin";

const EVENT_LABELS: Record<string, { icon: string; label: string }> = {
  PRODUCT_LAUNCH: { icon: "\u{1F680}", label: "\uCD9C\uC2DC" },
  PARTNERSHIP: { icon: "\u{1F91D}", label: "\uC81C\uD734" },
  FUNDING: { icon: "\u{1F4B0}", label: "\uD22C\uC790" },
  POLICY: { icon: "\u{1F4CB}", label: "\uC815\uCC45" },
  PERSONNEL: { icon: "\u{1F464}", label: "\uC778\uC0AC" },
  OTHER: { icon: "\u{1F4F0}", label: "\uAE30\uD0C0" },
};

const EVENT_TYPE_FILTERS = [
  { key: "", label: "\uC804\uCCB4" },
  { key: "PRODUCT_LAUNCH", label: "\u{1F680} \uCD9C\uC2DC" },
  { key: "PARTNERSHIP", label: "\u{1F91D} \uC81C\uD734" },
  { key: "FUNDING", label: "\u{1F4B0} \uD22C\uC790" },
  { key: "POLICY", label: "\u{1F4CB} \uC815\uCC45" },
  { key: "PERSONNEL", label: "\u{1F464} \uC778\uC0AC" },
];

interface CompetitorTimelinePanelProps {
  days: number;
  onItemsLoaded?: (items: CompetitorTimelineItem[]) => void;
  showManageLink?: boolean;
  maxItems?: number;
  fullViewHref?: string;
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    const dayNames = ["\uC77C", "\uC6D4", "\uD654", "\uC218", "\uBAA9", "\uAE08", "\uD1A0"];
    return `${d.getMonth() + 1}/${d.getDate()} (${dayNames[d.getDay()]})`;
  } catch {
    return iso.slice(5, 10);
  }
}

function importanceBadge(score: number): { className: string; label: string } | null {
  if (score >= 0.85) return { className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]", label: "\uC8FC\uBAA9" };
  if (score >= 0.7) return { className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]", label: "\uAD00\uC2EC" };
  return null;
}

function buildCompetitorSummary(items: CompetitorTimelineItem[]) {
  const map = new Map<string, { count: number; sentiments: Record<string, number>; events: Record<string, number> }>();
  for (const item of items) {
    const entry = map.get(item.competitorName) ?? { count: 0, sentiments: {}, events: {} };
    entry.count += 1;
    const s = item.sentiment ?? "NEUTRAL";
    entry.sentiments[s] = (entry.sentiments[s] ?? 0) + 1;
    const e = item.eventType ?? "OTHER";
    entry.events[e] = (entry.events[e] ?? 0) + 1;
    map.set(item.competitorName, entry);
  }
  return Array.from(map.entries())
    .sort(([, a], [, b]) => b.count - a.count)
    .map(([name, data]) => ({ name, ...data }));
}

function topEventType(events: Record<string, number>): string | null {
  let max = 0;
  let top: string | null = null;
  for (const [k, v] of Object.entries(events)) {
    if (v > max) {
      max = v;
      top = k;
    }
  }
  return top;
}

export function CompetitorTimelinePanel({
  days,
  onItemsLoaded,
  maxItems,
  fullViewHref,
}: CompetitorTimelinePanelProps) {
  const [eventTypeFilter, setEventTypeFilter] = useState("");
  const [competitorFilter, setCompetitorFilter] = useState("");

  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.competitorTimeline({ days }),
    queryFn: () => newsIntelligenceService.getCompetitorTimeline({ days }),
  });

  const items: CompetitorTimelineItem[] = Array.isArray(data?.items) ? data.items : [];

  // Notify parent when items load
  if (items.length > 0 && onItemsLoaded) {
    onItemsLoaded(items);
  }

  const competitorSummary = buildCompetitorSummary(items);

  let filtered = items;
  if (eventTypeFilter) filtered = filtered.filter((it) => it.eventType === eventTypeFilter);
  if (competitorFilter) filtered = filtered.filter((it) => it.competitorName === competitorFilter);

  const filteredTotal = filtered.length;
  const displayItems = maxItems != null ? filtered.slice(0, maxItems) : filtered;

  function buildInsightText(): string | null {
    if (items.length === 0) return null;
    const top = competitorSummary[0];
    if (!top) return null;
    const topEvent = topEventType(top.events);
    const eventLabel = topEvent ? (EVENT_LABELS[topEvent]?.label ?? "") : "";
    const negativeCount = items.filter((it) => it.sentiment === "NEGATIVE").length;
    const parts: string[] = [`${top.name}이(가) ${top.count}건으로 가장 많이 언급됐어요`];
    if (eventLabel) parts[0] += ` (주로 ${eventLabel})`;
    if (negativeCount > 0) parts.push(`부정 기사 ${negativeCount}건 감지`);
    return parts.join(" \u00B7 ");
  }
  const insightText = buildInsightText();

  const content = (
    <>
      {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
      {error && (
        <p className="text-sm text-destructive">
          {userFriendlyMessage(error, "타임라인을 불러오지 못했어요")}
        </p>
      )}

      {!isLoading && !error && items.length === 0 && (
        <EmptyState
          title="경쟁사 뉴스가 아직 없어요"
          description="경쟁사 뉴스가 수집되면 여기에 표시돼요."
          className="bg-muted rounded-xl py-8"
        />
      )}

      {!isLoading && !error && items.length > 0 && (
        <>
          {/* 인사이트 요약 */}
          {insightText && (
            <div className="px-4 py-3 bg-primary/5 rounded-[10px] mb-4 text-[13px] leading-relaxed text-foreground">
              {insightText}
            </div>
          )}

          {/* 경쟁사별 요약 카드 */}
          <div className="flex flex-wrap gap-2 mb-4">
            {competitorSummary.map((comp) => {
              const isActive = competitorFilter === comp.name;
              return (
                <button
                  key={comp.name}
                  type="button"
                  onClick={() => setCompetitorFilter(isActive ? "" : comp.name)}
                  aria-label={`${comp.name} 필터 ${isActive ? "해제" : "적용"}`}
                  aria-pressed={isActive}
                  className={cn(
                    "flex items-center gap-1.5 px-3.5 py-1.5 rounded-full border-none text-xs cursor-pointer transition-all duration-150",
                    isActive
                      ? "bg-[var(--status-neutral-text)] text-white font-semibold"
                      : "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]"
                  )}
                >
                  {comp.name}
                  <span className="opacity-70">{comp.count}건</span>
                </button>
              );
            })}
          </div>

          {/* 필터: 이벤트 타입 */}
          <div className="flex flex-wrap items-center gap-1.5 mb-3.5">
            {EVENT_TYPE_FILTERS.map((f) => (
              <FilterChip
                key={f.key}
                active={eventTypeFilter === f.key}
                onClick={() => setEventTypeFilter(eventTypeFilter === f.key ? "" : f.key)}
              >
                {f.label}
              </FilterChip>
            ))}
          </div>

          {/* 타임라인 */}
          {filtered.length === 0 ? (
            <div className="text-center px-4 py-6 text-muted-foreground bg-muted rounded-xl text-[13px]">
              필터 조건에 맞는 기사가 없어요
            </div>
          ) : (
            <div className="grid gap-2">
              {displayItems.map((item) => (
                <TimelineRow key={item.summaryId} item={item} />
              ))}
            </div>
          )}

          {/* maxItems 초과 시 전체 보기 링크 */}
          {maxItems != null &&
            filteredTotal > maxItems &&
            (fullViewHref ? (
              <a
                href={fullViewHref}
                className="block text-center mt-2.5 text-[13px] text-primary font-medium no-underline"
              >
                전체 {filteredTotal}건 보기 &rarr;
              </a>
            ) : (
              <p className="text-center mt-2.5 mb-0 text-xs text-muted-foreground">
                전체 {filteredTotal}건
              </p>
            ))}
        </>
      )}
    </>
  );

  if (maxItems != null) return content;

  return (
    <section className="panel">
      <div className="panel-head">
        <h3>경쟁사 동향</h3>
      </div>
      {content}
    </section>
  );
}

/* ── Sub-components ── */

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "px-3 py-1 rounded-full border-none text-[11px] cursor-pointer transition-all duration-150",
        active
          ? "bg-primary text-primary-foreground font-semibold"
          : "bg-secondary text-muted-foreground hover:bg-secondary/70"
      )}
    >
      {children}
    </button>
  );
}

function TimelineRow({ item }: { item: CompetitorTimelineItem }) {
  const event = EVENT_LABELS[item.eventType ?? "OTHER"] ?? EVENT_LABELS.OTHER;
  const badge = importanceBadge(item.importanceScore);

  return (
    <a
      href={item.sourceLink}
      target="_blank"
      rel="noopener noreferrer"
      className="grid grid-cols-[auto_1fr_auto] gap-3 items-start px-4 py-3.5 bg-muted rounded-xl no-underline text-inherit transition-colors duration-150 hover:bg-muted/70"
    >
      {/* 왼쪽: 날짜 + 이벤트 */}
      <div className="flex flex-col items-center gap-1 min-w-[52px]">
        <span className="text-muted-foreground text-[11px]">{formatDate(item.createdAt)}</span>
        <span className="px-2 py-0.5 rounded-full bg-primary/10 text-[10px] text-primary font-semibold whitespace-nowrap">
          {event.icon} {event.label}
        </span>
      </div>

      {/* 가운데: 경쟁사 + 제목 */}
      <div className="min-w-0">
        <div className="flex items-center gap-1.5 mb-1">
          <span className="px-2 py-0.5 rounded-full bg-[var(--status-neutral-bg)] text-[11px] text-[var(--status-neutral-text)] font-semibold shrink-0">
            {item.competitorName}
          </span>
        </div>
        <span className="text-[13px] leading-snug line-clamp-2">
          {item.title}
        </span>
      </div>

      {/* 오른쪽: 중요도 뱃지 */}
      <div className="flex items-center min-w-9">
        {badge && (
          <span className={cn("px-2.5 py-0.5 rounded-full text-[11px] font-semibold", badge.className)}>
            {badge.label}
          </span>
        )}
      </div>
    </a>
  );
}
