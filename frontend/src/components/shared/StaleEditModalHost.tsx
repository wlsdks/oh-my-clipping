import { toast } from "sonner";
import { useStaleEditStore } from "@/lib/staleEditBus";
import { StaleEditResolveModal } from "./StaleEditResolveModal";

/**
 * 앱 루트에 한 번 마운트되어 전역 stale-edit 충돌 상태를 모달로 보여준다.
 *
 * 각 mutation의 `onError`에서 {@link useStaleEditStore.getState().show}를 호출하면
 * 이 호스트가 감지해 사용자에게 "최신 불러오기" UX를 제공한다.
 */
export function StaleEditModalHost() {
  const pending = useStaleEditStore((s) => s.pending);
  const reloadFn = useStaleEditStore((s) => s.reloadFn);
  const clear = useStaleEditStore((s) => s.clear);

  async function handleReload() {
    if (!reloadFn) {
      clear();
      return;
    }
    try {
      await reloadFn();
      // 로컬 드래프트가 있다면 "임시 저장됐어요" 힌트를 가볍게 한 번 띄운다.
      if (useStaleEditStore.getState().draftKey) {
        toast.info("이전 편집은 임시 저장됐어요");
      }
    } catch {
      toast.error("최신 내용을 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    } finally {
      clear();
    }
  }

  return (
    <StaleEditResolveModal
      open={pending !== null}
      onOpenChange={(open) => {
        if (!open) clear();
      }}
      staleEditInfo={pending}
      onReload={handleReload}
    />
  );
}
