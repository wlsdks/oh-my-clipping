import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { OrganizationsPage } from "../OrganizationsPage";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { Organization, OrganizationListResponse } from "@/types/organization";

// 서비스 mock — 테스트별로 구현체를 갈아끼운다.
const listMock = vi.fn();
const deleteMock = vi.fn();
const createMock = vi.fn();

vi.mock("@/services/organizationService", () => ({
  organizationService: {
    list: (...args: unknown[]) => listMock(...args),
    delete: (...args: unknown[]) => deleteMock(...args),
    create: (...args: unknown[]) => createMock(...args),
    update: vi.fn(),
    getById: vi.fn(),
    listByCategoryId: vi.fn(),
    setCategoryOrganizations: vi.fn(),
  },
}));

function makeOrg(overrides: Partial<Organization> = {}): Organization {
  return {
    id: "org-1",
    tenantId: "default",
    name: "Acme",
    type: "COMPETITOR",
    domain: "acme.com",
    description: "주요 경쟁사",
    stockCode: null,
    aliases: [],
    origin: null,
    usageCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <OrganizationsPage />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("OrganizationsPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    deleteMock.mockReset();
    createMock.mockReset();
  });

  it("조직이 없으면 빈 상태 안내를 표시한다", async () => {
    listMock.mockResolvedValueOnce({ content: [], totalCount: 0 } as OrganizationListResponse);

    renderPage();

    expect(await screen.findByText(/아직 등록된 조직이 없어요/)).toBeInTheDocument();
  });

  it("조회된 조직을 테이블에 표시한다", async () => {
    listMock.mockResolvedValueOnce({
      content: [
        // 둘 다 비-COMPETITOR 로 두어 기존 편집/삭제 경로가 그대로 동작하는지 검증한다.
        // (COMPETITOR 읽기 전용 경로는 별도 테스트에서 검증.)
        makeOrg({ type: "CUSTOMER" }),
        makeOrg({ id: "org-2", name: "Beta Co", type: "PARTNER" }),
      ],
      totalCount: 2,
    } as OrganizationListResponse);

    renderPage();

    expect(await screen.findByText("Acme")).toBeInTheDocument();
    expect(screen.getByText("Beta Co")).toBeInTheDocument();
    // 액션 버튼 aria-label 로 두 조직이 모두 렌더링됐는지 교차 검증.
    expect(screen.getByRole("button", { name: "Acme 편집" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Beta Co 편집" })).toBeInTheDocument();
  });

  it("필터 칩을 클릭하면 해당 타입으로 재조회한다", async () => {
    const user = userEvent.setup();
    // 초기 "ALL"
    listMock.mockResolvedValueOnce({ content: [], totalCount: 0 });
    // 클릭 후 "COMPETITOR"
    listMock.mockResolvedValueOnce({
      content: [makeOrg()],
      totalCount: 1,
    });

    renderPage();

    await waitFor(() => expect(listMock).toHaveBeenCalledWith(undefined));

    // 필터 칩은 true 탭이 아니라 토글 버튼 → aria-pressed 토글 버튼으로 조회.
    const competitorChip = screen.getByRole("button", { name: "경쟁사" });
    expect(competitorChip).toHaveAttribute("aria-pressed", "false");
    await user.click(competitorChip);

    await waitFor(() => expect(listMock).toHaveBeenCalledWith("COMPETITOR"));
    expect(screen.getByRole("button", { name: "경쟁사" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("API 실패 시 다시 시도 버튼을 보여준다", async () => {
    listMock.mockRejectedValueOnce(new Error("network fail"));

    renderPage();

    expect(await screen.findByText(/조직 목록을 불러오지 못했어요/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("삭제 버튼을 누르면 확인 모달이 열린다", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValueOnce({
      content: [makeOrg({ type: "PARTNER", name: "Beta Co" })],
      totalCount: 1,
    });

    renderPage();

    await screen.findByText("Beta Co");
    await user.click(screen.getByRole("button", { name: "Beta Co 삭제" }));

    expect(await screen.findByText(/조직을 삭제할까요\?/)).toBeInTheDocument();
  });

  it("COMPETITOR 타입 행은 편집/삭제 버튼 대신 '경쟁사 관리에서 편집' 링크를 보여준다", async () => {
    listMock.mockResolvedValueOnce({
      content: [makeOrg({ type: "COMPETITOR", name: "Acme" })],
      totalCount: 1,
    });

    renderPage();

    await screen.findByText("Acme");

    // 경쟁사는 읽기 전용 — 편집/삭제 버튼이 없어야 한다.
    expect(screen.queryByRole("button", { name: "Acme 편집" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Acme 삭제" })).not.toBeInTheDocument();

    // 대신 경쟁사 관리로 이동하는 링크가 보여야 한다.
    const link = screen.getByRole("link", { name: /Acme 편집은 경쟁사 관리에서 진행/ });
    expect(link).toHaveAttribute("href", "/admin/competitors");
  });

  it("비-COMPETITOR 타입 행은 편집/삭제 버튼을 그대로 제공한다", async () => {
    listMock.mockResolvedValueOnce({
      content: [makeOrg({ type: "PARTNER", name: "Beta Co" })],
      totalCount: 1,
    });

    renderPage();

    await screen.findByText("Beta Co");

    expect(screen.getByRole("button", { name: "Beta Co 편집" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Beta Co 삭제" })).toBeInTheDocument();
  });
});

describe("OrganizationsPage — 탭 + 확장 컬럼 + 삭제 가드", () => {
  beforeEach(() => {
    listMock.mockReset();
    deleteMock.mockReset();
    createMock.mockReset();
  });

  it("URL ?tab=backfill 이면 Backfill 탭 활성", async () => {
    listMock.mockResolvedValueOnce({ content: [], totalCount: 0 });
    render(<OrganizationsPage />, { wrapper: withSearchParams("?tab=backfill") });
    expect(await screen.findByRole("tab", { name: /기존 구독 가져오기/, selected: true })).toBeInTheDocument();
  });

  it("리스트 탭에 origin + usageCount 컬럼 노출", async () => {
    listMock.mockResolvedValueOnce({ content: [makeOrg({ origin: "user_wizard", usageCount: 3 })], totalCount: 1 });
    render(<OrganizationsPage />, { wrapper: withSearchParams("") });
    expect(await screen.findByText("유저 신청")).toBeInTheDocument();
    expect(screen.getByText("3개 카테고리")).toBeInTheDocument();
  });

  it("usageCount === 0 이면 일반 삭제 Confirm", async () => {
    listMock.mockResolvedValueOnce({ content: [makeOrg({ id: "o1", name: "Free", type: "CUSTOMER", usageCount: 0 })], totalCount: 1 });
    render(<OrganizationsPage />, { wrapper: withSearchParams("") });
    const deleteBtn = await screen.findByRole("button", { name: /Free 삭제/ });
    await userEvent.click(deleteBtn);
    expect(screen.getByText(/연결된 카테고리의 조직 링크도 함께 제거/)).toBeInTheDocument();
  });

  it("usageCount > 0 이면 강제 삭제 경고 Confirm", async () => {
    listMock.mockResolvedValueOnce({ content: [makeOrg({ id: "o2", name: "Used", type: "CUSTOMER", usageCount: 4 })], totalCount: 1 });
    render(<OrganizationsPage />, { wrapper: withSearchParams("") });
    const deleteBtn = await screen.findByRole("button", { name: /Used 삭제/ });
    await userEvent.click(deleteBtn);
    // 강화된 경고문: "4개 카테고리에서 사용 중" + "강제 삭제" 키워드
    expect(screen.getByText(/4개 카테고리에서 사용 중/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /강제 삭제/ })).toBeInTheDocument();
  });
});

// helper
function withSearchParams(search: string) {
  const QueryClientWrapper = createQueryClientWrapper();
  return ({ children }: { children: ReactNode }) => (
    <QueryClientWrapper>
      <MemoryRouter initialEntries={[{ pathname: "/admin/organizations", search }]}>
        {children}
      </MemoryRouter>
    </QueryClientWrapper>
  );
}
