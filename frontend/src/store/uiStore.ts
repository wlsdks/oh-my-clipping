import { create } from "zustand";
import { persist } from "zustand/middleware";
import { applyTheme, type Theme } from "@/lib/theme";

interface UiState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

export const uiStore = create<UiState>()(
  persist(
    (set) => ({
      theme: "light" as Theme,
      setTheme: (theme) => {
        applyTheme(theme);
        set({ theme });
      }
    }),
    {
      name: "ui-store",
      // React hydration 후 저장된 테마 재적용 (FOUC 방지 스크립트 실패 시 보완)
      onRehydrateStorage: () => (state) => {
        if (state) applyTheme(state.theme);
      }
    }
  )
);

export const useUiStore = uiStore;
