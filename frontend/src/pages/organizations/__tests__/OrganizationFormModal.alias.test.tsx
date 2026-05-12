import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrganizationFormModal } from "../OrganizationFormModal";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { Organization } from "@/types/organization";

// 서비스 mock — updateMock 을 통해 전달 인자를 검증한다.
const updateMock = vi.fn();
const createMock = vi.fn();

vi.mock("@/services/organizationService", () => ({
  organizationService: {
    list: vi.fn(),
    delete: vi.fn(),
    create: (...args: unknown[]) => createMock(...args),
    update: (...args: unknown[]) => updateMock(...args),
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
    type: "CUSTOMER",
    domain: "acme.com",
    description: null,
    stockCode: null,
    aliases: [],
    origin: null,
    usageCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderModal(organization?: Organization | null, onClose = vi.fn()) {
  return render(
    <OrganizationFormModal open onClose={onClose} organization={organization} />,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("OrganizationFormModal — aliases", () => {
  beforeEach(() => {
    updateMock.mockReset();
    createMock.mockReset();
    // 성공 응답 기본 세팅
    updateMock.mockResolvedValue(makeOrg());
    createMock.mockResolvedValue(makeOrg());
  });

  it("편집 모드에서 기존 aliases 가 Textarea 에 라인별로 표시된다", async () => {
    renderModal(makeOrg({ aliases: ["SEC", "samsung"] }));
    const textarea = screen.getByLabelText(/별칭/);
    expect(textarea).toHaveValue("SEC\nsamsung");
  });

  it("저장 시 aliases 배열이 update 에 전달된다 (trim + dedup + 빈줄 제거)", async () => {
    const user = userEvent.setup();
    renderModal(makeOrg({ id: "o1", aliases: [] }));
    const textarea = screen.getByLabelText(/별칭/);
    await user.clear(textarea);
    await user.type(textarea, "SEC\n\nSEC\n  samsung  \nMegaCorp");
    await user.click(screen.getByRole("button", { name: /저장/ }));
    await waitFor(() => expect(updateMock).toHaveBeenCalledTimes(1));
    const body = updateMock.mock.calls[0][1];
    expect(body.aliases).toEqual(["SEC", "samsung", "MegaCorp"]);
  });

  it("21개 초과 시 validation 에러 노출 + 저장 버튼 disabled", async () => {
    const user = userEvent.setup();
    renderModal(makeOrg({ id: "o1", aliases: [] }));
    const textarea = screen.getByLabelText(/별칭/);
    await user.clear(textarea);
    // 21개 이상의 고유한 라인
    await user.type(textarea, Array.from({ length: 21 }, (_, i) => `alias${i}`).join("\n"));
    expect(await screen.findByText(/별칭.*최대 20/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /저장/ })).toBeDisabled();
  });

  it("단일 별칭 50자 초과 시 validation 에러", async () => {
    const user = userEvent.setup();
    renderModal(makeOrg({ id: "o1", aliases: [] }));
    const textarea = screen.getByLabelText(/별칭/);
    await user.clear(textarea);
    await user.type(textarea, "a".repeat(51));
    expect(await screen.findByText(/별칭.*최대 50자/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /저장/ })).toBeDisabled();
  });

  it("생성 모드에서는 alias Textarea 가 표시되지 않는다", () => {
    renderModal(null);
    expect(screen.queryByLabelText(/별칭/)).not.toBeInTheDocument();
  });
});
