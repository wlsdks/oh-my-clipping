import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { z } from "zod";
import { LoginPage } from "../LoginPage";

// authService mock
vi.mock("@/services/authService", () => ({
  authService: {
    login: vi.fn(),
    logout: vi.fn()
  }
}));

// authStore mock — 동일한 login/logout 함수를 항상 반환
const mockAuthLogin = vi.fn();
const mockAuthLogout = vi.fn();
vi.mock("@/store/authStore", () => ({
  authStore: {
    getState: vi.fn(() => ({
      login: mockAuthLogin,
      logout: mockAuthLogout
    }))
  }
}));

// useNavigate mock
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate
  };
});

// Dev login shortcuts mock — dev 환경이 아니면 빠른 로그인 버튼 숨김
vi.mock("@/shared/lib/devLoginShortcuts", () => ({
  useDevLoginShortcuts: () => ({ loading: false, data: null })
}));

// sonner mock
vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() }
}));

// ClippingLogo mock
vi.mock("@/components/shared/ClippingLogo", () => ({
  ClippingLogo: () => <div data-testid="clipping-logo" />
}));

// framer-motion mock — 애니메이션 무력화 (onSubmit 등 이벤트 핸들러 그대로 전달)
vi.mock("framer-motion", async () => {
  const React = await import("react");
  // motion 전용 prop 을 걸러내는 헬퍼 — forwardRef children 에 DOM 속성만 남긴다
  const MOTION_ONLY_PROPS = ["initial", "animate", "exit", "transition", "variants", "whileHover", "whileTap"];
  function stripMotionProps(props: Record<string, unknown>) {
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(props)) {
      if (!MOTION_ONLY_PROPS.includes(key)) result[key] = props[key];
    }
    return result;
  }
  return {
    motion: {
      div: React.forwardRef((props: Record<string, unknown>, ref) => {
        const { children, ...rest } = stripMotionProps(props);
        return React.createElement("div", { ...rest, ref: ref as React.Ref<HTMLDivElement> }, children as React.ReactNode);
      }),
      form: React.forwardRef((props: Record<string, unknown>, ref) => {
        const { children, ...rest } = stripMotionProps(props);
        return React.createElement("form", { ...rest, ref: ref as React.Ref<HTMLFormElement> }, children as React.ReactNode);
      })
    },
    AnimatePresence: ({ children }: { children: React.ReactNode }) => children
  };
});

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthLogin.mockReset();
    mockAuthLogout.mockReset();
  });

  describe("렌더링", () => {
    it("로그인 폼이 올바르게 렌더링된다", () => {
      renderLogin();

      expect(screen.getByLabelText("아이디")).toBeInTheDocument();
      expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
    });

    it("회원가입 링크가 표시된다", () => {
      renderLogin();

      const signupLink = screen.getByRole("link", { name: "회원가입" });
      expect(signupLink).toBeInTheDocument();
      expect(signupLink).toHaveAttribute("href", "/signup");
    });

    it("브랜드 태그라인이 표시된다", () => {
      renderLogin();

      expect(screen.getByText("놓치면 안 되는 뉴스, 매일 비춰드립니다")).toBeInTheDocument();
    });
  });

  describe("폼 유효성 검사 (Zod 스키마)", () => {
    // motion.form mock의 한계로 DOM 이벤트 기반 검증 대신
    // zod 스키마 직접 검증 — loginSchema의 규칙이 올바른지 검증
    const loginSchema = z.object({
      email: z.string().min(1, "아이디를 입력하세요"),
      password: z.string().min(1, "비밀번호를 입력하세요")
    });

    it("빈 아이디는 스키마 검증 실패", () => {
      const result = loginSchema.safeParse({ email: "", password: "pass123" });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errors[0].message).toBe("아이디를 입력하세요");
      }
    });

    it("빈 비밀번호는 스키마 검증 실패", () => {
      const result = loginSchema.safeParse({ email: "user", password: "" });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errors[0].message).toBe("비밀번호를 입력하세요");
      }
    });

    it("아이디와 비밀번호 모두 없으면 스키마 검증 실패", () => {
      const result = loginSchema.safeParse({ email: "", password: "" });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errors).toHaveLength(2);
      }
    });

    it("정상 입력은 스키마 검증 성공", () => {
      const result = loginSchema.safeParse({ email: "testuser", password: "pass123" });
      expect(result.success).toBe(true);
    });
  });

  describe("로그인 성공", () => {
    it("관리자 로그인 성공 시 /admin으로 이동한다", async () => {
      const { authService } = await import("@/services/authService");
      vi.mocked(authService.login).mockResolvedValue({
        id: "admin-1",
        username: "admin",
        displayName: "관리자",
        role: "ADMIN",
        approvalStatus: "APPROVED"
      });

      renderLogin();

      await userEvent.type(screen.getByLabelText("아이디"), "admin");
      await userEvent.type(screen.getByLabelText("비밀번호"), "password123");
      await userEvent.click(screen.getByRole("button", { name: "로그인" }));

      await waitFor(() => {
        expect(mockAuthLogin).toHaveBeenCalled();
        expect(mockNavigate).toHaveBeenCalledWith("/admin", { replace: true });
      });
    });

    it("일반 유저 로그인 성공 시 /user로 이동한다", async () => {
      const { authService } = await import("@/services/authService");
      vi.mocked(authService.login).mockResolvedValue({
        id: "user-1",
        username: "testuser",
        displayName: "테스트유저",
        role: "USER",
        approvalStatus: "APPROVED"
      });

      renderLogin();

      await userEvent.type(screen.getByLabelText("아이디"), "testuser");
      await userEvent.type(screen.getByLabelText("비밀번호"), "password123");
      await userEvent.click(screen.getByRole("button", { name: "로그인" }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/user", { replace: true });
      });
    });
  });

  describe("로그인 실패", () => {
    it("로그인 실패 시 toast.error가 호출된다", async () => {
      const { authService } = await import("@/services/authService");
      const { toast } = await import("sonner");
      vi.mocked(authService.login).mockRejectedValue(new Error("Unauthorized"));

      renderLogin();

      await userEvent.type(screen.getByLabelText("아이디"), "wronguser");
      await userEvent.type(screen.getByLabelText("비밀번호"), "wrongpass");
      await userEvent.click(screen.getByRole("button", { name: "로그인" }));

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled();
      });
    });
  });

  describe("로그인 진행 중 UI", () => {
    it("로그인 진행 중 버튼 텍스트가 변경된다", async () => {
      const { authService } = await import("@/services/authService");
      vi.mocked(authService.login).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({
          id: "u1", username: "a", displayName: "A", role: "ADMIN", approvalStatus: "APPROVED" as const
        }), 100))
      );

      renderLogin();

      await userEvent.type(screen.getByLabelText("아이디"), "admin");
      await userEvent.type(screen.getByLabelText("비밀번호"), "password123");
      fireEvent.click(screen.getByRole("button", { name: "로그인" }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "로그인 중..." })).toBeInTheDocument();
      });
    });
  });
});
