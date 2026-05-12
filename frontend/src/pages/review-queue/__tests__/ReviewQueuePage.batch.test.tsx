import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { Toaster } from "sonner";
import { ReviewQueuePage } from "../ReviewQueuePage";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { ReviewQueueItem, BulkActionResponse } from "@/types/review";
import type { RuntimeSettings } from "@/types/runtime";

// Service mocks — 실제 HTTP 호출 차단
vi.mock("@/services/reviewService", () => ({
  reviewService: {
    listItems: vi.fn(),
    getSummary: vi.fn(),
    approve: vi.fn(),
    exclude: vi.fn(),
    markForReview: vi.fn(),
    bulkApprove: vi.fn(),
    bulkExclude: vi.fn(),
    bulkRevert: vi.fn(),
  },
}));
vi.mock("@/services/categoryService", () => ({
  categoryService: { getAll: vi.fn() },
}));
vi.mock("@/services/runtimeService", () => ({
  runtimeService: { getSettings: vi.fn() },
}));

import { reviewService } from "@/services/reviewService";
import { categoryService } from "@/services/categoryService";
import { runtimeService } from "@/services/runtimeService";

function makeItem(overrides: Partial<ReviewQueueItem> = {}): ReviewQueueItem {
  return {
    summaryId: "sum-1",
    categoryId: "cat-1",
    categoryName: "AI",
    title: "샘플 뉴스",
    summary: "요약",
    sourceLink: "https://example.com/1",
    keywords: ["AI"],
    importanceScore: 0.8,
    suggestedStatus: "INCLUDE",
    currentStatus: "REVIEW",
    statusReason: "",
    priorityScore: 80,
    priorityLabel: "high",
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeSettings(overrides: Partial<RuntimeSettings> = {}): RuntimeSettings {
  return {
    defaultHoursBack: 24,
    summaryInputMaxChars: 5000,
    digestMinImportanceScore: 0.5,
    digestDefaultMaxItems: 5,
    digestMaxMessageChars: 3000,
    digestItemSummaryMaxChars: 200,
    digestKeywordMaxCount: 5,
    jobWorkerBatchSize: 10,
    jobMaxAttempts: 3,
    jobInitialBackoffSeconds: 30,
    slackBotToken: "",
    slackBotTokenConfigured: false,
    slackDigestBlockKitTemplate: "",
    slackAutoDigestEnabled: false,
    slackDigestCron: "0 9 * * 1-5",
    slackAutoDigestMaxItems: 5,
    slackAutoDigestUnsentOnly: true,
    slackDailyChannelMessageLimit: 50,
    ralphOrchestrationEnabled: false,
    ralphLoopEnabled: false,
    ralphLoopMaxIterations: 3,
    ralphLoopStopPhrase: "stop",
    maintenanceMode: false,
    maintenanceMessage: "",
    opsLogChannelId: "",
    opsRequestChannelId: "",
    securityAlertChannelId: "",
    competitorWeeklyEnabled: false,
    competitorWeeklyChannelId: "",
    competitorWeeklyDmMode: "off",
    competitorWeeklyDmUserIds: "",
    competitorWeeklyDay: "MONDAY",
    competitorWeeklyHour: 9,
    reviewBatchUxEnabled: true,
    defaultReviewPerCategory: 20,
    retentionRssItemsDays: 30,
    retentionBatchSummariesDays: 90,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ReviewQueuePage />
      <Toaster />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

// 모바일 판별용 matchMedia 목
function mockMatchMedia(isMobile: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: isMobile && query.includes("max-width: 767px"),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  });
}

const sampleItems: ReviewQueueItem[] = [
  makeItem({ summaryId: "a", title: "첫 뉴스" }),
  makeItem({ summaryId: "b", title: "두번째 뉴스", importanceScore: 0.5 }),
  makeItem({ summaryId: "c", title: "세번째 뉴스" }),
];

beforeEach(() => {
  vi.clearAllMocks();
  mockMatchMedia(false);
  vi.mocked(categoryService.getAll).mockResolvedValue([]);
  vi.mocked(reviewService.getSummary).mockResolvedValue({
    totalCount: 0,
    reviewCount: 0,
    includeCount: 0,
    excludeCount: 0,
    categories: [],
  });
  vi.mocked(reviewService.listItems).mockResolvedValue(sampleItems);
});

describe("ReviewQueuePage — 일괄 UX", () => {
  it("feature flag가 off면 체크박스를 렌더하지 않는다 (단건 모드)", async () => {
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: false })
    );
    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0);
    });
    // 체크박스가 없어야 함
    expect(screen.queryByLabelText("모두 선택")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("선택")).not.toBeInTheDocument();
  });

  it("feature flag on이면 체크박스와 '모두 선택' 컨트롤을 렌더한다", async () => {
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0);
    });
    expect(screen.getByLabelText("모두 선택")).toBeInTheDocument();
  });

  it("모바일 환경이면 feature flag on이어도 체크박스를 노출하지 않는다", async () => {
    mockMatchMedia(true);
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    renderPage();

    await waitFor(() => {
      // 모바일에서도 데스크톱 panel은 렌더되지만 md:hidden일 뿐. 체크박스가 "모두 선택"은 없어야 함.
      // 구조상 데스크톱 panel은 md:grid로 숨겨지고, 모바일 panel만 체크박스 없이 렌더된다.
      const moduleLabels = screen.queryAllByLabelText("모두 선택");
      expect(moduleLabels.length).toBe(0);
    });
  });

  it("체크박스를 선택하면 FloatingActionBar가 나타난다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0);
    });

    // 첫번째 체크박스 클릭 (모두 선택은 [0]번, 첫 row가 [1]번)
    const checkboxes = screen.getAllByRole("checkbox");
    await user.click(checkboxes[1]);

    await waitFor(() => {
      expect(screen.getAllByText(/1건 선택/).length).toBeGreaterThan(0);
    });
    // FloatingActionBar의 승인 버튼
    expect(screen.getByRole("button", { name: "보내기" })).toBeInTheDocument();
  });

  it("부분 실패 시 성공/실패 카운트를 포함한 경고 토스트를 노출한다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    const partialResult: BulkActionResponse = {
      succeeded: ["a"],
      failed: [{ id: "b", reason: "이미 처리됨", code: "ALREADY_PROCESSED" }],
    };
    vi.mocked(reviewService.bulkApprove).mockResolvedValue(partialResult);

    renderPage();
    await waitFor(() => {
      expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0);
    });

    // 모두 선택 → 2건 처리 시나리오
    await user.click(screen.getByLabelText("모두 선택"));

    // FloatingActionBar에서 "보내기" 누르면 다이얼로그
    await user.click(screen.getByRole("button", { name: "보내기" }));

    // 다이얼로그 체크박스 및 확인
    const ackBox = await screen.findByLabelText("샘플을 확인했어요");
    await user.click(ackBox);
    await user.click(screen.getByRole("button", { name: "보내기로 처리" }));

    await waitFor(() => {
      expect(reviewService.bulkApprove).toHaveBeenCalled();
    });

    // 부분 실패 토스트 텍스트
    await waitFor(() => {
      expect(screen.getByText(/성공, .*실패/)).toBeInTheDocument();
    });
  });

  it("전원 실패 시 에러 토스트를 노출한다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    vi.mocked(reviewService.bulkApprove).mockResolvedValue({
      succeeded: [],
      failed: [{ id: "a", reason: "이미 처리됨", code: "ALREADY_PROCESSED" }],
    });

    renderPage();
    await waitFor(() => expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0));

    await user.click(screen.getByLabelText("모두 선택"));
    await user.click(screen.getByRole("button", { name: "보내기" }));

    const ackBox = await screen.findByLabelText("샘플을 확인했어요");
    await user.click(ackBox);
    await user.click(screen.getByRole("button", { name: "보내기로 처리" }));

    await waitFor(() => {
      expect(screen.getByText(/모두 실패/)).toBeInTheDocument();
    });
  });

  it("저신뢰 항목이 섞여 있으면 BulkApproveDialog에 경고 배너를 렌더한다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    // b는 importanceScore 0.5 (저신뢰)
    renderPage();
    await waitFor(() => expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0));

    await user.click(screen.getByLabelText("모두 선택"));
    await user.click(screen.getByRole("button", { name: "보내기" }));

    // 다이얼로그 내부에 경고 배너
    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText(/검토 필요 항목이 1건 섞여 있어요/)).toBeInTheDocument();
  });

  it("확인 체크박스 없이는 승인 버튼이 비활성이다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    renderPage();
    await waitFor(() => expect(screen.getAllByText("첫 뉴스").length).toBeGreaterThan(0));

    await user.click(screen.getByLabelText("모두 선택"));
    await user.click(screen.getByRole("button", { name: "보내기" }));

    const confirmButton = (await screen.findByRole("button", { name: "보내기로 처리" })) as HTMLButtonElement;
    expect(confirmButton.disabled).toBe(true);
  });

  it("overflow 선택(20건 초과) 시 경고 토스트를 띄우고 다이얼로그를 열지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(runtimeService.getSettings).mockResolvedValue(
      makeSettings({ reviewBatchUxEnabled: true })
    );
    // 25건 생성
    const manyItems = Array.from({ length: 25 }, (_, i) =>
      makeItem({ summaryId: `s-${i}`, title: `뉴스 ${i}` })
    );
    vi.mocked(reviewService.listItems).mockResolvedValue(manyItems);

    renderPage();
    await waitFor(() => expect(screen.getAllByText("뉴스 0").length).toBeGreaterThan(0));

    // 모두 선택 → 25건 (> MAX_BULK_SELECT)
    await user.click(screen.getByLabelText("모두 선택"));

    // FloatingActionBar에 overflow 경고 문구 노출, 버튼 disabled
    await waitFor(() => {
      expect(screen.getByText(/최대 20건까지/)).toBeInTheDocument();
    });
    const approveBtn = screen.getByRole("button", { name: "보내기" }) as HTMLButtonElement;
    expect(approveBtn.disabled).toBe(true);
  });
});
