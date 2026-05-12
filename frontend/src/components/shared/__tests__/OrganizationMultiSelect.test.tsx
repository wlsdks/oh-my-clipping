import { useState } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrganizationMultiSelect } from "../OrganizationMultiSelect";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { Organization, OrganizationListResponse } from "@/types/organization";

const listMock = vi.fn();
vi.mock("@/services/organizationService", () => ({
  organizationService: {
    list: (...args: unknown[]) => listMock(...args),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
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
    domain: null,
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

interface HarnessProps {
  initialValue?: string[];
}

function Harness({ initialValue = [] }: HarnessProps) {
  const [v, setV] = useState<string[]>(initialValue);
  return <OrganizationMultiSelect value={v} onChange={setV} />;
}

function renderSelect(initialValue: string[] = []) {
  return render(<Harness initialValue={initialValue} />, {
    wrapper: createQueryClientWrapper(),
  });
}

describe("OrganizationMultiSelect", () => {
  beforeEach(() => {
    listMock.mockReset();
  });

  it("선택된 항목이 없으면 placeholder 를 보여준다", async () => {
    listMock.mockResolvedValue({ content: [], totalCount: 0 } as OrganizationListResponse);
    renderSelect([]);

    expect(screen.getByText("기업을 선택하세요")).toBeInTheDocument();
  });

  it("선택된 id 에 대한 pill 이 표시된다", async () => {
    listMock.mockResolvedValue({
      content: [makeOrg(), makeOrg({ id: "org-2", name: "Beta Co" })],
      totalCount: 2,
    });

    renderSelect(["org-2"]);

    // 쿼리 완료 대기
    await waitFor(() => {
      expect(screen.getByText("Beta Co")).toBeInTheDocument();
    });
  });

  it("popover 를 열고 항목을 클릭하면 선택 토글이 된다", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue({
      content: [makeOrg({ id: "org-1", name: "Alpha Co" })],
      totalCount: 1,
    });

    renderSelect([]);

    await user.click(screen.getByRole("button", { name: /기업을 선택하세요/ }));

    // 리스트에 Alpha Co 가 나타남
    const option = await screen.findByRole("option", { name: /Alpha Co/ });
    await user.click(option);

    // pill 로 표시됨 (trigger 외부 요소에서 Alpha Co 가 등장)
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Alpha Co 제거" })).toBeInTheDocument();
    });

    // 다시 누르면 해제
    await user.click(option);
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Alpha Co 제거" })).not.toBeInTheDocument();
    });
  });

  it("기업이 하나도 없으면 안내 문구를 보여준다", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue({ content: [], totalCount: 0 });

    renderSelect([]);

    await user.click(screen.getByRole("button", { name: /기업을 선택하세요/ }));

    expect(await screen.findByText(/관심 기업에서 먼저 추가하세요/)).toBeInTheDocument();
  });
});
