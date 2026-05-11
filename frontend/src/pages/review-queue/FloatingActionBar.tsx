import { AnimatePresence, motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";
import { MAX_BULK_SELECT } from "./model/constants";

interface FloatingActionBarProps {
  selectedCount: number;
  isPending: boolean;
  isRevertPending?: boolean;
  onApprove: () => void;
  onExclude: () => void;
  onClear: () => void;
}

/**
 * 화면 하단에 고정되는 일괄 액션 바.
 * 선택된 항목이 있을 때만 노출되며, 20건 초과 시 버튼을 비활성화하고 안내 메시지를 보여준다.
 * undo mutation이 진행 중이면 새 bulk 액션을 막아 경쟁 상태를 방지한다 (엣지 2-5).
 */
export function FloatingActionBar({
  selectedCount,
  isPending,
  isRevertPending = false,
  onApprove,
  onExclude,
  onClear,
}: FloatingActionBarProps) {
  const overLimit = selectedCount > MAX_BULK_SELECT;
  const busy = isPending || isRevertPending;
  const disableActions = busy || overLimit || selectedCount === 0;

  return (
    <AnimatePresence>
      {selectedCount > 0 && (
        <motion.div
          initial={{ y: 80, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 80, opacity: 0 }}
          transition={{ duration: 0.2 }}
          role="status"
          aria-live="polite"
          className="fixed bottom-0 left-0 right-0 z-50 border-t bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80"
        >
          <div className="mx-auto flex max-w-screen-xl flex-col gap-2 px-6 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-medium">{selectedCount}건 선택</span>
              {overLimit && (
                <span className="text-xs text-[var(--status-warning-text)]">
                  최대 {MAX_BULK_SELECT}건까지 일괄 처리할 수 있어요. 선택을 줄여 주세요.
                </span>
              )}
              {isRevertPending && !overLimit && (
                <span className="text-xs text-muted-foreground">되돌리는 중이에요. 잠시만요…</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {busy ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  처리 중...
                </div>
              ) : (
                <>
                  <Button size="sm" onClick={onApprove} disabled={disableActions}>
                    보내기
                  </Button>
                  <Button size="sm" variant="outline" onClick={onExclude} disabled={disableActions}>
                    건너뛰기
                  </Button>
                  <Button size="sm" variant="ghost" onClick={onClear} disabled={busy}>
                    선택 해제
                  </Button>
                </>
              )}
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
