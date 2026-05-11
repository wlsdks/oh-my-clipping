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

interface ZeroSignalActivationWarningProps {
  open: boolean;
  categoryId: string;
  /** 새로 입력한 includeKeywords — 비어있다가 최초로 채워지는 순간에만 이 경고가 뜬다. */
  proposedIncludeKeywords: string[];
  /**
   * dry-run 으로 계산된 "지난 30일 동안 자동 제외될 건수". 선택.
   * 아직 dry-run 을 돌리지 않았다면 undefined — 그 경우 건수 문구는 숨긴다.
   */
  previewCount?: number;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Zero-signal 자동 제외 룰이 "비활성 → 활성" 으로 넘어가는 순간 운영자에게 확인받는 경고 다이얼로그.
 *
 * 배경:
 *  - Zero-signal 룰은 `includeKeywords` 가 비어있으면 절대 발동하지 않는다.
 *  - 비어있던 카테고리에 키워드를 "처음으로" 추가하면 해당 카테고리의 OTHER + NEUTRAL + 키워드 미매칭
 *    기사가 전부 자동 EXCLUDE 로 전환된다 — 운영자가 의도치 않게 중요 기사를 놓칠 수 있는 큰 상태 전이.
 *  - 따라서 저장 직전에 "이해했습니다" 확인을 받는다.
 *
 * - `previewCount` 가 있으면 "지난 30일 {N}건 제외 예정" 문구를 보강한다.
 * - `onCancel` 은 닫기 버튼/ESC/바깥 클릭에도 연결된다.
 */
export function ZeroSignalActivationWarning({
  open,
  categoryId,
  proposedIncludeKeywords,
  previewCount,
  onConfirm,
  onCancel,
}: ZeroSignalActivationWarningProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent
        className="sm:max-w-lg"
        data-testid="zero-signal-activation-warning"
        data-category-id={categoryId}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle
              className="h-5 w-5 text-[var(--status-warning-text)]"
              aria-hidden="true"
            />
            Zero-signal 자동 제외 룰이 활성화됩니다
          </DialogTitle>
          <DialogDescription>
            이 카테고리에 키워드를 추가하면 OTHER + NEUTRAL + 이 키워드들 중 매칭 0건 기사가 자동
            제외됩니다. 중요 기사를 놓칠 수 있으니 미리보기 확인 후 저장하세요.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          {/* 사용자가 방금 입력한 키워드 미리보기 — 공백 키워드는 부모가 걸러 주지만 UI 레벨에서도 안전하게 filter */}
          {proposedIncludeKeywords.length > 0 && (
            <div
              className="rounded-xl bg-muted p-3"
              data-testid="zero-signal-keyword-list"
            >
              <p className="text-xs text-muted-foreground">추가될 키워드</p>
              <ul className="mt-1.5 flex flex-wrap gap-1.5">
                {proposedIncludeKeywords
                  .filter((keyword) => keyword.trim().length > 0)
                  .map((keyword) => (
                    <li
                      key={keyword}
                      className="rounded-full bg-background px-2.5 py-0.5 text-xs font-medium text-foreground"
                    >
                      {keyword}
                    </li>
                  ))}
              </ul>
            </div>
          )}

          {/* dry-run 결과 건수가 있으면 강조 — 없으면 숨긴다 */}
          {previewCount !== undefined && (
            <p
              className="rounded-lg bg-[var(--status-warning-bg)] px-3 py-2 text-sm text-[var(--status-warning-text)]"
              data-testid="zero-signal-preview-count"
            >
              지난 30일 <span className="font-semibold tabular-nums">{previewCount.toLocaleString("ko-KR")}</span>건이
              제외될 예정입니다.
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={onCancel}
            data-testid="zero-signal-cancel"
          >
            취소
          </Button>
          <Button
            onClick={onConfirm}
            data-testid="zero-signal-confirm"
          >
            이해했습니다, 저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
