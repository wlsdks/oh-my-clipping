import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { UserInfo } from "@/types/auth";

interface AuthState {
  isLoggedIn: boolean;
  user: UserInfo | null;
  login: (user: UserInfo) => void;
  logout: () => void;
}

/**
 * `/api/me` / 로그인 응답이 FK 필드(`departmentId` 등)를 아직 포함하지 않는 배포 단계에서도
 * legacy 이름 캐시(`department`/`team`)만으로 UI 가 안전하게 동작하도록 보정한다.
 *
 * - BE 가 FK 필드를 내려주면 그대로 저장한다.
 * - BE 가 legacy 필드만 내려주면 FK 필드는 `null` 로 둔다 (UI 가 legacy 폴백 경로로 흘러가게).
 * - 반대로 FK 만 있고 legacy 이름이 없으면, UI 폴백을 깨지 않도록 JOIN 이름을 legacy 슬롯에 미러링한다.
 */
function normalizeUserInfo(input: UserInfo): UserInfo {
  const departmentName = input.departmentName ?? input.department ?? null;
  const teamName = input.teamName ?? input.team ?? null;
  return {
    ...input,
    department: input.department ?? departmentName,
    team: input.team ?? teamName,
    departmentId: input.departmentId ?? null,
    teamId: input.teamId ?? null,
    departmentName,
    teamName,
  };
}

export const authStore = create<AuthState>()(
  persist(
    (set) => ({
      isLoggedIn: false,
      user: null,
      login: (user) => set({ isLoggedIn: true, user: normalizeUserInfo(user) }),
      logout: () => set({ isLoggedIn: false, user: null })
    }),
    { name: "auth-store" }
  )
);

export const useAuthStore = authStore;
