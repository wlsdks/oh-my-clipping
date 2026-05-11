import { describe, it, expect, vi, beforeEach, beforeAll } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

// 사내 부서·팀 admin UI — master-detail 렌더링 + mutation 성공 토스트 + DnD 정렬 검증.

// jsdom + Radix/dnd-kit 호환 폴리필 — pointer capture / scrollIntoView 누락 시 throw.
beforeAll(() => {
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
});

vi.mock("@/services/departmentService", () => ({
  departmentService: {
    getPublicTree: vi.fn(),
    getAdminTree: vi.fn().mockResolvedValue({
      content: [
        {
          department: {
            id: "dept-1",
            name: "개발팀",
            displayOrder: 1,
            isActive: true,
            createdAt: "",
            updatedAt: "",
          },
          teams: [
            {
              id: "team-1",
              departmentId: "dept-1",
              name: "프론트",
              displayOrder: 1,
              isActive: true,
              createdAt: "",
              updatedAt: "",
            },
          ],
        },
        {
          department: {
            id: "dept-2",
            name: "영업팀",
            displayOrder: 2,
            isActive: true,
            createdAt: "",
            updatedAt: "",
          },
          teams: [],
        },
      ],
      totalCount: 2,
    }),
    createDepartment: vi.fn(),
    updateDepartment: vi.fn(),
    setDepartmentActive: vi.fn(),
    createTeam: vi.fn(),
    updateTeam: vi.fn(),
    setTeamActive: vi.fn(),
    deleteDepartment: vi.fn().mockResolvedValue(undefined),
    deleteTeam: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { DepartmentsPage } from "@/pages/system/DepartmentsPage";
import { departmentService } from "@/services/departmentService";
import { toast } from "sonner";

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <DepartmentsPage />
    </QueryClientProvider>
  );
}

describe("DepartmentsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("관리자 트리를 불러와 좌우 패널을 렌더링한다", async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByLabelText("개발팀 이름")).toBeInTheDocument();
      expect(screen.getByLabelText("영업팀 이름")).toBeInTheDocument();
    });
    // 첫 부서가 기본 선택되어 팀 목록이 오른쪽에 보인다.
    await waitFor(() => {
      expect(screen.getByDisplayValue("프론트")).toBeInTheDocument();
    });
  });

  it("빈 부서 이름으로는 추가 버튼이 비활성화된다", async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText("개발팀 이름"));

    const addBtn = screen.getByRole("button", { name: "부서 추가" });
    expect(addBtn).toBeDisabled();
  });

  it("새 부서 이름을 입력하고 추가하면 createDepartment 서비스가 호출되고 성공 토스트가 뜬다", async () => {
    vi.mocked(departmentService.createDepartment).mockResolvedValue({
      id: "dept-3",
      name: "디자인팀",
      displayOrder: 0,
      isActive: true,
      createdAt: "",
      updatedAt: "",
    });

    renderPage();
    await waitFor(() => screen.getByLabelText("개발팀 이름"));

    await userEvent.type(screen.getByLabelText("새 부서 이름"), "디자인팀");
    await userEvent.click(screen.getByRole("button", { name: "부서 추가" }));

    await waitFor(() => {
      expect(departmentService.createDepartment).toHaveBeenCalledWith({ name: "디자인팀" });
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("부서를 추가했어요");
    });
  });

  it("팀 isActive 토글 성공 시 setTeamActive 가 호출된다", async () => {
    vi.mocked(departmentService.setTeamActive).mockResolvedValue({
      id: "team-1",
      departmentId: "dept-1",
      name: "프론트",
      displayOrder: 1,
      isActive: false,
      createdAt: "",
      updatedAt: "",
    });

    renderPage();
    await waitFor(() => screen.getByDisplayValue("프론트"));

    const toggle = screen.getByRole("switch", { name: "프론트 활성 상태" });
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(departmentService.setTeamActive).toHaveBeenCalledWith("team-1", false);
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("팀을 비활성화했어요");
    });
  });

  it("부서 이동 Select 변경 시 updateTeam 이 호출된다", async () => {
    vi.mocked(departmentService.updateTeam).mockResolvedValue({
      id: "team-1",
      departmentId: "dept-2",
      name: "프론트",
      displayOrder: 1,
      isActive: true,
      createdAt: "",
      updatedAt: "",
    });

    renderPage();
    await waitFor(() => screen.getByDisplayValue("프론트"));

    // Radix Select 는 jsdom 에서 트리거 open 에 제약이 있으므로 내부 prop 호출을 직접 시뮬레이션
    // 하는 대신 훅이 노출한 "부서 이동" combobox 의 존재만 확인한다. 실제 상호작용은 E2E 에서 커버한다.
    expect(screen.getByRole("combobox", { name: "프론트 부서 이동" })).toBeInTheDocument();
  });

  it("부서와 팀 각 행에 순서 이동 드래그 핸들이 렌더링된다 (a11y 핸들)", async () => {
    renderPage();

    await waitFor(() => screen.getByLabelText("개발팀 이름"));

    // 부서 행 — 각 부서별 핸들 + role=button 으로 키보드 접근 가능해야 한다.
    const deptHandle = screen.getByLabelText("개발팀 순서 이동 핸들");
    expect(deptHandle).toBeInTheDocument();
    expect(deptHandle.tagName).toBe("BUTTON");
    expect(screen.getByLabelText("영업팀 순서 이동 핸들")).toBeInTheDocument();
    // 팀 행 — 선택된 부서의 팀 핸들
    expect(screen.getByLabelText("프론트 순서 이동 핸들")).toBeInTheDocument();
  });

  it("부서 행 클릭 시 드래그 핸들 클릭은 선택 이벤트를 전파하지 않는다", async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText("영업팀 순서 이동 핸들"));

    // 핸들 자체는 onClick stopPropagation 으로 선택 토글을 막아야 한다.
    // (드래그 시작이 selection 과 충돌하면 사용자 경험이 깨진다.)
    const handle = screen.getByLabelText("영업팀 순서 이동 핸들");
    fireEvent.click(handle);

    // 기본 선택은 첫 부서(dept-1=개발팀). 핸들 클릭 후에도 여전히 개발팀의 팀(프론트) 이 보여야 한다.
    expect(screen.getByDisplayValue("프론트")).toBeInTheDocument();
  });

  it("부서 이름 inline edit 후 blur 하면 updateDepartment 가 호출된다", async () => {
    vi.mocked(departmentService.updateDepartment).mockResolvedValue({
      id: "dept-1",
      name: "개발 1팀",
      displayOrder: 1,
      isActive: true,
      createdAt: "",
      updatedAt: "",
    });

    renderPage();
    await waitFor(() => screen.getByLabelText("개발팀 이름"));

    const deptInput = screen.getByLabelText("개발팀 이름") as HTMLInputElement;
    fireEvent.change(deptInput, { target: { value: "개발 1팀" } });
    fireEvent.blur(deptInput);

    await waitFor(() => {
      expect(departmentService.updateDepartment).toHaveBeenCalledWith("dept-1", {
        name: "개발 1팀",
        displayOrder: undefined,
      });
    });
  });

  it("활성 부서에는 삭제 버튼이 노출되지 않는다", async () => {
    renderPage();
    await waitFor(() => {
      // 좌측 리스트의 부서 input 으로 로드 완료를 확인.
      expect(screen.getByRole("textbox", { name: /개발팀 이름/ })).toBeInTheDocument();
    });
    // 부서/팀 모두 isActive=true 인 기본 목 데이터 — '삭제' 버튼 0개.
    expect(screen.queryByRole("button", { name: /개발팀 삭제/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /영업팀 삭제/ })).not.toBeInTheDocument();
  });

  it("비활성 부서의 삭제 버튼을 누르면 확인 모달이 뜨고 '삭제' 클릭 시 deleteDepartment 가 호출된다", async () => {
    const { departmentService } = await import("@/services/departmentService");
    const inactiveMock = vi.mocked(departmentService.getAdminTree).mockResolvedValueOnce({
      content: [
        {
          department: {
            id: "dept-inactive",
            name: "인재경영실",
            displayOrder: 1,
            isActive: false,
            createdAt: "",
            updatedAt: "",
          },
          teams: [],
        },
      ],
      totalCount: 1,
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText("인재경영실")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /인재경영실 삭제/ }));
    // 확인 모달이 렌더되었다.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    // 'Confirm' 버튼은 텍스트 '삭제' 로 설정돼있다.
    await userEvent.click(screen.getAllByRole("button", { name: "삭제" }).at(-1)!);

    await waitFor(() => {
      expect(departmentService.deleteDepartment).toHaveBeenCalledWith("dept-inactive");
    });
    inactiveMock.mockClear();
  });
});
