import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CircleX, ExternalLink, RotateCcw } from "lucide-react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { categoryRuleKeys } from "@/queries/categoryRuleKeys";
import { categoryRuleService } from "@/services/categoryRuleService";
import type { AutoExcludedItem } from "@/types/autoExcludedItem";

/**
 * 자동 제외 감사 상세 드로어.
 *
 * 테이블에서 제목을 클릭했을 때 열리는 우측 Sheet. 기사 본문 컨텍스트(원제/번역제/요약/원문 링크)와
 * 룰 엔진이 이 항목을 자동 제외한 근거(차단된 event_type 목록 또는 zero_signal 필수 키워드)를 함께
 * 보여준다. 룰 근거 섹션은 `GET /api/admin/category-rules/{id}` 를 lazy fetch 한다.
 *
 * nullable 필드 방어:
 *  - `translatedTitle` null → 보조 라인 생략.
 *  - `sourceName`/`publishedAt` null → 메타 라인에서 해당 조각만 filter out (꼬리 separator 없음).
 *  - `sourceUrl` null → 원문 열기 버튼 disabled + 안내 tooltip.
 *  - `zero_signal` 인데 `eventType`/`sentiment` null → 룰 근거 대신 데이터 불일치 경고.
 *
 * Task 5 에서 `AutoExcludeAuditPage` 가 이 컴포넌트를 마운트해 실제 wiring 을 완성한다.
 */

/* ── 상수 ── */

const REASON_EVENT_TYPE_BLACKLIST = "rule:event_type_blacklist";
const REASON_ZERO_SIGNAL = "rule:zero_signal";

/* ── 날짜 포맷 헬퍼 ── */

/** ISO → MM/DD (발행 표시 — 드로어의 메타 라인). */
function formatDateShort(iso: string): string {
  const d = new Date(iso);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${mm}/${dd}`;
}

/** ISO → MM/DD HH:mm (제외 일시 — 푸터 메타). */
function formatDateTimeShort(iso: string): string {
  const d = new Date(iso);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${mm}/${dd} ${hh}:${mi}`;
}

/* ── 룰 근거 섹션 ── */

interface RuleRationaleProps {
  item: AutoExcludedItem;
  rule: import("@/types/category").CategoryRule | undefined;
  isRuleLoading: boolean;
  isRuleError: boolean;
}

function RuleRationale({ item, rule, isRuleLoading, isRuleError }: RuleRationaleProps) {
  // event_type_blacklist 분기.
  if (item.reason === REASON_EVENT_TYPE_BLACKLIST) {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-1.5 text-sm font-semibold">
          <CircleX className="h-4 w-4 text-[var(--status-danger-text)]" />
          <span>이벤트 타입 차단</span>
        </div>
        <p className="text-sm">
          이 기사 event_type: <span className="font-mono">{item.eventType ?? "(미분류)"}</span>
        </p>
        {isRuleLoading && <p className="text-sm text-muted-foreground">룰 정보 불러오는 중...</p>}
        {isRuleError && <p className="text-sm text-destructive">룰 정보를 불러오지 못했어요</p>}
        {rule && (
          <p className="text-sm">
            카테고리 &quot;{item.categoryName}&quot; 차단 목록:{" "}
            <span className="font-mono">[{rule.excludeEventTypes.join(", ")}]</span>
          </p>
        )}
      </div>
    );
  }

  // zero_signal 분기.
  if (item.reason === REASON_ZERO_SIGNAL) {
    // 데이터 불일치 — 룰 조건(eventType=OTHER + sentiment=NEUTRAL) 평가 자체가 불가능했어야 함.
    const hasClassification = item.eventType != null && item.sentiment != null;

    return (
      <div className="space-y-2">
        <div className="flex items-center gap-1.5 text-sm font-semibold">
          <AlertTriangle className="h-4 w-4 text-[var(--status-warning-text)]" />
          <span>시그널 없음</span>
        </div>

        {!hasClassification && (
          <p className="text-sm text-destructive">
            룰 실행 시점의 분류 정보(event_type/sentiment)가 저장되지 않았습니다. 감사 로그 검토가 필요합니다.
          </p>
        )}

        {hasClassification && (
          <>
            <ul className="text-sm space-y-1 list-disc pl-5">
              <li>
                event_type: <span className="font-mono">{item.eventType}</span>
              </li>
              <li>
                sentiment: <span className="font-mono">{item.sentiment}</span>
              </li>
              <li>
                카테고리 &quot;{item.categoryName}&quot;{" "}
                {isRuleLoading
                  ? "(키워드 불러오는 중...)"
                  : isRuleError
                    ? "(키워드 로드 실패)"
                    : rule
                      ? null
                      : "(키워드 정보 없음)"}{" "}
                필수 키워드 {rule && <span className="font-mono">[{rule.includeKeywords.join(", ")}]</span>} 과 본문
                매칭 실패
              </li>
            </ul>
            <p className="text-xs text-muted-foreground">
              ※ 구체 매칭 결과는 저장되지 않아 원인 키워드를 특정할 수 없어요.
            </p>
          </>
        )}
      </div>
    );
  }

  // fallback — 알 수 없는 reason.
  return <p className="text-sm text-muted-foreground">사유: {item.reason}</p>;
}

/* ── Props ── */

interface AutoExcludeDetailDrawerProps {
  item: AutoExcludedItem | null;
  onClose: () => void;
  onRestoreClick: (item: AutoExcludedItem) => void;
  isRestoring: boolean;
}

/* ── 메인 컴포넌트 ── */

export function AutoExcludeDetailDrawer({ item, onClose, onRestoreClick, isRestoring }: AutoExcludeDetailDrawerProps) {
  // 카테고리 룰 lazy fetch — 드로어 열릴 때만.
  const {
    data: rule,
    isLoading: isRuleLoading,
    isError: isRuleError
  } = useQuery({
    queryKey: categoryRuleKeys.detail(item?.categoryId ?? ""),
    queryFn: () => categoryRuleService.getById(item!.categoryId),
    enabled: !!item?.categoryId,
    staleTime: 60_000
  });

  // 메타 라인 조각 — null 은 filter 로 제거해서 꼬리 separator 발생 방지.
  const metaParts = item
    ? [
        item.sourceName,
        item.categoryName,
        item.publishedAt ? `발행 ${formatDateShort(item.publishedAt)}` : null
      ].filter((v): v is string => typeof v === "string" && v.length > 0)
    : [];
  const metaLine = metaParts.join(" · ");

  return (
    <Sheet
      open={!!item}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto">
        {item && (
          <div className="flex flex-col gap-4">
            {/* 타이틀 블록 */}
            <SheetHeader className="space-y-1">
              {item.translatedTitle && <p className="text-sm text-muted-foreground">{item.translatedTitle}</p>}
              <SheetTitle>{item.originalTitle}</SheetTitle>
              {/* a11y — Radix Dialog 가 aria-describedby 를 요구. 시각적으론 sr-only. */}
              <SheetDescription className="sr-only">자동 제외된 기사 상세 및 룰 근거</SheetDescription>
            </SheetHeader>

            {/* 메타 라인 — 비어있으면 아예 렌더하지 않음. */}
            {metaLine.length > 0 && (
              <p data-testid="auto-exclude-meta" className="text-xs text-muted-foreground">
                {metaLine}
              </p>
            )}

            <hr className="border-t" />

            {/* 요약 섹션 */}
            <div>
              <h3 className="text-sm font-semibold mb-2">요약</h3>
              <p className="text-sm leading-relaxed whitespace-pre-wrap">{item.summary}</p>
            </div>

            <hr className="border-t" />

            {/* 룰 근거 */}
            <RuleRationale item={item} rule={rule} isRuleLoading={isRuleLoading} isRuleError={isRuleError} />

            <hr className="border-t" />

            {/* 푸터 메타 — 점수 + 제외 시각. */}
            <p className="text-xs text-muted-foreground">
              중요도 점수: <span className="tabular-nums font-medium">{item.score.toFixed(2)}</span> · 제외{" "}
              {formatDateTimeShort(item.excludedAt)}
            </p>

            <hr className="border-t" />

            {/* 액션 바 (sticky bottom) */}
            <div className="flex items-center justify-between gap-2 sticky bottom-0 bg-background pt-3">
              {/* 원문 열기 — sourceUrl null 이면 disabled. asChild 로 <a> 감싸서 새 탭 열기. */}
              {item.sourceUrl ? (
                <Button variant="outline" size="sm" asChild>
                  <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">
                    <ExternalLink className="h-3.5 w-3.5 mr-1" />
                    원문 열기
                  </a>
                </Button>
              ) : (
                <Button variant="outline" size="sm" disabled title="원문 링크 없음">
                  <ExternalLink className="h-3.5 w-3.5 mr-1" />
                  원문 열기
                </Button>
              )}

              {/* REVIEW 로 복구 — 확인 모달은 부모(AutoExcludeAuditPage) 가 담당. */}
              <Button variant="default" size="sm" onClick={() => onRestoreClick(item)} disabled={isRestoring}>
                <RotateCcw className="h-3.5 w-3.5 mr-1" />
                REVIEW 로 복구
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
