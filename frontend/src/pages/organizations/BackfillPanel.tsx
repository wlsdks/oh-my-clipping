import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { backfillService } from "@/services/backfillService";
import type { BackfillCandidate } from "@/services/backfillService";
import { backfillKeys } from "@/queries/backfillKeys";
import { organizationKeys } from "@/queries/organizationKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

/** 신뢰도 레이블 매핑. */
const CONFIDENCE_LABELS: Record<string, string> = {
  high: "고신뢰",
  medium: "중간",
  low: "저신뢰",
};

/** 신뢰도별 배지 스타일 — semantic color token 사용. */
function ConfidenceBadge({ confidence }: { confidence: string }) {
  if (confidence === "high") {
    return (
      <Badge
        className="bg-[var(--status-success-bg)] text-[var(--status-success-text)] border-0"
        variant="outline"
      >
        고신뢰
      </Badge>
    );
  }
  if (confidence === "medium") {
    return (
      <Badge
        className="bg-[var(--status-warning-bg)] text-[var(--status-warning-text)] border-0"
        variant="outline"
      >
        중간
      </Badge>
    );
  }
  return (
    <Badge
      className="bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border-0"
      variant="outline"
    >
      저신뢰
    </Badge>
  );
}

/** 기존 구독에서 조직을 자동으로 매칭하여 적용하는 Backfill 관리 패널. */
export function BackfillPanel() {
  const qc = useQueryClient();

  // 필터 상태
  const [confidence, setConfidence] = useState<string>("high");
  const [includeMedium, setIncludeMedium] = useState(false);
  const [categoryId, setCategoryId] = useState("");

  // 선택 상태 — sourceId 기준
  const [selected, setSelected] = useState<Set<string>>(new Set());

  // apply 후 에러 목록 표시용
  const [applyErrors, setApplyErrors] = useState<Array<{ candidateId: string; reason: string }>>(
    [],
  );

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: backfillKeys.preview({ confidence, includeMedium, categoryId: categoryId || undefined }),
    queryFn: () =>
      backfillService.preview({
        confidence,
        includeMedium,
        categoryId: categoryId || undefined,
      }),
  });

  const applyMutation = useMutation({
    mutationFn: (ids: string[]) => backfillService.apply({ candidateIds: ids }),
    onSuccess: (result) => {
      // backfill 전체 쿼리 무효화
      qc.invalidateQueries({ queryKey: backfillKeys.all });
      // 영향받은 카테고리 조직 목록도 무효화
      result.affectedCategoryIds.forEach((cid) =>
        qc.invalidateQueries({ queryKey: organizationKeys.byCategory(cid) }),
      );
      toast.success(`${result.total}개 중 ${result.succeeded}개 적용 성공`);
      setSelected(new Set());
      setApplyErrors(result.errors);
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "적용에 실패했어요"));
    },
  });

  // 페이지 이탈 방지 — apply 진행 중에는 beforeunload 이벤트 등록
  useEffect(() => {
    if (!applyMutation.isPending) return;

    function handleBeforeUnload(e: BeforeUnloadEvent) {
      e.preventDefault();
      e.returnValue = "";
    }

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, [applyMutation.isPending]);

  const candidates = data?.candidates ?? [];
  const byConfidence = data?.byConfidence ?? { high: 0, medium: 0, low: 0 };

  // 전체 선택/해제
  function handleSelectAll(checked: boolean) {
    if (checked) {
      setSelected(new Set(candidates.map((c) => c.sourceId)));
    } else {
      setSelected(new Set());
    }
  }

  function handleSelectRow(sourceId: string, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(sourceId);
      else next.delete(sourceId);
      return next;
    });
  }

  const allSelected = candidates.length > 0 && selected.size === candidates.length;
  const someSelected = selected.size > 0 && selected.size < candidates.length;
  const isOverLimit = selected.size > 100;

  function handleApply() {
    if (selected.size === 0 || isOverLimit || applyMutation.isPending) return;
    setApplyErrors([]);
    applyMutation.mutate([...selected]);
  }

  return (
    <div className="flex flex-col gap-4">
      {/* 1. 필터 바 */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground whitespace-nowrap">신뢰도 필터</span>
          <Select value={confidence} onValueChange={setConfidence}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="high">고신뢰</SelectItem>
              <SelectItem value="medium">중간</SelectItem>
              <SelectItem value="low">저신뢰</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-center gap-2">
          <Checkbox
            id="include-medium"
            checked={includeMedium}
            onCheckedChange={(checked) => setIncludeMedium(!!checked)}
          />
          <label htmlFor="include-medium" className="text-sm cursor-pointer select-none">
            Medium 포함
          </label>
        </div>

        <Input
          className="w-56"
          placeholder="카테고리 ID 필터 (선택)"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
        />
      </div>

      {/* 2. 신뢰도 요약 strip */}
      {data && (
        <div className="flex items-center gap-2">
          <Badge
            className="bg-[var(--status-success-bg)] text-[var(--status-success-text)] border-0"
            variant="outline"
          >
            고신뢰 {byConfidence.high}
          </Badge>
          <Badge
            className="bg-[var(--status-warning-bg)] text-[var(--status-warning-text)] border-0"
            variant="outline"
          >
            중간 {byConfidence.medium}
          </Badge>
          <Badge
            className="bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border-0"
            variant="outline"
          >
            저신뢰 {byConfidence.low}
          </Badge>
        </div>
      )}

      {/* 로딩/에러 상태 */}
      {isLoading && (
        <div className="flex items-center justify-center py-12 text-muted-foreground gap-2">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm">후보를 불러오는 중…</span>
        </div>
      )}

      {isError && (
        <div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
          <p className="text-sm">후보 목록을 불러오지 못했어요</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      {/* 3. 후보 테이블 */}
      {!isLoading && !isError && (
        <>
          {candidates.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
              <p className="text-sm">조건에 맞는 후보가 없어요</p>
              <p className="text-xs">신뢰도 필터를 낮추거나 Medium 포함을 활성화해 보세요</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">
                    <Checkbox
                      checked={allSelected}
                      data-state={someSelected ? "indeterminate" : undefined}
                      onCheckedChange={handleSelectAll}
                      aria-label="전체 선택"
                    />
                  </TableHead>
                  <TableHead>소스</TableHead>
                  <TableHead>매칭 기업</TableHead>
                  <TableHead>카테고리</TableHead>
                  <TableHead>신뢰도</TableHead>
                  <TableHead className="text-right">Precision</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {candidates.map((candidate: BackfillCandidate) => (
                  <TableRow key={candidate.sourceId}>
                    <TableCell>
                      <Checkbox
                        checked={selected.has(candidate.sourceId)}
                        onCheckedChange={(checked) =>
                          handleSelectRow(candidate.sourceId, !!checked)
                        }
                        aria-label={`${candidate.sourceName} 선택`}
                      />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col gap-0.5">
                        <span className="font-medium text-sm">{candidate.sourceName}</span>
                        <span className="text-xs text-muted-foreground truncate max-w-[200px]">
                          {candidate.sourceUrl}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col gap-0.5">
                        <span className="text-sm">{candidate.matchedCompanyName}</span>
                        {candidate.stockCode && (
                          <span className="text-xs text-muted-foreground">{candidate.stockCode}</span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="text-sm">{candidate.categoryName}</span>
                    </TableCell>
                    <TableCell>
                      <ConfidenceBadge confidence={candidate.confidence} />
                    </TableCell>
                    <TableCell className="text-right font-tabular-nums text-sm">
                      {candidate.precision.toFixed(2)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {/* 4. 적용 액션 행 */}
          <div className="flex items-center gap-3 pt-2 border-t">
            <p className="text-sm text-muted-foreground tabular-nums">선택 {selected.size}개</p>
            <Button
              onClick={handleApply}
              disabled={selected.size === 0 || isOverLimit || applyMutation.isPending}
            >
              {applyMutation.isPending && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
              선택 적용
            </Button>
            {isOverLimit && (
              <p className="text-xs text-destructive">
                한 번에 최대 100개까지 적용할 수 있어요
              </p>
            )}
          </div>

          {/* 5. 진행 / 결과 피드백 */}
          {applyMutation.isPending && (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>적용 중…</span>
            </div>
          )}

          {applyErrors.length > 0 && (
            <div className="rounded-lg border border-destructive/30 bg-[var(--status-danger-bg)] p-3">
              <p className="text-xs font-medium text-[var(--status-danger-text)] mb-1">
                일부 항목 적용 실패
              </p>
              <ul className="text-xs text-[var(--status-danger-text)] space-y-0.5">
                {applyErrors.map((err) => (
                  <li key={err.candidateId}>
                    <span className="font-mono">{err.candidateId}</span>: {err.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// 신뢰도 레이블 — 외부 노출용
export { CONFIDENCE_LABELS };
