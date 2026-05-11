import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/utils/cn";
import type { ReviewItemSummary } from "./types";

interface SpotCheckDialogProps {
  open: boolean;
  items: ReviewItemSummary[];
  onConfirm: () => void;
  onCancel: () => void;
  /**
   * 테스트/결정성을 위해 샘플링 함수를 주입할 수 있다.
   * 미지정 시 `Math.random` 기반 Fisher-Yates 샘플링을 사용한다.
   */
  sample?: (items: ReviewItemSummary[], size: number) => ReviewItemSummary[];
}

// 스팟체크에 표시할 최대 샘플 수. 5건 전부 OK 해야 일괄 승인을 허용한다.
const SPOT_CHECK_SAMPLE_SIZE = 5;

type Verdict = "pending" | "ok" | "ng";

/**
 * 50건 이상 일괄 승인 전에 **랜덤 샘플 5건**을 강제로 보여주고
 * 운영자가 각 항목을 OK / NG 로 판정하게 만드는 다이얼로그.
 *
 * - 5건 모두 OK → "전체 승인" 활성화 → `onConfirm` 호출
 * - 1건이라도 NG → "전체 승인" disabled + 품질 경고 배너 노출
 * - items.length < 5 면 가능한 만큼만 노출 (전체 검증 후 승인)
 *
 * 랜덤 샘플링은 `Math.random` 기반이라 렌더마다 결과가 달라진다.
 * 결정성이 필요한 테스트는 `sample` 프롭으로 결정적 함수를 주입한다.
 */
export function SpotCheckDialog({
  open,
  items,
  onConfirm,
  onCancel,
  sample,
}: SpotCheckDialogProps) {
  // 다이얼로그가 열릴 때마다 새 샘플을 뽑는다 (재시도 시 다른 샘플)
  const [sampled, setSampled] = useState<ReviewItemSummary[]>([]);
  // summaryId → Verdict 맵 — 다이얼로그가 열릴 때마다 초기화
  const [verdicts, setVerdicts] = useState<Record<string, Verdict>>({});

  // 다이얼로그 open 전환 시 샘플 재추출 + verdict 초기화
  useEffect(() => {
    if (!open) return;
    // 샘플링 함수 선택 — 미지정 시 Math.random 기반
    const picker = sample ?? defaultSample;
    const picked = picker(items, SPOT_CHECK_SAMPLE_SIZE);
    setSampled(picked);
    // 모든 항목을 pending 으로 초기화 — 명시적 OK 판정 필수
    const initial: Record<string, Verdict> = {};
    for (const item of picked) {
      initial[item.summaryId] = "pending";
    }
    setVerdicts(initial);
  }, [open, items, sample]);

  // OK / NG 토글 — 같은 버튼 재클릭 시 pending 으로 되돌림
  function setVerdict(summaryId: string, next: "ok" | "ng") {
    setVerdicts((prev) => {
      const current = prev[summaryId];
      return {
        ...prev,
        [summaryId]: current === next ? "pending" : next,
      };
    });
  }

  // 승인 가능 조건: 모든 샘플이 OK (NG 1건이라도 있으면 즉시 차단)
  const allOk =
    sampled.length > 0 && sampled.every((item) => verdicts[item.summaryId] === "ok");
  const hasNg = sampled.some((item) => verdicts[item.summaryId] === "ng");

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="sm:max-w-lg" data-testid="spot-check-dialog">
        <DialogHeader>
          <DialogTitle>스팟체크 — 랜덤 {sampled.length}건 확인</DialogTitle>
          <DialogDescription>
            50건 이상 일괄 승인은 품질 위험이 커요. 아래 샘플을 각각 판정해 주세요.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          {/* NG 경고 배너 — 1건이라도 NG 면 즉시 전체 취소 권장 */}
          {hasNg && (
            <div
              role="alert"
              data-testid="spot-check-ng-warning"
              className="flex items-start gap-2 rounded-lg border border-[var(--status-danger-text)]/30 bg-[var(--status-danger-bg)] px-3 py-2 text-xs text-[var(--status-danger-text)]"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <p>품질 문제 발견 — 전체 취소 권장</p>
            </div>
          )}

          <ul className="space-y-2">
            {sampled.map((item) => {
              const v = verdicts[item.summaryId] ?? "pending";
              return (
                <li
                  key={item.summaryId}
                  data-testid="spot-check-item"
                  className="rounded-xl border bg-card p-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground line-clamp-2">
                        {item.title}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground tabular-nums">
                        {item.eventType ?? "이벤트 없음"} · 중요도 {item.score.toFixed(2)}
                      </p>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      <button
                        type="button"
                        aria-label={`${item.title} OK`}
                        aria-pressed={v === "ok"}
                        data-testid="spot-check-ok"
                        onClick={() => setVerdict(item.summaryId, "ok")}
                        className={cn(
                          "rounded-full px-3 py-1 text-xs font-medium transition",
                          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                          v === "ok"
                            ? "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
                            : "bg-muted text-muted-foreground hover:bg-muted/80",
                        )}
                      >
                        OK
                      </button>
                      <button
                        type="button"
                        aria-label={`${item.title} NG`}
                        aria-pressed={v === "ng"}
                        data-testid="spot-check-ng"
                        onClick={() => setVerdict(item.summaryId, "ng")}
                        className={cn(
                          "rounded-full px-3 py-1 text-xs font-medium transition",
                          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                          v === "ng"
                            ? "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
                            : "bg-muted text-muted-foreground hover:bg-muted/80",
                        )}
                      >
                        NG
                      </button>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            취소
          </Button>
          <Button onClick={onConfirm} disabled={!allOk} data-testid="spot-check-confirm">
            전체 승인
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * 기본 샘플링 — `Math.random` 기반 Fisher-Yates shuffle 로 상위 `size` 개 추출.
 * items.length <= size 면 전부 반환한다.
 */
function defaultSample(
  items: ReviewItemSummary[],
  size: number,
): ReviewItemSummary[] {
  if (items.length <= size) return [...items];
  // copy + Fisher-Yates partial shuffle
  const pool = [...items];
  const n = pool.length;
  for (let i = 0; i < size; i++) {
    const j = i + Math.floor(Math.random() * (n - i));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  return pool.slice(0, size);
}
