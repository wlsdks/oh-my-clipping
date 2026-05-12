import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { CompetitorListTab } from "../CompetitorListTab";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { Competitor } from "@/types/competitor";

const listMock = vi.fn();

vi.mock("@/services/competitorService", () => ({
  competitorService: {
    list: (...args: unknown[]) => listMock(...args),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    collect: vi.fn(),
    previewKeywords: vi.fn(),
  },
}));

function makeCompetitor(overrides: Partial<Competitor> = {}): Competitor {
  return {
    id: "c1",
    name: "테스트 경쟁사",
    aliases: [],
    excludeKeywords: [],
    tier: "DIRECT",
    isActive: true,
    rssFeeds: [],
    articleCount: 0,
    last24hCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderTab() {
  return render(
    <MemoryRouter>
      <CompetitorListTab />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("CompetitorListTab — 관심 기업 동기화 안내", () => {
  beforeEach(() => {
    listMock.mockReset();
  });

  it("경쟁사가 있을 때 관심 기업 자동 동기화 안내 배너를 표시한다", async () => {
    listMock.mockResolvedValueOnce([makeCompetitor()]);

    renderTab();

    // 배너 텍스트와 "관심 기업" 링크 둘 다 존재해야 한다.
    expect(await screen.findByText(/자동으로 동기화/)).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "관심 기업" });
    expect(link).toHaveAttribute("href", "/admin/organizations");
  });
});
