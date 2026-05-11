import { create } from "zustand";
import type { StaleEditInfo } from "@/shared/types/common";

/**
 * Stale-edit 충돌(409 STALE_EDIT)을 전역 모달로 띄우기 위한 단순 pub-sub 스토어.
 *
 * mutation onError에서 {@link useStaleEditStore.getState().show}를 호출하면
 * 전역에 마운트된 `<StaleEditModalHost/>`가 감지해 모달을 연다.
 * 사용자가 "최신 불러오기"를 누르면 등록된 `reloadFn`이 실행된다.
 */
export interface StaleEditState {
  /** 현재 열려 있는 충돌 정보. null이면 모달이 닫혀 있다. */
  pending: StaleEditInfo | null;
  /** 사용자가 "최신 불러오기"를 누르면 호출할 refetch 콜백. */
  reloadFn: (() => void | Promise<void>) | null;
  /** 재진입 시 이전 폼을 복원할 수 있게 저장한 로컬 드래프트 키 (선택). */
  draftKey: string | null;
  /** 충돌을 알리고 모달을 연다. */
  show: (
    info: StaleEditInfo,
    reloadFn: () => void | Promise<void>,
    options?: { draftKey?: string | null }
  ) => void;
  /** 모달을 닫고 상태를 초기화한다. */
  clear: () => void;
}

export const useStaleEditStore = create<StaleEditState>((set) => ({
  pending: null,
  reloadFn: null,
  draftKey: null,
  show: (info, reloadFn, options) =>
    set({
      pending: info,
      reloadFn,
      draftKey: options?.draftKey ?? null
    }),
  clear: () => set({ pending: null, reloadFn: null, draftKey: null })
}));
