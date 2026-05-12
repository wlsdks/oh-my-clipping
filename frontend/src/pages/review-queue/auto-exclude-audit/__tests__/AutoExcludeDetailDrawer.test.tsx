import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createQueryClientWrapper } from "@/test/queryClient";

/* ── 서비스 모킹 ── */

vi.mock("@/services/categoryRuleService", () => ({
  categoryRuleService: {
    getById: vi.fn()
  }
}));

import { categoryRuleService } from "@/services/categoryRuleService";
import { AutoExcludeDetailDrawer } from "../AutoExcludeDetailDrawer";
import type { AutoExcludedItem } from "@/types/autoExcludedItem";
import type { CategoryRule } from "@/types/category";

/* ── 픽스처 ── */

function makeItem(overrides: Partial<AutoExcludedItem> = {}): AutoExcludedItem {
  return {
    summaryId: "sum-1",
    title: "MegaCorp 신규 HBM",
    originalTitle: "TestCorp New HBM",
    translatedTitle: "MegaCorp 신규 HBM",
    categoryId: "cat-1",
    categoryName: "AI",
    score: 0.42,
    reason: "rule:event_type_blacklist",
    excludedAt: "2026-04-18T09:30:00Z",
    summary: "본문 요약 내용",
    sourceUrl: "https://example.com/article",
    sourceName: "테스트 매체",
    publishedAt: "2026-04-18T08:00:00Z",
    eventType: "MARKETING_PR",
    sentiment: "NEUTRAL",
    ...overrides
  };
}

function makeRule(overrides: Partial<CategoryRule> = {}): CategoryRule {
  return {
    categoryId: "cat-1",
    includeKeywords: ["HBM", "AI"],
    excludeKeywords: [],
    riskTags: [],
    excludeEventTypes: ["MARKETING_PR", "ACADEMIC"],
    includeThreshold: 0.7,
    reviewThreshold: 0.4,
    uncertainToReview: true,
    autoExcludeEnabled: true,
    revision: 1,
    updatedBy: "admin",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides
  };
}

function renderDrawer(props: Partial<React.ComponentProps<typeof AutoExcludeDetailDrawer>> = {}) {
  const defaultProps: React.ComponentProps<typeof AutoExcludeDetailDrawer> = {
    item: makeItem(),
    onClose: vi.fn(),
    onRestoreClick: vi.fn(),
    isRestoring: false
  };
  return render(<AutoExcludeDetailDrawer {...defaultProps} {...props} />, {
    wrapper: createQueryClientWrapper()
  });
}

/* ── 테스트 ── */

describe("AutoExcludeDetailDrawer", () => {
  beforeEach(() => {
    vi.mocked(categoryRuleService.getById).mockReset();
    vi.mocked(categoryRuleService.getById).mockResolvedValue(makeRule());
  });

  it("원제/번역제/요약/발행사 모두 렌더된다", async () => {
    const item = makeItem();
    renderDrawer({ item });

    // 원제 (SheetTitle).
    expect(await screen.findByText("TestCorp New HBM")).toBeInTheDocument();
    // 번역제 (헤더 보조 라인).
    expect(screen.getByText("MegaCorp 신규 HBM")).toBeInTheDocument();
    // 요약.
    expect(screen.getByText("본문 요약 내용")).toBeInTheDocument();
    // 메타 — sourceName · categoryName · 발행 MM/DD.
    expect(screen.getByText(/테스트 매체\s*·\s*AI\s*·\s*발행 04\/18/)).toBeInTheDocument();
  });

  it("translatedTitle 이 null 이면 보조 라인이 없다", async () => {
    const item = makeItem({ translatedTitle: null });
    renderDrawer({ item });

    // 원제만 렌더.
    expect(await screen.findByText("TestCorp New HBM")).toBeInTheDocument();
    // 번역제 (fixture 에 "MegaCorp 신규 HBM") 가 더 이상 존재하지 않음.
    expect(screen.queryByText("MegaCorp 신규 HBM")).not.toBeInTheDocument();
  });

  it("publishedAt 이 null 이면 메타 한 줄의 '발행 ...' 부분이 누락된다 (separator 꼬리 없음)", async () => {
    const item = makeItem({ publishedAt: null });
    renderDrawer({ item });

    await screen.findByText("TestCorp New HBM");

    // "발행" 이 아예 등장하지 않아야 한다.
    expect(screen.queryByText(/발행/)).not.toBeInTheDocument();

    // 메타 라인 textContent 가 정확히 "테스트 매체 · AI".
    const meta = screen.getByTestId("auto-exclude-meta");
    expect(meta.textContent).toBe("테스트 매체 · AI");
  });

  it("sourceName 도 null 이면 카테고리만 남고 '·' 꼬리가 없다", async () => {
    const item = makeItem({ sourceName: null, publishedAt: null });
    renderDrawer({ item });

    await screen.findByText("TestCorp New HBM");

    const meta = screen.getByTestId("auto-exclude-meta");
    expect(meta.textContent).toBe("AI");
  });

  it("sourceUrl null 이면 원문 열기 버튼이 disabled 되고 tooltip 이 붙는다", async () => {
    const item = makeItem({ sourceUrl: null });
    renderDrawer({ item });

    const btn = await screen.findByRole("button", { name: /원문 열기/ });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute("title", "원문 링크 없음");
  });

  it("event_type_blacklist 이면 CircleX 아이콘 + 차단 목록이 렌더된다", async () => {
    const item = makeItem({
      reason: "rule:event_type_blacklist",
      eventType: "MARKETING_PR"
    });
    renderDrawer({ item });

    // 제목 섹션 렌더 대기.
    await screen.findByText("TestCorp New HBM");

    // 섹션 제목 — "이벤트 타입 차단".
    expect(await screen.findByText("이벤트 타입 차단")).toBeInTheDocument();

    // 이 기사 event_type.
    expect(screen.getByText("MARKETING_PR")).toBeInTheDocument();

    // 카테고리 차단 목록 (rule 로드 완료 후).
    await waitFor(() => {
      expect(screen.getByText(/\[MARKETING_PR, ACADEMIC\]/)).toBeInTheDocument();
    });
  });

  it("zero_signal 인데 eventType 이 null 이면 데이터 불일치 경고가 뜬다", async () => {
    const item = makeItem({
      reason: "rule:zero_signal",
      eventType: null,
      sentiment: null
    });
    renderDrawer({ item });

    await screen.findByText("TestCorp New HBM");

    // 경고 텍스트.
    expect(await screen.findByText(/룰 실행 시점의 분류 정보.*저장되지 않았습니다/)).toBeInTheDocument();

    // event_type 불릿이 노출되지 않아야 한다.
    expect(screen.queryByText(/event_type:/)).not.toBeInTheDocument();
  });

  it("복구 버튼 클릭 시 onRestoreClick 이 해당 item 으로 호출된다", async () => {
    const onRestoreClick = vi.fn();
    const item = makeItem();
    renderDrawer({ item, onRestoreClick });

    const restoreBtn = await screen.findByRole("button", {
      name: /REVIEW 로 복구/
    });
    await userEvent.click(restoreBtn);

    expect(onRestoreClick).toHaveBeenCalledTimes(1);
    expect(onRestoreClick).toHaveBeenCalledWith(item);
  });

  it("원문 열기 링크는 새 탭으로 열린다 (target + rel)", async () => {
    const item = makeItem({ sourceUrl: "https://example.com/article" });
    renderDrawer({ item });

    // asChild 로 <a> 가 렌더되며 role=link 로 잡힌다.
    const link = await screen.findByRole("link", { name: /원문 열기/ });
    expect(link).toHaveAttribute("href", "https://example.com/article");
    expect(link).toHaveAttribute("target", "_blank");
    // rel 에 noopener + noreferrer 포함.
    const rel = link.getAttribute("rel") ?? "";
    expect(rel).toMatch(/noopener/);
    expect(rel).toMatch(/noreferrer/);
  });
});
