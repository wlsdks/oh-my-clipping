import { useState } from "react";
import { Sparkles } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { SpotCheckDialog } from "./SpotCheckDialog";
import type { ReviewItemSummary } from "./types";

interface AiRecommendPanelProps {
  /** 이미 필터가 적용된 "승인 후보" 항목들. 정렬 순서는 호출부에서 결정. */
  filteredItems: ReviewItemSummary[];
  /**
   * 일괄 승인 요청. 성공/실패 처리는 내부에서 toast 로 안내한다.
   * 외부에서 추가 side-effect(refetch 등) 를 수행하려면 resolve 후에 처리한다.
   */
  onBulkApprove: (summaryIds: string[]) => Promise<void>;
}

// 스팟체크 강제 임계값. 이 값 이상이면 랜덤 5건 OK/NG 판정을 통과해야 승인 가능.
const SPOT_CHECK_THRESHOLD = 50;
// 패널 프리뷰에 노출할 상위 항목 개수.
const PREVIEW_SIZE = 10;

/**
 * AI 추천 일괄 승인 패널.
 *
 * 동작:
 * - `filteredItems.length` 가 0 이면 빈 상태 안내 (승인 버튼 비활성)
 * - `< 50` 이면 간단한 확인 다이얼로그만 표시
 * - `>= 50` 이면 {@link SpotCheckDialog} 로 랜덤 5건 품질 판정 강제
 *
 * 승인 완료 후 로컬에서는 성공/실패 toast 만 띄우고, 추가 상태 갱신은 호출부가
 * `onBulkApprove` resolve 후에 자신의 쿼리를 무효화한다.
 */
export function AiRecommendPanel({
  filteredItems,
  onBulkApprove,
}: AiRecommendPanelProps) {
  const [simpleConfirmOpen, setSimpleConfirmOpen] = useState(false);
  const [spotCheckOpen, setSpotCheckOpen] = useState(false);
  const [isPending, setIsPending] = useState(false);

  const total = filteredItems.length;
  // 미리보기: 상위 N 개만 노출 (나머지는 "외 M건" 으로 요약)
  const preview = filteredItems.slice(0, PREVIEW_SIZE);
  const needsSpotCheck = total >= SPOT_CHECK_THRESHOLD;
  const canApprove = total > 0 && !isPending;

  // 승인 버튼 클릭 — 임계값에 따라 스팟체크 or 간단 확인 다이얼로그를 띄운다
  function handleApproveClick() {
    if (!canApprove) return;
    if (needsSpotCheck) {
      setSpotCheckOpen(true);
    } else {
      setSimpleConfirmOpen(true);
    }
  }

  // 실제 승인 API 호출 — 다이얼로그 종류와 무관하게 동일 로직
  async function runApprove() {
    if (isPending) return;
    setIsPending(true);
    try {
      // 전체 ID 배열을 그대로 넘긴다 — 백엔드가 중복/없는 ID 는 실패 목록으로 반환
      const ids = filteredItems.map((item) => item.summaryId);
      await onBulkApprove(ids);
      toast.success(`${ids.length}건 승인 처리했어요`);
      setSimpleConfirmOpen(false);
      setSpotCheckOpen(false);
    } catch (error) {
      toast.error(userFriendlyMessage(error, "일괄 승인에 실패했어요"));
    } finally {
      setIsPending(false);
    }
  }

  return (
    <section
      data-testid="ai-recommend-panel"
      aria-label="AI 추천 승인"
      className="rounded-2xl border bg-card p-4 space-y-3"
    >
      <header className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
          <h3 className="text-sm font-semibold text-foreground">AI 추천 승인</h3>
        </div>
        <span
          data-testid="ai-recommend-count"
          className="rounded-full bg-[var(--status-success-bg)] px-2.5 py-0.5 text-xs font-medium text-[var(--status-success-text)] tabular-nums"
        >
          {total}개 항목이 승인 후보
        </span>
      </header>

      {total === 0 ? (
        <p className="text-sm text-muted-foreground">
          현재 필터 조건에 해당하는 승인 후보가 없어요.
        </p>
      ) : (
        <>
          <ol
            data-testid="ai-recommend-preview"
            className="space-y-1.5 rounded-xl bg-muted/40 p-3 text-sm"
          >
            {preview.map((item) => (
              <li
                key={item.summaryId}
                data-testid="ai-recommend-preview-item"
                className="flex items-center justify-between gap-3"
              >
                <span className="flex-1 truncate text-foreground">{item.title}</span>
                <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                  {item.score.toFixed(2)}
                </span>
              </li>
            ))}
            {total > preview.length && (
              <li className="pt-1 text-xs text-muted-foreground">
                외 {total - preview.length}건
              </li>
            )}
          </ol>

          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-muted-foreground">
              {needsSpotCheck
                ? `${SPOT_CHECK_THRESHOLD}건 이상 — 랜덤 스팟체크 후 승인`
                : "확인 후 일괄 승인"}
            </p>
            <Button
              onClick={handleApproveClick}
              disabled={!canApprove}
              data-testid="ai-recommend-approve"
            >
              {isPending ? "처리 중..." : "승인"}
            </Button>
          </div>
        </>
      )}

      {/* 50건 미만 — 간단한 확인 다이얼로그 */}
      <Dialog
        open={simpleConfirmOpen}
        onOpenChange={(next) => {
          if (!isPending) setSimpleConfirmOpen(next);
        }}
      >
        <DialogContent className="sm:max-w-md" data-testid="ai-recommend-simple-confirm">
          <DialogHeader>
            <DialogTitle>{total}건을 일괄 승인할까요?</DialogTitle>
            <DialogDescription>
              승인 처리된 항목은 다음 발송에 포함돼요. 나중에 개별 되돌리기가 가능해요.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setSimpleConfirmOpen(false)}
              disabled={isPending}
            >
              취소
            </Button>
            <Button
              onClick={() => void runApprove()}
              disabled={isPending}
              data-testid="ai-recommend-simple-confirm-button"
            >
              {isPending ? "처리 중..." : "확인"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 50건 이상 — 스팟체크 다이얼로그 */}
      <SpotCheckDialog
        open={spotCheckOpen}
        items={filteredItems}
        onConfirm={() => void runApprove()}
        onCancel={() => {
          if (!isPending) setSpotCheckOpen(false);
        }}
      />
    </section>
  );
}
