import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { UserAccountApproval } from "@/types/user";

// ── 서비스 mock (vi.mock hoist 규칙에 따라 import 전에 선언) ────────────
vi.mock("@/services/userService", () => ({
  userService: {
    listAdminUserAccounts: vi.fn().mockResolvedValue([]),
    getUserAccountSummary: vi.fn().mockResolvedValue({
      pendingCount: 0,
      rejectedCount: 0,
      weeklyProcessedCount: 0,
    }),
    approveAdminUserAccount: vi.fn(),
    rejectAdminUserAccount: vi.fn(),
    bulkApproveAdminUserAccounts: vi.fn(),
    bulkRejectAdminUserAccounts: vi.fn(),
  },
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

import { ApprovalTab } from "../ApprovalTab";
import { userService } from "@/services/userService";

// ── 테스트 헬퍼 ────────────────────────────────────────────────────────

function makeApproval(overrides: Partial<UserAccountApproval> = {}): UserAccountApproval {
  return {
    id: overrides.id ?? "user-1",
    username: overrides.username ?? "alice@example.com",
    displayName: overrides.displayName ?? "Alice",
    department: overrides.department ?? "마케팅팀",
    isActive: overrides.isActive ?? false,
    approvalStatus: overrides.approvalStatus ?? "PENDING",
    approvalNote: overrides.approvalNote ?? null,
    approvedByUserId: overrides.approvedByUserId ?? null,
    approvedAt: overrides.approvedAt ?? null,
    createdAt: overrides.createdAt ?? new Date("2026-04-10T00:00:00Z").toISOString(),
    updatedAt: overrides.updatedAt ?? new Date("2026-04-10T00:00:00Z").toISOString(),
    lastLoginAt: overrides.lastLoginAt ?? null,
    role: overrides.role ?? "USER",
    approvedByUsername: overrides.approvedByUsername ?? null,
    ...overrides,
  };
}

function renderApprovalTab() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ApprovalTab />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userService.listAdminUserAccounts).mockResolvedValue([]);
  vi.mocked(userService.getUserAccountSummary).mockResolvedValue({
    pendingCount: 0,
    rejectedCount: 0,
    weeklyProcessedCount: 0,
  });
});

// ── 테스트 ────────────────────────────────────────────────────────────

describe("ApprovalTab — 초기 렌더", () => {
  it("PENDING 목록이 비어있을 때 빈 상태 안내 문구를 표시한다", async () => {
    vi.mocked(userService.listAdminUserAccounts).mockResolvedValue([]);

    renderApprovalTab();

    await waitFor(() => {
      expect(screen.getByText("모든 가입 신청을 처리했어요")).toBeInTheDocument();
    });
  });

  it("PENDING 신청 1건이 있으면 테이블에 신청자 이름과 부서가 표시된다", async () => {
    const pending = makeApproval({
      id: "user-1",
      displayName: "홍길동",
      department: "개발팀",
    });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();

    await waitFor(() => {
      expect(screen.getByText("홍길동")).toBeInTheDocument();
    });
    expect(screen.getByText("개발팀")).toBeInTheDocument();
  });
});

describe("ApprovalTab — 필터 및 검색", () => {
  it("기본 필터는 PENDING 이고 REJECTED 로 전환하면 반려 항목만 표시된다", async () => {
    const pending = makeApproval({ id: "p-1", displayName: "펜딩사용자" });
    const rejected = makeApproval({
      id: "r-1",
      displayName: "반려사용자",
      approvalStatus: "REJECTED",
      approvalNote: "사유 명확하지 않음",
      approvedByUsername: "admin",
      approvedAt: new Date("2026-04-10").toISOString(),
    });

    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      if (status === "REJECTED") return Promise.resolve([rejected]);
      return Promise.resolve([]);
    });

    renderApprovalTab();

    // 초기: pending 표시
    await waitFor(() => {
      expect(screen.getByText("펜딩사용자")).toBeInTheDocument();
    });

    // 필터 칩은 radiogroup 안의 radio 버튼 — "반려" 라디오를 선택한다
    const filterGroup = screen.getByRole("radiogroup", { name: /승인 상태 필터/ });
    const rejectedChip = within(filterGroup).getByRole("radio", { name: "반려" });
    expect(rejectedChip).toBeEnabled();
    fireEvent.click(rejectedChip);

    await waitFor(() => {
      expect(screen.getByText("반려사용자")).toBeInTheDocument();
    });
    // pending 은 더 이상 보이지 않는다
    expect(screen.queryByText("펜딩사용자")).not.toBeInTheDocument();
  });

  it("검색 쿼리가 없는 결과에서는 '결과가 없어요' 안내를 표시한다", async () => {
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING")
        return Promise.resolve([makeApproval({ id: "u-1", displayName: "홍길동" })]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    const searchInput = screen.getByPlaceholderText(/이름|검색/);
    fireEvent.change(searchInput, { target: { value: "존재하지않는이름xyz" } });

    await waitFor(() => {
      expect(screen.getByText(/'존재하지않는이름xyz'에 대한 결과가 없어요/)).toBeInTheDocument();
    });
  });
});

describe("ApprovalTab — 에러 상태", () => {
  it("listAdminUserAccounts 가 실패하면 재시도 버튼과 에러 문구를 표시한다", async () => {
    vi.mocked(userService.listAdminUserAccounts).mockRejectedValue(
      new Error("network error"),
    );

    renderApprovalTab();

    await waitFor(() => {
      expect(screen.getByText("데이터를 불러오지 못했어요")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});

describe("ApprovalTab — 승인/반려 액션", () => {
  it("PENDING 항목의 '승인' 버튼을 클릭하면 승인 다이얼로그가 열린다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    // 테이블 row 범위로 승인 버튼을 찾는다 (요약 카드 "승인 대기"와 혼동 방지)
    const nameCell = screen.getByText("홍길동");
    const row = nameCell.closest("tr");
    expect(row).toBeTruthy();
    const approveBtn = within(row as HTMLElement).getByRole("button", { name: /승인/ });
    fireEvent.click(approveBtn);

    // 다이얼로그 열림 — role="dialog" 및 그 안의 제목을 확인
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/회원가입 승인/)).toBeInTheDocument();
  });

  it("반려 다이얼로그에서 사유 없이 제출하면 에러 메시지를 표시하고 API 를 호출하지 않는다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    const nameCell = screen.getByText("홍길동");
    const row = nameCell.closest("tr");
    expect(row).toBeTruthy();
    const rejectBtn = within(row as HTMLElement).getByRole("button", { name: /반려/ });
    fireEvent.click(rejectBtn);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/회원가입 반려/)).toBeInTheDocument();

    // 사유 입력 없이 다이얼로그 하단의 반려 제출 버튼 클릭
    const submitBtn = within(dialog).getByRole("button", { name: /반려/ });
    fireEvent.click(submitBtn);

    // 에러 메시지 표시
    await waitFor(() => {
      expect(within(dialog).getByText(/반려 사유를 입력해주세요/)).toBeInTheDocument();
    });
    // API 는 호출되지 않음
    expect(userService.rejectAdminUserAccount).not.toHaveBeenCalled();
  });
});

describe("ApprovalTab — 체크박스 선택", () => {
  it("체크박스로 개별 항목을 선택하면 일괄 처리 바가 표시된다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    // 항목 체크박스(헤더 체크박스 제외) 클릭
    const checkboxes = screen.getAllByRole("checkbox");
    // 첫 번째는 전체선택(헤더), 두 번째부터 개별 행
    expect(checkboxes.length).toBeGreaterThanOrEqual(2);
    fireEvent.click(checkboxes[1]);

    // 선택된 건수 표시(BulkActionBar) — "1건" 또는 "선택" 등 문구 포함
    await waitFor(() => {
      const bulkHints = screen.queryAllByText(/1건|선택/);
      expect(bulkHints.length).toBeGreaterThan(0);
    });
  });
});

describe("ApprovalTab — 접근성", () => {
  it("PENDING 테이블은 role='table' + aria-label 로 스크린리더가 접근할 수 있다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    // role="table" + aria-label 로 접근 가능
    const table = screen.getByRole("table", { name: /가입 승인 대기 회원 목록/ });
    expect(table).toBeInTheDocument();
  });

  it("각 행의 승인/반려 버튼은 신청자 이름이 포함된 aria-label 을 가진다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    // 승인/반려 버튼은 신청자 이름을 포함한 aria-label 로 구분 가능
    expect(
      screen.getByRole("button", { name: "홍길동 승인" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "홍길동 반려" }),
    ).toBeInTheDocument();
  });

  it("필터 칩은 radiogroup 시맨틱을 가져 현재 선택 상태를 aria-checked 로 노출한다", async () => {
    vi.mocked(userService.listAdminUserAccounts).mockResolvedValue([]);
    renderApprovalTab();

    const filterGroup = await screen.findByRole("radiogroup", {
      name: /승인 상태 필터/,
    });
    const pendingChip = within(filterGroup).getByRole("radio", {
      name: "승인 대기",
    });
    const rejectedChip = within(filterGroup).getByRole("radio", {
      name: "반려",
    });

    // 초기 상태: PENDING 이 checked
    expect(pendingChip).toHaveAttribute("aria-checked", "true");
    expect(rejectedChip).toHaveAttribute("aria-checked", "false");
  });

  it("행 체크박스는 신청자 이름이 포함된 aria-label 을 가진다", async () => {
    const pending = makeApproval({ id: "u-1", displayName: "홍길동" });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "PENDING") return Promise.resolve([pending]);
      return Promise.resolve([]);
    });

    renderApprovalTab();
    await waitFor(() => screen.getByText("홍길동"));

    // 행 체크박스 aria-label
    expect(
      screen.getByRole("checkbox", { name: "홍길동 선택" }),
    ).toBeInTheDocument();
  });

  it("REJECTED 테이블로 전환하면 재승인 버튼이 aria-label 로 노출된다", async () => {
    const rejected = makeApproval({
      id: "r-1",
      displayName: "반려사용자",
      approvalStatus: "REJECTED",
      approvalNote: "사유 명확하지 않음",
      approvedByUsername: "admin",
      approvedAt: new Date("2026-04-10").toISOString(),
    });
    vi.mocked(userService.listAdminUserAccounts).mockImplementation((status?: string) => {
      if (status === "REJECTED") return Promise.resolve([rejected]);
      return Promise.resolve([]);
    });

    renderApprovalTab();

    // REJECTED 필터 전환
    const filterGroup = await screen.findByRole("radiogroup", {
      name: /승인 상태 필터/,
    });
    fireEvent.click(within(filterGroup).getByRole("radio", { name: "반려" }));

    await waitFor(() => screen.getByText("반려사용자"));

    // 반려 테이블은 role="table" + 다른 aria-label 을 가진다
    expect(
      screen.getByRole("table", { name: /반려된 가입 신청 목록/ }),
    ).toBeInTheDocument();
    // 재승인 버튼은 신청자 이름이 포함된 aria-label 을 가진다
    expect(
      screen.getByRole("button", { name: "반려사용자 재승인" }),
    ).toBeInTheDocument();
  });
});
