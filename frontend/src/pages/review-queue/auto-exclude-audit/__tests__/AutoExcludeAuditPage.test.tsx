import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createQueryClientWrapper } from "@/test/queryClient";

vi.mock("@/services/categoryRuleService", () => ({
  categoryRuleService: {
    listAutoExcluded: vi.fn(),
    restoreFromAutoExclude: vi.fn(),
    // 드로어가 열릴 때 룰 근거 섹션이 getById 를 lazy fetch 한다.
    getById: vi.fn()
  }
}));

vi.mock("@/services/categoryService", () => ({
  categoryService: {
    getAll: vi.fn()
  }
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn()
  }
}));

import { toast } from "sonner";
import { categoryRuleService } from "@/services/categoryRuleService";
import { categoryService } from "@/services/categoryService";
import { AutoExcludeAuditPage } from "../AutoExcludeAuditPage";
import type { AutoExcludedItem, AutoExcludedResponse } from "@/types/autoExcludedItem";
import type { Category, CategoryRule } from "@/types/category";

/* ── 픽스처 ── */

function makeItem(overrides: Partial<AutoExcludedItem> = {}): AutoExcludedItem {
  return {
    summaryId: "sum-1",
    title: "MegaCorp, 신규 HBM 양산 일정 공개",
    originalTitle: "TestCorp Announces New HBM Mass Production Schedule",
    translatedTitle: "MegaCorp, 신규 HBM 양산 일정 공개",
    categoryId: "cat-1",
    categoryName: "반도체",
    score: 0.42,
    reason: "rule:event_type_blacklist",
    excludedAt: "2026-04-18T09:30:00Z",
    summary: "MegaCorp가 신규 HBM 양산 일정을 공개했다.",
    sourceUrl: "https://example.com/testcorp-hbm",
    sourceName: "예시 경제",
    publishedAt: "2026-04-18T08:00:00Z",
    eventType: "ANNOUNCEMENT",
    sentiment: "NEUTRAL",
    ...overrides
  };
}

function makeResponse(overrides: Partial<AutoExcludedResponse> = {}): AutoExcludedResponse {
  return {
    items: [makeItem()],
    totalCount: 1,
    reasonBreakdown: { "rule:event_type_blacklist": 1 },
    ...overrides
  };
}

const mockCategories: Category[] = [
  {
    id: "cat-1",
    name: "반도체",
    description: null,
    slackChannelId: null,
    isActive: true,
    isPublic: true,
    maxItems: 10,
    personaId: null,
    sourceCount: 3,
    subscriberCount: 5,
    lastDeliveryAt: null,
    errorSourceCount: 0,
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    status: "ACTIVE",
    pausedAt: null
  } as Category
];

const defaultCategoryRule: CategoryRule = {
  categoryId: "cat-1",
  includeKeywords: ["HBM"],
  excludeKeywords: [],
  riskTags: [],
  excludeEventTypes: ["ANNOUNCEMENT"],
  includeThreshold: 0.7,
  reviewThreshold: 0.4,
  uncertainToReview: true,
  autoExcludeEnabled: true,
  revision: 1,
  updatedBy: "admin",
  updatedAt: "2026-04-01T00:00:00Z"
};

function renderPage() {
  return render(<AutoExcludeAuditPage />, { wrapper: createQueryClientWrapper() });
}

/* ── 테스트 ── */

describe("AutoExcludeAuditPage", () => {
  beforeEach(() => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockReset();
    vi.mocked(categoryRuleService.restoreFromAutoExclude).mockReset();
    vi.mocked(categoryRuleService.getById).mockReset();
    vi.mocked(categoryService.getAll).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
    vi.mocked(toast.info).mockReset();

    // 기본 카테고리 fetch 성공 응답.
    vi.mocked(categoryService.getAll).mockResolvedValue(mockCategories);
    // 드로어가 언마운트/마운트될 때 getById 가 unresolved 로 매달리지 않도록 기본값 제공.
    vi.mocked(categoryRuleService.getById).mockResolvedValue(defaultCategoryRule);
  });

  it("리스트 로드 시 제목/reason 한국어 라벨/복구 버튼을 렌더한다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(makeResponse());

    renderPage();

    // 제목 & reason 한국어 라벨 노출.
    await waitFor(() => {
      expect(screen.getByText("MegaCorp, 신규 HBM 양산 일정 공개")).toBeInTheDocument();
    });
    // reason 은 테이블 셀에 "이벤트 타입 차단" 으로 노출 — 요약 카드에도 같은 라벨이 있으므로 getAllByText 로 확인.
    expect(screen.getAllByText("이벤트 타입 차단").length).toBeGreaterThanOrEqual(1);
    // 복구 버튼 존재.
    expect(screen.getByRole("button", { name: /복구$/ })).toBeInTheDocument();
  });

  it("카테고리 필터 변경 시 새 파라미터로 재조회한다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({ items: [], totalCount: 0, reasonBreakdown: {} })
    );

    renderPage();

    // 초기 호출 — categoryId 없이 호출되는지 확인.
    await waitFor(() => {
      expect(categoryRuleService.listAutoExcluded).toHaveBeenCalled();
    });
    const initialCall = vi.mocked(categoryRuleService.listAutoExcluded).mock.calls.at(-1);
    expect(initialCall?.[0].categoryId).toBeUndefined();

    // 카테고리 드롭다운 열고 "반도체" 선택.
    const categorySelect = screen.getByLabelText("카테고리 필터");
    await userEvent.click(categorySelect);
    const opt = await screen.findByRole("option", { name: "반도체" });
    await userEvent.click(opt);

    // categoryId='cat-1' 로 재조회.
    await waitFor(() => {
      const latest = vi.mocked(categoryRuleService.listAutoExcluded).mock.calls.at(-1);
      expect(latest?.[0].categoryId).toBe("cat-1");
    });
  });

  it("복구 버튼 클릭 → 확인 다이얼로그 → mutation 성공 시 toast + 재조회", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(makeResponse());
    vi.mocked(categoryRuleService.restoreFromAutoExclude).mockResolvedValue({
      summaryId: "sum-1",
      newStatus: "REVIEW"
    });

    renderPage();

    // 복구 버튼 노출 대기.
    const restoreBtn = await screen.findByRole("button", { name: /복구$/ });
    await userEvent.click(restoreBtn);

    // confirm 모달 제목/설명 확인.
    expect(await screen.findByText("REVIEW 로 복구할까요?")).toBeInTheDocument();
    // 모달의 "복구" 확정 버튼 — DialogFooter 의 마지막 "복구" 텍스트 버튼.
    const confirmBtn = screen.getAllByRole("button", { name: /복구$/ }).at(-1)!;
    await userEvent.click(confirmBtn);

    // mutation 호출 + 성공 토스트.
    await waitFor(() => {
      expect(categoryRuleService.restoreFromAutoExclude).toHaveBeenCalledWith("sum-1");
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(expect.stringContaining("REVIEW"));
    });
  });

  it("빈 결과일 때 EmptyState 를 렌더한다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({ items: [], totalCount: 0, reasonBreakdown: {} })
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("자동 제외된 기사가 없어요")).toBeInTheDocument();
    });
    // 합계 0 노출 — 요약 카드 숫자.
    expect(screen.getByText("0")).toBeInTheDocument();
  });

  it("제목 셀이 접근 가능한 버튼으로 렌더된다 (aria-label 포함)", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({
        items: [makeItem({ title: "테스트 제목" })],
        totalCount: 1,
        reasonBreakdown: { "rule:event_type_blacklist": 1 }
      })
    );

    renderPage();

    await screen.findByText("테스트 제목");

    const titleButton = screen.getByRole("button", {
      name: /테스트 제목 상세 보기/
    });
    expect(titleButton).toBeInTheDocument();
    expect(titleButton.tagName).toBe("BUTTON");
  });

  it("제목 클릭 시 드로어가 열리고 요약 본문이 보인다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({
        items: [
          makeItem({
            title: "드로어 테스트 기사",
            summary: "드로어에 표시될 고유 요약 본문입니다."
          })
        ],
        totalCount: 1,
        reasonBreakdown: { "rule:event_type_blacklist": 1 }
      })
    );

    renderPage();

    // 제목 버튼 클릭 → 드로어 오픈.
    const titleBtn = await screen.findByRole("button", {
      name: /드로어 테스트 기사 상세 보기/
    });
    await userEvent.click(titleBtn);

    // 요약 본문이 드로어 안에서 보여야 한다 — 리스트 테이블에는 summary 가 렌더되지 않으므로 unique.
    await waitFor(() => {
      expect(screen.getByText("드로어에 표시될 고유 요약 본문입니다.")).toBeInTheDocument();
    });
  });

  it("드로어 열린 상태에서 '복구' 로 성공하면 드로어가 자동으로 닫힌다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({
        items: [
          makeItem({
            title: "자동 닫힘 테스트",
            summary: "드로어-자동닫힘-본문"
          })
        ],
        totalCount: 1,
        reasonBreakdown: { "rule:event_type_blacklist": 1 }
      })
    );
    vi.mocked(categoryRuleService.restoreFromAutoExclude).mockResolvedValue({
      summaryId: "sum-1",
      newStatus: "REVIEW"
    });

    renderPage();

    // 제목 클릭 → 드로어 오픈.
    const titleBtn = await screen.findByRole("button", {
      name: /자동 닫힘 테스트 상세 보기/
    });
    await userEvent.click(titleBtn);

    // 드로어 본문이 보이는지 확인 (드로어가 실제로 열렸다는 증거).
    await waitFor(() => {
      expect(screen.getByText("드로어-자동닫힘-본문")).toBeInTheDocument();
    });

    // 드로어가 열리면 Radix 가 뒤쪽 콘텐츠에 aria-hidden 을 건다.
    // 드로어 내부의 "REVIEW 로 복구" 버튼을 클릭 — row 버튼과 같은 ConfirmModal + mutation 을 거친다.
    const drawerRestoreBtn = await screen.findByRole("button", {
      name: /REVIEW 로 복구/
    });
    await userEvent.click(drawerRestoreBtn);

    // ConfirmModal 오픈 확인.
    expect(await screen.findByText("REVIEW 로 복구할까요?")).toBeInTheDocument();

    // 확인 모달의 "복구" 확정 버튼 — 마지막 /복구$/ 버튼.
    const confirmBtn = screen.getAllByRole("button", { name: /복구$/ }).at(-1)!;
    await userEvent.click(confirmBtn);

    // mutation 성공 → 드로어 auto-close.
    await waitFor(() => {
      expect(categoryRuleService.restoreFromAutoExclude).toHaveBeenCalledWith("sum-1");
    });
    await waitFor(() => {
      // 드로어 본문이 DOM 에서 사라짐 — 드로어가 닫혔다는 증거.
      expect(screen.queryByText("드로어-자동닫힘-본문")).not.toBeInTheDocument();
    });
  });

  it("reason breakdown 은 요약 카드에 pill 로 노출된다", async () => {
    vi.mocked(categoryRuleService.listAutoExcluded).mockResolvedValue(
      makeResponse({
        items: [
          makeItem({ summaryId: "s1", reason: "rule:event_type_blacklist" }),
          makeItem({
            summaryId: "s2",
            reason: "rule:zero_signal",
            title: "시그널 없는 기사"
          })
        ],
        totalCount: 2,
        reasonBreakdown: {
          "rule:event_type_blacklist": 1,
          "rule:zero_signal": 1
        }
      })
    );

    renderPage();

    await waitFor(() => {
      // 두 라벨 모두 렌더.
      expect(screen.getAllByText("이벤트 타입 차단").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("시그널 없음").length).toBeGreaterThanOrEqual(1);
    });
  });
});
