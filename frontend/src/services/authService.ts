import { api } from "@/lib/kyInstance";
import type { UserInfo } from "@/types/auth";

export const authService = {
  login: async (email: string, password: string): Promise<UserInfo> => {
    const params = new URLSearchParams({ username: email, password });
    const response = await fetch("/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "ngrok-skip-browser-warning": "true"
      },
      body: params.toString(),
      credentials: "include",
      redirect: "manual"
    });
    // 302 = 로그인 처리됨. opaqueredirect로 올 수 있음.
    // /api/me로 실제 인증 확인.
    if (response.type === "opaqueredirect" || response.status === 302 || response.status === 0) {
      return api.get("me").json<UserInfo>();
    }
    if (!response.ok) {
      throw new Error("아이디 또는 비밀번호가 올바르지 않아요");
    }
    return api.get("me").json<UserInfo>();
  },

  /**
   * V129 이후 signup 요청 payload.
   * `departmentId` / `teamId` 모두 선택 — 본부만 있는 조직, 팀 구분이 없는 부서(예: 인재경영실)
   * 등 다양한 조직 구조를 허용하기 위해 빈 값이면 `null` 로 전달한다. 프론트는
   * `/api/public/departments/tree` 로 옵션을 로드해 cascade 선택 UI 로 입력한다.
   */
  signup: async (data: {
    email: string;
    displayName: string;
    password: string;
    departmentId?: string | null;
    teamId?: string | null;
  }): Promise<void> => {
    await api.post("public/user/auth/signup", {
      json: {
        email: data.email,
        displayName: data.displayName,
        password: data.password,
        departmentId: data.departmentId && data.departmentId.length > 0 ? data.departmentId : null,
        teamId: data.teamId && data.teamId.length > 0 ? data.teamId : null
      }
    }).json();
  },

  logout: async (): Promise<void> => {
    // API 로그아웃은 204 응답만 받고 화면 이동은 클라이언트가 맡는다.
    const response = await fetch("/logout", {
      method: "POST",
      credentials: "include",
      headers: {
        "X-Logout-Mode": "api",
        "ngrok-skip-browser-warning": "true"
      }
    });
    if (!response.ok) {
      throw new Error("로그아웃에 실패했어요");
    }
  },

  /** 비밀번호 변경 (현재 비밀번호 확인 후 새 비밀번호 설정) */
  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await api.post("user/account/change-password", {
      json: { currentPassword, newPassword }
    });
  },

  /** 현재 로그인 사용자 정보를 다시 조회한다 */
  fetchMe: async (): Promise<UserInfo> => {
    return api.get("me").json<UserInfo>();
  }
};
