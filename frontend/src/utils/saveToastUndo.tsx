import { toast } from "sonner";

import { historyService } from "@/services/historyService";
import type { RevisionResource } from "@/types/revisionHistory";

/**
 * 저장 성공 Toast에 "되돌리기" action을 붙여 30초 동안 노출한다.
 *
 * action 클릭 시:
 *   1. 최근 revision 2건을 조회 (index 1 = 직전 상태)
 *   2. 현재 엔티티의 updatedAt(`savedUpdatedAt`)으로 restore 호출
 *   3. 성공 시 상위의 invalidation 콜백 호출
 */
interface UndoArgs {
  resource: RevisionResource;
  savedId: string;
  savedUpdatedAt: string;
  onRestored: () => void;
  /** 저장 완료 메시지 문구를 커스터마이즈. 기본: "저장됐어요" */
  successMessage?: string;
}

/** 저장 성공 시 30초 Undo action이 붙은 Toast를 띄운다. */
export function showSaveToastWithUndo({
  resource,
  savedId,
  savedUpdatedAt,
  onRestored,
  successMessage = "저장됐어요"
}: UndoArgs) {
  toast.success(successMessage, {
    duration: 30_000,
    action: {
      label: "되돌리기",
      onClick: async () => {
        try {
          // 직전 revision을 찾기 위해 최근 2건만 조회한다. [0]=방금 저장분, [1]=직전 상태.
          const recent = await historyService.getHistory(resource, savedId, 2);
          const previous = recent[1];
          if (!previous) {
            toast.info("되돌릴 이전 버전이 없어요");
            return;
          }
          await historyService.restore(resource, savedId, {
            revisionId: previous.revisionId,
            expectedUpdatedAt: savedUpdatedAt
          });
          toast.success("되돌렸어요");
          onRestored();
        } catch (err) {
          const message = err instanceof Error ? err.message : "되돌리기에 실패했어요";
          toast.error(message);
        }
      }
    }
  });
}
