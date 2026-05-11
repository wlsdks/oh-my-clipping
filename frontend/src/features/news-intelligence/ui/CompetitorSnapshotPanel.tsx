import { useQuery } from "@tanstack/react-query";
import { EmptyState } from "@/components/shared/EmptyState";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userFriendlyMessage } from "../../../shared/lib/httpError";

interface CompetitorSnapshotPanelProps {
  /** 경쟁사 뉴스 전체 보기 링크 */
  fullViewHref?: string;
  /** fullViewHref 미지정 시 폴백 링크 */
  detailHref?: string;
}

const KOREA_SHORT_DATE = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
});

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso.slice(5, 10);
    return KOREA_SHORT_DATE.format(d);
  } catch {
    return iso.slice(5, 10);
  }
}

export function CompetitorSnapshotPanel({ fullViewHref, detailHref }: CompetitorSnapshotPanelProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.competitorSnapshot({ days: 7, limit: 3 }),
    queryFn: () => newsIntelligenceService.getCompetitorSnapshot({ days: 7, limit: 3 }),
  });

  const items = Array.isArray(data?.items) ? data.items : [];
  const linkHref = fullViewHref ?? detailHref;

  return (
    <section className="panel">
      <div className="panel-head">
        <h3>경쟁사 동향</h3>
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
      {error && (
        <p className="text-sm text-destructive">
          {userFriendlyMessage(error, "경쟁사 데이터를 불러오지 못했어요")}
        </p>
      )}

      {!isLoading && !error && items.length === 0 && (
        <EmptyState
          title="경쟁사 뉴스가 아직 없어요"
          description="경쟁사 뉴스가 수집되면 여기에 표시돼요."
          className="bg-muted rounded-xl py-6"
        />
      )}

      {!isLoading && !error && items.length > 0 && (
        <>
          <div className="grid gap-1.5">
            {items.map((item) => (
              <a
                key={item.summaryId}
                href={item.sourceLink}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2.5 px-3 py-2.5 bg-muted rounded-[10px] no-underline text-inherit transition-colors duration-150 hover:bg-muted/70"
              >
                <span className="text-muted-foreground text-[11px] min-w-8 shrink-0">
                  {formatDate(item.createdAt)}
                </span>
                <span className="px-2 py-0.5 rounded-full bg-[var(--status-neutral-bg)] text-[11px] text-[var(--status-neutral-text)] font-semibold shrink-0">
                  {item.competitorName}
                </span>
                <span className="flex-1 min-w-0 truncate text-[13px] leading-snug">
                  {item.title}
                </span>
              </a>
            ))}
          </div>

          {linkHref && (
            <a
              href={linkHref}
              className="block text-center mt-3 py-2.5 text-[13px] text-primary font-medium no-underline bg-primary/5 rounded-[10px] transition-colors duration-150 hover:bg-primary/10"
            >
              경쟁사 뉴스에서 더 보기 &rarr;
            </a>
          )}
        </>
      )}
    </section>
  );
}
