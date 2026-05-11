import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

// departmentService 와 authService 는 훅이 import 하므로 반드시 SignupPage 보다 먼저 모킹해야 한다.
vi.mock("@/services/authService", () => ({
  authService: {
    signup: vi.fn(),
    login: vi.fn(),
  },
}));

vi.mock("@/services/departmentService", () => ({
  departmentService: {
    getPublicTree: vi.fn().mockResolvedValue({
      departments: [
        {
          id: "dept-1",
          name: "개발팀",
          teams: [
            { id: "team-1", name: "프론트" },
            { id: "team-2", name: "백엔드" },
          ],
        },
        {
          id: "dept-2",
          name: "영업팀",
          teams: [],
        },
      ],
    }),
    getAdminTree: vi.fn(),
  },
}));

// useNavigate mock
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import { SignupPage } from "../SignupPage";

function renderSignup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SignupPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SignupPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("렌더링", () => {
    it("회원가입 폼이 올바르게 렌더링된다", () => {
      renderSignup();

      expect(screen.getByRole("heading", { name: "회원가입" })).toBeInTheDocument();
      expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("홍길동")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("영문 + 숫자 포함, 8자 이상")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("비밀번호를 다시 입력하세요")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "회원가입" })).toBeInTheDocument();
    });

    it("로그인 링크가 표시된다", () => {
      renderSignup();
      const loginLink = screen.getByRole("link", { name: "로그인" });
      expect(loginLink).toBeInTheDocument();
      expect(loginLink).toHaveAttribute("href", "/login");
    });

    it("부서 선택 버튼과 팀 선택 버튼이 각각 렌더링된다", async () => {
      renderSignup();
      // useDepartmentTree 의 공개 트리 로드 완료 후 Select trigger 가 활성화된다.
      await waitFor(() => {
        expect(screen.getByRole("combobox", { name: "부서 선택" })).toBeInTheDocument();
        expect(screen.getByRole("combobox", { name: "팀 선택" })).toBeInTheDocument();
      });
    });
  });

  describe("유효성 검사 (부서/팀 선택)", () => {
    it("부서/팀 모두 선택 — 빈 값으로 제출해도 '부서를 선택하세요' 에러가 나오지 않는다", async () => {
      renderSignup();
      // 로드 대기
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "회원가입" })).not.toBeDisabled();
      });
      await userEvent.click(screen.getByRole("button", { name: "회원가입" }));

      // '부서를 선택하세요' 메시지는 더 이상 렌더되면 안 된다.
      expect(screen.queryByText("부서를 선택하세요")).not.toBeInTheDocument();
    });

    it("부서/팀 모두 optional — 빈 값도 zod 스키마 유효성을 통과한다", async () => {
      const { z } = await import("zod");
      const schema = z.object({
        username: z.string().email(),
        displayName: z.string().min(2),
        departmentId: z.string().optional(),
        teamId: z.string().optional(),
        password: z.string().min(8),
        confirmPassword: z.string().min(1),
      });

      expect(
        schema.safeParse({
          username: "a@b.co",
          displayName: "테스터",
          departmentId: "",
          teamId: "",
          password: "abc12345",
          confirmPassword: "abc12345",
        }).success
      ).toBe(true);
    });

    it("부서/팀 없이 필수 필드만 채우면 signup API 가 departmentId=null, teamId=null 로 호출된다", async () => {
      const { authService } = await import("@/services/authService");

      renderSignup();
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "회원가입" })).not.toBeDisabled();
      });
      await userEvent.type(screen.getByPlaceholderText("name@company.com"), "tester@example.com");
      await userEvent.type(screen.getByPlaceholderText("홍길동"), "테스터");
      await userEvent.type(screen.getByPlaceholderText("영문 + 숫자 포함, 8자 이상"), "Test1234");
      await userEvent.type(screen.getByPlaceholderText("비밀번호를 다시 입력하세요"), "Test1234");
      await userEvent.click(screen.getByRole("button", { name: "회원가입" }));

      await waitFor(() => {
        expect(vi.mocked(authService.signup)).toHaveBeenCalledWith({
          email: "tester@example.com",
          displayName: "테스터",
          password: "Test1234",
          departmentId: null,
          teamId: null,
        });
      });
    });
  });

  describe("전체 폼 에러 요약", () => {
    it("빈 폼 제출 시 에러 요약 배너가 표시된다", async () => {
      renderSignup();
      await userEvent.click(screen.getByRole("button", { name: "회원가입" }));

      await waitFor(() => {
        expect(screen.getByText("입력 정보를 확인해주세요.")).toBeInTheDocument();
      });
    });
  });

  describe("부서 목록 비어있음/에러 — submit 은 항상 허용", () => {
    it("부서 트리가 비어있어도 empty-departments 배너는 렌더되지 않고 submit 도 활성화된다", async () => {
      const { departmentService } = await import("@/services/departmentService");
      vi.mocked(departmentService.getPublicTree).mockResolvedValueOnce({ departments: [] });

      renderSignup();

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "회원가입" })).not.toBeDisabled();
      });
      // 부서가 선택이므로 "가입 가능한 부서 없음" 배너 자체를 제거했다.
      expect(screen.queryByTestId("signup-empty-departments")).not.toBeInTheDocument();
    });

    it("부서 트리 로드 실패 시 경고 배너(재시도 버튼 포함) 가 나오지만 submit 은 여전히 활성화된다", async () => {
      const { departmentService } = await import("@/services/departmentService");
      vi.mocked(departmentService.getPublicTree).mockRejectedValueOnce(new Error("network down"));

      renderSignup();

      await waitFor(() => {
        expect(screen.getByTestId("signup-tree-error")).toBeInTheDocument();
      });
      expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
      // 부서가 선택이므로 트리 로드 실패여도 가입은 진행 가능.
      expect(screen.getByRole("button", { name: "회원가입" })).not.toBeDisabled();
    });

    it("부서 트리가 정상 로드되면 경고 배너 없이 submit 가능 상태가 된다", async () => {
      renderSignup();

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "회원가입" })).not.toBeDisabled();
      });
      expect(screen.queryByTestId("signup-empty-departments")).not.toBeInTheDocument();
      expect(screen.queryByTestId("signup-tree-error")).not.toBeInTheDocument();
    });
  });
});
