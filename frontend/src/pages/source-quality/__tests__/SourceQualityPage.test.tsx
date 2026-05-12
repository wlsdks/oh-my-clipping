// frontend/src/pages/source-quality/__tests__/SourceQualityPage.test.tsx
import { describe, it, expect, vi, beforeEach, beforeAll } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

/* ── jsdom 폴리필 (Radix Dialog / Select / framer-motion) ── */

beforeAll(() => {
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
  window.HTMLElement.prototype.scrollTo =
    window.HTMLElement.prototype.scrollTo || (() => {});
});

/* ── Service mocks ── */

vi.mock("@/services/sourceQualityService", () => ({
  sourceQualityService: {
    getSummary: vi.fn(),
  },
}));

vi.mock("@/services/sourceService", () => ({
  sourceService: {
    getById: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock("@/services/categoryService", () => ({
  categoryService: {
    getAll: vi.fn(),
  },
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

// SourceEditModal 은 프리젠테이션 + 내부에 다른 쿼리를 많이 쓰므로
// 단위 테스트에서는 열림 여부만 확인하는 경량 stub 으로 대체한다.
vi.mock("@/features/source-edit/SourceEditModal", () => ({
  SourceEditModal: ({
    open,
    source,
    onClose,
  }: {
    open: boolean;
    source: { id: string; name: string } | null;
    categories: unknown[];
    onClose: () => void;
  }) =>
    open ? (
      <div data-testid="source-edit-modal">
        <div data-testid="source-edit-modal-source-name">{source?.name}</div>
        <button type="button" onClick={onClose} data-testid="source-edit-modal-close">
          닫기
        </button>
      </div>
    ) : null,
}));

import { toast } from "sonner";
import { sourceQualityService } from "@/services/sourceQualityService";
import { sourceService } from "@/services/sourceService";
import { categoryService } from "@/services/categoryService";
import { SourceQualityPage } from "../SourceQualityPage";
import type {
  SourceQualitySummary,
  SourceQualityRow,
} from "@/types/sourceQuality";
import type { Source } from "@/types/source";

/* ── Fixtures ── */

function row(overrides: Partial<SourceQualityRow> = {}): SourceQualityRow {
  return {
    sourceId: "s1",
    sourceName: "TechCrunch",
    delivered: 100,
    uniqueUserClicks: 28,
    clickRatePct: 28.0,
    likes: 18,
    dislikes: 2,
    likeRatePct: 90.0,
    statusLabel: "normal",
    isActive: true,
    updatedAt: "2026-04-18T00:00:00Z",
    ...overrides,
  };
}

function summary(rows: SourceQualityRow[]): SourceQualitySummary {
  return { sourceQuality: rows };
}

const mockSource: Source = {
  id: "s1",
  name: "TechCrunch",
  url: "https://techcrunch.com/feed",
  sourceRegion: "GLOBAL",
  emoji: null,
  isActive: true,
  crawlApproved: true,
  approvedBy: null,
  approvedAt: null,
  legalBasis: "RSS_OFFICIAL",
  summaryAllowed: true,
  fulltextAllowed: false,
  termsReviewedAt: null,
  expectedReviewAt: null,
  reviewNotes: null,
  verificationStatus: "VERIFIED",
  reliabilityScore: 1.0,
  lastCrawlError: null,
  crawlFailCount: 0,
  lastSuccessAt: null,
  curated: false,
  categoryId: "cat-1",
  createdAt: "2026-04-01T00:00:00Z",
  updatedAt: "2026-04-18T00:00:00Z",
};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <SourceQualityPage />
    </QueryClientProvider>,
  );
}

/* ── Tests ── */

describe("SourceQualityPage", () => {
  beforeEach(() => {
    vi.mocked(sourceQualityService.getSummary).mockReset();
    vi.mocked(sourceService.getById).mockReset();
    vi.mocked(sourceService.update).mockReset();
    vi.mocked(categoryService.getAll).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();

    // 합리적 기본값
    vi.mocked(sourceService.getById).mockResolvedValue(mockSource);
    vi.mocked(categoryService.getAll).mockResolvedValue([]);
  });

  it("1. 로딩 상태 — summary 응답 전 로딩 메시지 표시", () => {
    vi.mocked(sourceQualityService.getSummary).mockReturnValue(
      new Promise(() => undefined),
    );
    renderPage();
    expect(screen.getByText("로딩 중…")).toBeInTheDocument();
  });

  it("2. 에러 상태 — 재시도 버튼 클릭 시 getSummary 재호출", async () => {
    const user = userEvent.setup();
    // 첫 호출 실패 → 두 번째 호출 성공
    vi.mocked(sourceQualityService.getSummary)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce(summary([row()]));

    renderPage();

    // 에러 화면 노출 대기
    expect(
      await screen.findByText("소스 품질 데이터를 불러오지 못했어요"),
    ).toBeInTheDocument();
    expect(sourceQualityService.getSummary).toHaveBeenCalledTimes(1);

    const retryBtn = screen.getByRole("button", { name: /다시 시도/ });
    await user.click(retryBtn);

    // 두 번째 호출이 발생하고 데이터가 렌더되어야 한다
    await waitFor(() =>
      expect(sourceQualityService.getSummary).toHaveBeenCalledTimes(2),
    );
    expect(await screen.findByText("TechCrunch")).toBeInTheDocument();
  });

  it("3. 정상 데이터 — KPI 카드 + 테이블 두 섹션 모두 렌더", async () => {
    vi.mocked(sourceQualityService.getSummary).mockResolvedValue(
      summary([
        row({ sourceId: "s1", sourceName: "Alpha", clickRatePct: 15 }),
        row({
          sourceId: "s2",
          sourceName: "Beta",
          clickRatePct: 3,
          statusLabel: "review",
        }),
      ]),
    );

    renderPage();

    // KPI + 테이블 모두 존재
    expect(await screen.findByTestId("source-quality-kpi-cards")).toBeInTheDocument();
    expect(screen.getByTestId("source-quality-table")).toBeInTheDocument();
    // 테이블 내 두 소스 노출
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Beta")).toBeInTheDocument();
    // 검토 필요 KPI = 1 (review 1개)
    const reviewCard = screen.getByTestId("kpi-card-review");
    expect(reviewCard).toHaveTextContent("1");
  });

  it("4. 편집 버튼 → sourceService.getById + categoryService.getAll 호출 + SourceEditModal 오픈", async () => {
    const user = userEvent.setup();
    vi.mocked(sourceQualityService.getSummary).mockResolvedValue(
      summary([row({ sourceId: "s1", sourceName: "TechCrunch" })]),
    );

    renderPage();

    const editBtn = await screen.findByRole("button", { name: /TechCrunch 편집/ });
    await user.click(editBtn);

    // 단건 조회 + 카테고리 목록 fetch 가 발동해야 한다
    await waitFor(() => {
      expect(sourceService.getById).toHaveBeenCalledWith("s1");
      expect(categoryService.getAll).toHaveBeenCalled();
    });
    // 모달 오픈 (stub 으로 확인)
    expect(await screen.findByTestId("source-edit-modal")).toBeInTheDocument();
    expect(screen.getByTestId("source-edit-modal-source-name")).toHaveTextContent(
      "TechCrunch",
    );
  });

  it("5. 수집 일시중지 → confirm dialog → 확정 → update({isActive:false}) + toast.success + 재조회", async () => {
    const user = userEvent.setup();
    vi.mocked(sourceQualityService.getSummary).mockResolvedValue(
      summary([
        row({
          sourceId: "s1",
          sourceName: "TechCrunch",
          updatedAt: "2026-04-18T00:00:00Z",
        }),
      ]),
    );
    vi.mocked(sourceService.update).mockResolvedValue(mockSource);

    renderPage();

    const pauseBtn = await screen.findByRole("button", {
      name: /TechCrunch 수집 일시중지/,
    });
    await user.click(pauseBtn);

    // 확인 다이얼로그 노출
    const confirmBtn = await screen.findByTestId("source-deactivate-confirm");
    await user.click(confirmBtn);

    // mutation 호출 파라미터 검증
    await waitFor(() =>
      expect(sourceService.update).toHaveBeenCalledWith("s1", {
        isActive: false,
        expectedUpdatedAt: "2026-04-18T00:00:00Z",
      }),
    );
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("수집을 일시중지했습니다"),
    );
    // summary 는 mutation 직후 invalidation 으로 재호출 — 최소 2회
    await waitFor(() =>
      expect(
        vi.mocked(sourceQualityService.getSummary).mock.calls.length,
      ).toBeGreaterThanOrEqual(2),
    );
  });

  it("6. 활성화 — confirm dialog 없이 즉시 update({isActive:true}) 호출 + toast.success", async () => {
    const user = userEvent.setup();
    vi.mocked(sourceQualityService.getSummary).mockResolvedValue(
      summary([
        row({
          sourceId: "s2",
          sourceName: "Beta",
          isActive: false,
          updatedAt: "2026-04-10T00:00:00Z",
        }),
      ]),
    );
    vi.mocked(sourceService.update).mockResolvedValue(mockSource);

    renderPage();

    // 테이블이 렌더될 때까지 대기 후, "비활성" 필터로 전환하여 활성화 버튼 노출
    await screen.findByTestId("source-quality-table");
    const inactiveFilter = screen.getByRole("radio", { name: "비활성" });
    await user.click(inactiveFilter);

    const activateBtn = await screen.findByRole("button", {
      name: /Beta 활성화/,
    });
    await user.click(activateBtn);

    // 확인 다이얼로그 없이 즉시 mutate
    expect(screen.queryByTestId("source-deactivate-dialog")).not.toBeInTheDocument();

    await waitFor(() =>
      expect(sourceService.update).toHaveBeenCalledWith("s2", {
        isActive: true,
        expectedUpdatedAt: "2026-04-10T00:00:00Z",
      }),
    );
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "수집을 재개했습니다 (실패 카운트 초기화)",
      ),
    );
  });

  it("7. 기간 필터 변경 → 새 period 로 getSummary 재호출", async () => {
    const user = userEvent.setup();
    vi.mocked(sourceQualityService.getSummary).mockResolvedValue(summary([row()]));

    renderPage();

    await screen.findByTestId("source-quality-table");
    expect(sourceQualityService.getSummary).toHaveBeenCalledWith("28d");

    const sevenDayBtn = screen.getByRole("radio", { name: "7일" });
    await user.click(sevenDayBtn);

    await waitFor(() =>
      expect(sourceQualityService.getSummary).toHaveBeenCalledWith("7d"),
    );
  });
});
