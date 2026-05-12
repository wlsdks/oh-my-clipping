import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { Source, SourcePage } from "@/types/source";
import { createQueryClientWrapper } from "@/test/queryClient";

// ── 서비스 mock (vi.mock 은 import 전에 호이스팅 됨) ────────────────────
vi.mock("@/services/sourceService", () => ({
  sourceService: {
    getPage: vi.fn().mockResolvedValue({
      content: [],
      totalCount: 0,
      page: 0,
      size: 50,
    }),
    getAll: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    verify: vi.fn(),
    approve: vi.fn(),
    bulkVerify: vi.fn(),
    bulkArchive: vi.fn(),
    validateUrl: vi.fn().mockResolvedValue({ valid: true, reason: "" }),
    getArticleCounts: vi.fn().mockResolvedValue({ counts: {}, days: 7 }),
    getCoverageGaps: vi.fn().mockResolvedValue({ gaps: [] }),
    getById: vi.fn(),
    discoverSource: vi.fn(),
    getAnalytics: vi.fn(),
    getCompliance: vi.fn(),
    updateCompliance: vi.fn(),
    getCrawlHistory: vi.fn(),
    getAiCosts: vi.fn(),
  },
}));

vi.mock("@/services/categoryService", () => ({
  categoryService: {
    getAll: vi.fn().mockResolvedValue([]),
    getPage: vi.fn().mockResolvedValue({ content: [], totalCount: 0, page: 0, size: 50 }),
    create: vi.fn(),
  },
}));

vi.mock("@/services/pipelineService", () => ({
  pipelineService: {
    listRuns: vi.fn().mockResolvedValue({ content: [], totalCount: 0, page: 0, size: 1 }),
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

// recharts 는 jsdom 에서 ResizeObserver 가 필요함 — ResponsiveContainer 간단 치환
vi.mock("recharts", async () => {
  const actual: Record<string, unknown> = await vi.importActual("recharts");
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div style={{ width: 400, height: 300 }}>{children}</div>
    ),
  };
});

import { SourcesPage } from "../SourcesPage";
import { sourceService } from "@/services/sourceService";

// ── 테스트 헬퍼 ────────────────────────────────────────────────────────

function makeSource(overrides: Partial<Source> = {}): Source {
  return {
    id: overrides.id ?? "src-1",
    name: overrides.name ?? "테스트 소스",
    url: overrides.url ?? "https://example.com/rss",
    sourceRegion: overrides.sourceRegion ?? "DOMESTIC",
    emoji: overrides.emoji ?? null,
    isActive: overrides.isActive ?? true,
    crawlApproved: overrides.crawlApproved ?? true,
    approvedBy: overrides.approvedBy ?? null,
    approvedAt: overrides.approvedAt ?? null,
    legalBasis: overrides.legalBasis ?? "QUOTATION_ONLY",
    summaryAllowed: overrides.summaryAllowed ?? true,
    fulltextAllowed: overrides.fulltextAllowed ?? false,
    termsReviewedAt: overrides.termsReviewedAt ?? null,
    reviewNotes: overrides.reviewNotes ?? null,
    verificationStatus: overrides.verificationStatus ?? "OK",
    reliabilityScore: overrides.reliabilityScore ?? 95,
    lastCrawlError: overrides.lastCrawlError ?? null,
    crawlFailCount: overrides.crawlFailCount ?? 0,
    lastSuccessAt: overrides.lastSuccessAt ?? new Date().toISOString(),
    curated: overrides.curated ?? false,
    categoryId: overrides.categoryId ?? "cat-1",
    createdAt: overrides.createdAt ?? new Date().toISOString(),
    updatedAt: overrides.updatedAt ?? new Date().toISOString(),
    ...overrides,
  };
}

function makeSourcePage(sources: Source[] = []): SourcePage {
  return {
    content: sources,
    totalCount: sources.length,
    page: 0,
    size: 50,
  };
}

function renderSourcesPage(initialEntries: string[] = ["/admin/sources"]) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <SourcesPage />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(sourceService.getPage).mockResolvedValue(makeSourcePage([]));
  vi.mocked(sourceService.getArticleCounts).mockResolvedValue({ counts: {}, days: 7 });
});

// ── 테스트 ────────────────────────────────────────────────────────────

describe("SourcesPage — 로딩 및 빈 상태", () => {
  it("로딩 중에는 스켈레톤과 스크린리더 안내 문구가 표시된다", () => {
    // getPage 가 해소되지 않도록 새 Promise 로 유지
    vi.mocked(sourceService.getPage).mockReturnValue(new Promise(() => {}));
    renderSourcesPage();
    expect(screen.getByText("로딩 중...")).toBeInTheDocument();
  });

  it("데이터가 없고 검색/필터가 없을 때 빈 상태 화면을 표시한다", async () => {
    vi.mocked(sourceService.getPage).mockResolvedValue(makeSourcePage([]));

    renderSourcesPage();

    // SourcesEmptyState — 빈 상태 전용 CTA 포함
    await waitFor(() => {
      // '소스 추가' 또는 관련 empty state 문구가 보인다
      const heading = screen.queryByRole("heading");
      expect(heading).toBeTruthy();
    });
  });
});

describe("SourcesPage — 에러 상태", () => {
  it("getPage 가 실패하면 에러 문구와 '다시 시도' 버튼을 표시한다", async () => {
    vi.mocked(sourceService.getPage).mockRejectedValue(new Error("500"));

    renderSourcesPage();

    await waitFor(() => {
      expect(screen.getByText("소스 목록을 불러오지 못했어요")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("'다시 시도' 버튼을 클릭하면 getPage 가 재호출된다", async () => {
    vi.mocked(sourceService.getPage).mockRejectedValue(new Error("500"));

    renderSourcesPage();

    await waitFor(() => screen.getByText("소스 목록을 불러오지 못했어요"));

    const retryBtn = screen.getByRole("button", { name: "다시 시도" });
    const callsBefore = vi.mocked(sourceService.getPage).mock.calls.length;
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(vi.mocked(sourceService.getPage).mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });
});

describe("SourcesPage — 데이터 렌더", () => {
  it("활성 소스 목록이 있으면 소스 이름이 화면에 표시된다", async () => {
    const source = makeSource({ id: "s-1", name: "매우유니크소스명아아" });
    vi.mocked(sourceService.getPage).mockResolvedValue(makeSourcePage([source]));

    renderSourcesPage();

    await waitFor(() => {
      expect(screen.getByText("매우유니크소스명아아")).toBeInTheDocument();
    });
  });

  it("URL 쿼리에 categoryId 가 있으면 해당 값이 getPage 요청에 전달된다", async () => {
    vi.mocked(sourceService.getPage).mockResolvedValue(makeSourcePage([]));

    renderSourcesPage(["/admin/sources?categoryId=cat-abc"]);

    await waitFor(() => {
      const calls = vi.mocked(sourceService.getPage).mock.calls;
      const paramsList = calls
        .map((c) => c[0] as URLSearchParams | undefined)
        .filter((p): p is URLSearchParams => p instanceof URLSearchParams);
      // 어느 호출에서는 categoryId=cat-abc 를 포함해야 한다
      const hasCategory = paramsList.some((p) => p.get("categoryId") === "cat-abc");
      expect(hasCategory).toBe(true);
    });
  });
});
