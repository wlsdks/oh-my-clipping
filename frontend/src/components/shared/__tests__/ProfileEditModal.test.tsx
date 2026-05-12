import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { createQueryClientWrapper } from "@/test/queryClient";

// V129 ProfileEditModal — 부서/팀 FK cascade 편집 플로우 검증.

vi.mock("@/services/userService", () => ({
  userService: { updateProfile: vi.fn() },
}));

vi.mock("@/services/departmentService", () => ({
  departmentService: {
    getPublicTree: vi.fn().mockResolvedValue({
      departments: [
        {
          id: "dept-1",
          name: "개발팀",
          teams: [{ id: "team-1", name: "프론트" }],
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

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { ProfileEditModal } from "@/components/shared/ProfileEditModal";
import { userService } from "@/services/userService";

function renderWithQc(ui: ReactElement) {
  return render(ui, { wrapper: createQueryClientWrapper() });
}

describe("ProfileEditModal (V129 cascade)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("모달이 열리면 부서/팀 셀렉트가 렌더링된다", async () => {
    renderWithQc(
      <ProfileEditModal
        open
        initialDepartmentId={null}
        initialTeamId={null}
        onClose={vi.fn()}
      />
    );

    expect(screen.getByText("프로필 편집")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "부서 선택" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "팀 선택" })).toBeInTheDocument();
  });

  it("레거시 부서명만 있고 FK 가 없으면 경고 밴드가 표시된다", () => {
    renderWithQc(
      <ProfileEditModal
        open
        initialDepartmentId={null}
        initialTeamId={null}
        initialDepartment="영업팀"
        initialTeam="솔루션 1팀"
        onClose={vi.fn()}
      />
    );

    const alerts = screen.getAllByRole("alert");
    const legacyAlert = alerts.find((el) => el.textContent?.includes("레거시 부서"));
    // 단순 존재 여부가 아니라 사용자에게 보이는 상태를 단언한다 (DOM 연결 + 레거시 부서명 포함)
    expect(legacyAlert).toBeInstanceOf(HTMLElement);
    expect(legacyAlert).toBeInTheDocument();
    expect(legacyAlert?.textContent).toContain("영업팀");
  });

  it("FK 초기값이 있으면 레거시 경고 밴드는 표시되지 않는다", () => {
    renderWithQc(
      <ProfileEditModal
        open
        initialDepartmentId="dept-1"
        initialTeamId="team-1"
        initialDepartment="개발팀"
        initialTeam="프론트"
        onClose={vi.fn()}
      />
    );

    const alerts = screen.queryAllByRole("alert");
    const legacyAlert = alerts.find((el) => el.textContent?.includes("레거시 부서"));
    expect(legacyAlert).toBeUndefined();
  });

  it("초기 dirty 상태가 아니고 레거시 경고도 없으면 저장 버튼은 비활성화된다", async () => {
    renderWithQc(
      <ProfileEditModal
        open
        initialDepartmentId="dept-1"
        initialTeamId="team-1"
        onClose={vi.fn()}
      />
    );

    const submit = screen.getByRole("button", { name: "저장" });
    expect(submit).toBeDisabled();
  });

  it("열기 전 상태에서는 updateProfile 이 호출되지 않는다", () => {
    renderWithQc(
      <ProfileEditModal
        open={false}
        initialDepartmentId={null}
        initialTeamId={null}
        onClose={vi.fn()}
      />
    );

    expect(userService.updateProfile).not.toHaveBeenCalled();
  });
});
