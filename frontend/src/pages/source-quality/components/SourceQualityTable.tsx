// frontend/src/pages/source-quality/components/SourceQualityTable.tsx
import { useState } from "react";
import { Pencil, PauseCircle, PlayCircle } from "lucide-react";
import { cn } from "@/utils/cn";
import type { SourceQualityRow } from "@/types/sourceQuality";

/**
 * 소스 액션 콜백에 전달되는 최소 식별자.
 * expectedUpdatedAt 은 optimistic update + stale detection 용.
 */
export interface SourceActionArgs {
  sourceId: string;
  sourceName: string;
  expectedUpdatedAt: string;
}

interface Props {
  rows: SourceQualityRow[];
  onEdit: (sourceId: string) => void;
  onDeactivate: (args: SourceActionArgs) => void;
  onActivate: (args: SourceActionArgs) => void;
}

type SortBy = "name" | "delivered" | "clickRate" | "likeRate";
type SortDir = "asc" | "desc";
type StatusFilter = "activeAll" | "normal" | "review" | "default" | "inactive";

// status chip 정의 — 기본 필터 + label
const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: "activeAll", label: "전체 (활성)" },
  { key: "normal", label: "정상" },
  { key: "review", label: "검토 필요" },
  { key: "default", label: "신호 부족" },
  { key: "inactive", label: "비활성" },
];

// 상태 뱃지 매핑 — 시맨틱 색상 토큰 사용
const STATUS_BADGE: Record<SourceQualityRow["statusLabel"], { label: string; className: string }> = {
  normal: {
    label: "정상",
    className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
  },
  review: {
    label: "검토 필요",
    className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]",
  },
  default: {
    label: "신호 부족",
    className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]",
  },
};

// null-safe 정렬 비교기 — null 은 asc/desc 모두 맨 뒤로
function compareNullable(a: number | null, b: number | null, dir: SortDir): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1; // null 은 항상 뒤
  if (b == null) return -1;
  return dir === "asc" ? a - b : b - a;
}

function sortFn(by: SortBy, dir: SortDir): (a: SourceQualityRow, b: SourceQualityRow) => number {
  if (by === "name") {
    return (a, b) => {
      const cmp = a.sourceName.localeCompare(b.sourceName, "ko");
      return dir === "asc" ? cmp : -cmp;
    };
  }
  if (by === "delivered") {
    return (a, b) => (dir === "asc" ? a.delivered - b.delivered : b.delivered - a.delivered);
  }
  if (by === "clickRate") {
    return (a, b) => compareNullable(a.clickRatePct, b.clickRatePct, dir);
  }
  // likeRate
  return (a, b) => compareNullable(a.likeRatePct, b.likeRatePct, dir);
}

/**
 * 소스 품질 테이블.
 *
 * - 기본 정렬: 클릭률 asc (나쁜 순 위) — 검토 대상을 빠르게 찾게
 * - 기본 필터: activeAll — 활성 소스만 노출
 * - null clickRate/likeRate 는 asc/desc 모두 맨 뒤
 * - 수동 URL (sourceId=null): 모든 액션 disabled + title tooltip
 */
export function SourceQualityTable({ rows, onEdit, onDeactivate, onActivate }: Props) {
  const [sortBy, setSortBy] = useState<SortBy>("clickRate");
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("activeAll");

  // 헤더 클릭 핸들러 — 같은 컬럼이면 방향 토글, 다른 컬럼이면 asc 로 시작
  const onHeaderClick = (next: SortBy) => {
    if (sortBy === next) {
      setSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortBy(next);
      setSortDir("asc");
    }
  };

  // 필터 파이프라인 — inactive 외에선 활성 소스만, 뒤이어 statusLabel 매칭
  const filtered = rows.filter((r) => {
    if (statusFilter === "inactive") return r.isActive === false;
    if (r.isActive === false) return false;
    if (statusFilter === "activeAll") return true;
    return r.statusLabel === statusFilter;
  });

  const sorted = filtered.slice().sort(sortFn(sortBy, sortDir));

  const ariaSortFor = (col: SortBy): "ascending" | "descending" | "none" => {
    if (sortBy !== col) return "none";
    return sortDir === "asc" ? "ascending" : "descending";
  };

  const isInactiveView = statusFilter === "inactive";

  return (
    <section
      data-testid="source-quality-table"
      aria-label="소스 품질 목록"
      className="space-y-3"
    >
      {/* 상태 필터 chips — radiogroup */}
      <div
        role="radiogroup"
        aria-label="상태 필터"
        className="flex flex-wrap gap-2"
      >
        {STATUS_FILTERS.map((f) => {
          const checked = statusFilter === f.key;
          return (
            <button
              key={f.key}
              type="button"
              role="radio"
              aria-checked={checked}
              onClick={() => setStatusFilter(f.key)}
              className={cn(
                "rounded-full px-3 py-1 text-xs font-medium transition-colors border",
                checked
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-card text-foreground border-border hover:bg-accent",
              )}
            >
              {f.label}
            </button>
          );
        })}
      </div>

      <div className="rounded-lg border border-border bg-card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-muted/30 text-xs text-muted-foreground">
            <tr>
              <th
                scope="col"
                className="px-3 py-2 text-left"
                aria-sort={ariaSortFor("name")}
              >
                <SortHeaderButton
                  label="소스명"
                  active={sortBy === "name"}
                  dir={sortDir}
                  onClick={() => onHeaderClick("name")}
                />
              </th>
              <th
                scope="col"
                className="px-3 py-2 text-right"
                aria-sort={ariaSortFor("delivered")}
              >
                <SortHeaderButton
                  label="발송"
                  active={sortBy === "delivered"}
                  dir={sortDir}
                  onClick={() => onHeaderClick("delivered")}
                  align="right"
                />
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                유니크 클릭
              </th>
              <th
                scope="col"
                className="px-3 py-2 text-right"
                aria-sort={ariaSortFor("clickRate")}
              >
                <SortHeaderButton
                  label="클릭률"
                  active={sortBy === "clickRate"}
                  dir={sortDir}
                  onClick={() => onHeaderClick("clickRate")}
                  align="right"
                />
              </th>
              <th
                scope="col"
                className="px-3 py-2 text-right"
                aria-sort={ariaSortFor("likeRate")}
              >
                <SortHeaderButton
                  label="좋아요율"
                  active={sortBy === "likeRate"}
                  dir={sortDir}
                  onClick={() => onHeaderClick("likeRate")}
                  align="right"
                />
              </th>
              <th scope="col" className="px-3 py-2 text-center">
                상태
              </th>
              <th scope="col" className="px-3 py-2 text-center">
                액션
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.length === 0 && (
              <tr>
                <td colSpan={7} className="px-3 py-6 text-center text-muted-foreground">
                  조건에 맞는 소스가 없습니다.
                </td>
              </tr>
            )}
            {sorted.map((r) => (
              <SourceRow
                key={r.sourceId ?? `manual-${r.sourceName}`}
                row={r}
                isInactiveView={isInactiveView}
                onEdit={onEdit}
                onDeactivate={onDeactivate}
                onActivate={onActivate}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

interface SortHeaderButtonProps {
  label: string;
  active: boolean;
  dir: SortDir;
  onClick: () => void;
  align?: "left" | "right";
}

// 정렬 트리거 버튼 — 헤더 셀 전체 클릭 가능하게
function SortHeaderButton({ label, active, dir, onClick, align = "left" }: SortHeaderButtonProps) {
  const indicator = active ? (dir === "asc" ? "▲" : "▼") : "";
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 text-xs font-medium hover:text-foreground transition-colors",
        align === "right" && "justify-end w-full",
        active ? "text-foreground" : "text-muted-foreground",
      )}
    >
      <span>{label}</span>
      {indicator && <span className="text-[10px]">{indicator}</span>}
    </button>
  );
}

interface SourceRowProps {
  row: SourceQualityRow;
  isInactiveView: boolean;
  onEdit: (sourceId: string) => void;
  onDeactivate: (args: SourceActionArgs) => void;
  onActivate: (args: SourceActionArgs) => void;
}

// 개별 행 — sourceId=null (수동 URL) 은 모든 액션 disabled
function SourceRow({ row, isInactiveView, onEdit, onDeactivate, onActivate }: SourceRowProps) {
  const isManual = row.sourceId == null;
  const disabledTitle = "수동 URL 은 편집 불가";
  const badge = STATUS_BADGE[row.statusLabel];

  // 액션 콜백 공통 args — non-null sourceId 가정
  const actionArgs = (): SourceActionArgs => ({
    sourceId: row.sourceId as string,
    sourceName: row.sourceName,
    expectedUpdatedAt: row.updatedAt,
  });

  return (
    <tr data-testid="source-row" className="border-t border-border">
      <td className="px-3 py-2 break-keep">{row.sourceName}</td>
      <td className="px-3 py-2 text-right tabular-nums">{row.delivered.toLocaleString("ko-KR")}</td>
      <td className="px-3 py-2 text-right tabular-nums">
        {row.uniqueUserClicks.toLocaleString("ko-KR")}
      </td>
      <td className="px-3 py-2 text-right tabular-nums">
        {row.clickRatePct == null ? "—" : `${row.clickRatePct.toFixed(1)}%`}
      </td>
      <td className="px-3 py-2 text-right tabular-nums">
        {row.likeRatePct == null ? "—" : `${row.likeRatePct.toFixed(1)}%`}
      </td>
      <td className="px-3 py-2 text-center">
        <span
          className={cn(
            "inline-flex px-2 py-0.5 rounded-full text-xs font-medium",
            badge.className,
          )}
        >
          {badge.label}
        </span>
      </td>
      <td className="px-3 py-2">
        <div className="flex items-center justify-center gap-1">
          {/* 편집 — 항상 표시, 수동 URL 만 disabled */}
          <button
            type="button"
            onClick={() => !isManual && onEdit(row.sourceId as string)}
            disabled={isManual}
            aria-label={`${row.sourceName} 편집`}
            title={isManual ? disabledTitle : "편집"}
            className={cn(
              "inline-flex items-center justify-center h-7 w-7 rounded-md transition-colors",
              "hover:bg-accent text-muted-foreground hover:text-foreground",
              "disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent",
            )}
          >
            <Pencil size={14} />
          </button>

          {/* 비활성 뷰 = 활성화 버튼 / 활성 뷰 = 수집 일시중지 */}
          {isInactiveView ? (
            <button
              type="button"
              onClick={() => !isManual && onActivate(actionArgs())}
              disabled={isManual}
              aria-label={`${row.sourceName} 활성화`}
              title={isManual ? disabledTitle : "활성화"}
              className={cn(
                "inline-flex items-center justify-center h-7 w-7 rounded-md transition-colors",
                "hover:bg-[var(--status-success-bg)] text-muted-foreground",
                "hover:text-[var(--status-success-text)]",
                "disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent",
              )}
            >
              <PlayCircle size={14} />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => !isManual && onDeactivate(actionArgs())}
              disabled={isManual}
              aria-label={`${row.sourceName} 수집 일시중지`}
              title={isManual ? disabledTitle : "수집 일시중지"}
              className={cn(
                "inline-flex items-center justify-center h-7 w-7 rounded-md transition-colors",
                "hover:bg-[var(--status-warning-bg)] text-muted-foreground",
                "hover:text-[var(--status-warning-text)]",
                "disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent",
              )}
            >
              <PauseCircle size={14} />
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}
