import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { GitCompareArrows, ChevronLeft, ChevronRight, ChevronDown, ChevronUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { digestDiffKeys } from "@/queries/digestDiffKeys";
import { digestDiffService, type DigestDiffEntry } from "@/services/digestDiffService";

/* ── 상수 ── */

const PAGE_SIZE = 50;

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function thirtyDaysAgoStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
}

/* ── 헬퍼 ── */

/** ISO datetime → 짧은 표시 (MM/DD HH:mm) */
function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  const hour = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hour}:${min}`;
}

/* ── 스켈레톤 ── */

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          {Array.from({ length: 7 }).map((_, j) => (
            <TableCell key={j}>
              <div className="h-4 w-full animate-pulse rounded bg-muted" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}

/* ── 행 컴포넌트 (클릭 시 legacy/new 요약 펼치기) ── */

function DiffRow({ entry }: { entry: DigestDiffEntry }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <>
      <TableRow
        className="cursor-pointer hover:bg-muted/50 transition-colors"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <TableCell className="text-sm tabular-nums whitespace-nowrap">
          {entry.digestDate}
        </TableCell>
        <TableCell className="font-mono text-xs text-muted-foreground">
          {entry.categoryId}
        </TableCell>
        <TableCell>
          <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]">
            {entry.newMode ?? "-"}
          </span>
        </TableCell>
        <TableCell className="text-sm tabular-nums text-right">
          {entry.sectionsCount}
        </TableCell>
        <TableCell className="text-sm tabular-nums text-right">
          {entry.articlesCount}
        </TableCell>
        <TableCell className="text-sm tabular-nums text-right">
          {entry.crossMatchCount > 0 ? (
            <span className="text-[var(--status-success-text)] font-medium">
              ⭐ {entry.crossMatchCount}
            </span>
          ) : (
            <span className="text-muted-foreground">0</span>
          )}
        </TableCell>
        <TableCell className="text-xs text-muted-foreground tabular-nums whitespace-nowrap">
          <span className="flex items-center gap-1">
            {formatDateTime(entry.createdAt)}
            {expanded ? (
              <ChevronUp className="h-3 w-3 shrink-0" />
            ) : (
              <ChevronDown className="h-3 w-3 shrink-0" />
            )}
          </span>
        </TableCell>
      </TableRow>

      {expanded && (
        <TableRow>
          <TableCell colSpan={7} className="bg-muted/30 p-0">
            <div className="grid grid-cols-2 gap-px bg-border">
              {/* Legacy 요약 */}
              <div className="bg-background p-3">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                  Legacy 요약
                </p>
                <pre className="text-xs whitespace-pre-wrap break-all max-h-60 overflow-auto text-foreground font-sans leading-relaxed">
                  {entry.legacySummary ?? "(비어 있음)"}
                </pre>
              </div>
              {/* New 요약 */}
              <div className="bg-background p-3">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                  New 요약
                </p>
                <pre className="text-xs whitespace-pre-wrap break-all max-h-60 overflow-auto text-foreground font-sans leading-relaxed">
                  {entry.newSummary ?? "(비어 있음)"}
                </pre>
              </div>
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

/* ── 메인 페이지 ── */

export function DigestDiffPage() {
  const [categoryId, setCategoryId] = useState("");
  const [from, setFrom] = useState(thirtyDaysAgoStr());
  const [to, setTo] = useState(todayStr());
  const [page, setPage] = useState(0);

  // categoryId 가 있을 때만 API 를 호출한다.
  const enabled = categoryId.trim().length > 0;

  const filter = {
    categoryId: categoryId.trim(),
    from,
    to,
    page,
    size: PAGE_SIZE,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: digestDiffKeys.list(filter),
    queryFn: () => digestDiffService.list(filter),
    enabled,
  });

  const entries = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = Math.ceil(totalElements / PAGE_SIZE);

  const handleFilterChange = () => {
    // 필터가 변경되면 첫 페이지로 돌아간다.
    setPage(0);
  };

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex items-center gap-2">
        <GitCompareArrows className="h-6 w-6 text-muted-foreground" />
        <div>
          <h1 className="text-2xl font-bold">발송 모드 diff</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Shadow 모드 기간의 legacy / account-based 다이제스트 비교 기록
          </p>
        </div>
      </div>

      {/* 필터 바 */}
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">
            카테고리 ID <span className="text-[var(--status-danger-text)]">*</span>
          </label>
          <Input
            placeholder="카테고리 ID 입력"
            value={categoryId}
            onChange={(e) => {
              setCategoryId(e.target.value);
              handleFilterChange();
            }}
            className="w-72"
            aria-label="카테고리 ID"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">시작일</label>
          <Input
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              handleFilterChange();
            }}
            className="w-40"
            aria-label="시작일"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">종료일</label>
          <Input
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              handleFilterChange();
            }}
            className="w-40"
            aria-label="종료일"
          />
        </div>
      </div>

      {/* 안내 — categoryId 미입력 시 */}
      {!enabled && (
        <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
          카테고리 ID 를 입력하면 diff 기록을 조회할 수 있습니다.
        </div>
      )}

      {/* 테이블 */}
      {enabled && (
        <div className="rounded-xl border bg-card overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>날짜</TableHead>
                <TableHead>카테고리</TableHead>
                <TableHead>모드</TableHead>
                <TableHead className="text-right">섹션</TableHead>
                <TableHead className="text-right">기사</TableHead>
                <TableHead className="text-right">교차 일치</TableHead>
                <TableHead>생성 시각</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && <SkeletonRows />}

              {isError && (
                <TableRow>
                  <TableCell colSpan={7} className="text-center py-12 text-sm text-muted-foreground">
                    diff 기록을 불러오는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.
                  </TableCell>
                </TableRow>
              )}

              {!isLoading && !isError && entries.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-center py-16">
                    <div className="flex flex-col items-center gap-2 text-muted-foreground">
                      <GitCompareArrows className="h-8 w-8" />
                      <p className="text-sm">이 기간에 기록된 diff 가 없습니다</p>
                    </div>
                  </TableCell>
                </TableRow>
              )}

              {!isLoading && !isError && entries.map((entry) => (
                <DiffRow key={entry.id} entry={entry} />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {/* 페이지네이션 */}
      {enabled && totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            총 {totalElements}건
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              disabled={page === 0}
              aria-label="이전 페이지"
            >
              <ChevronLeft className="h-4 w-4" />
              이전
            </Button>
            <span className="text-sm text-muted-foreground">
              {page + 1} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
              disabled={page >= totalPages - 1}
              aria-label="다음 페이지"
            >
              다음
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
