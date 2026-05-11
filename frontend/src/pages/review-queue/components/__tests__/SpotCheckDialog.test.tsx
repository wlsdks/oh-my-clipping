import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeAll } from "vitest";
import { SpotCheckDialog } from "../SpotCheckDialog";
import type { ReviewItemSummary } from "../types";

// Radix Dialog 는 jsdom 환경에서 PointerEvents / scroll API 를 요구한다 (AGENTS.md §5.1.2)
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

function makeItem(overrides: Partial<ReviewItemSummary> = {}): ReviewItemSummary {
  return {
    summaryId: `sum-${Math.random().toString(36).slice(2, 8)}`,
    title: "샘플 뉴스",
    score: 0.8,
    eventType: "policy_update",
    ...overrides,
  };
}

/**
 * 결정적 샘플러 — 테스트에서 어떤 항목이 뽑히는지 고정하기 위해
 * `items.slice(0, size)` 를 반환한다.
 */
function headSampler(items: ReviewItemSummary[], size: number) {
  return items.slice(0, size);
}

describe("SpotCheckDialog", () => {
  it("items 가 5건 이상이면 5 샘플을 렌더한다", () => {
    const items: ReviewItemSummary[] = Array.from({ length: 12 }, (_, i) =>
      makeItem({ summaryId: `s-${i}`, title: `뉴스 ${i}`, score: 0.7 + i * 0.01 }),
    );

    render(
      <SpotCheckDialog
        open
        items={items}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        sample={headSampler}
      />,
    );

    // 정확히 5개의 샘플 카드 (지정된 sampler 는 앞 5개)
    const spotItems = screen.getAllByTestId("spot-check-item");
    expect(spotItems).toHaveLength(5);

    // 첫 샘플의 타이틀이 뉴스 0 (headSampler 이므로 결정적)
    expect(screen.getByText("뉴스 0")).toBeInTheDocument();
    expect(screen.getByText("뉴스 4")).toBeInTheDocument();

    // 다이얼로그 제목에 "스팟체크" 포함
    expect(screen.getByRole("heading", { name: /스팟체크/ })).toBeInTheDocument();
  });

  it("5개 샘플 모두 OK 를 누르면 '전체 승인' 이 활성화되고 onConfirm 이 호출된다", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const items: ReviewItemSummary[] = Array.from({ length: 5 }, (_, i) =>
      makeItem({ summaryId: `s-${i}`, title: `뉴스 ${i}` }),
    );

    render(
      <SpotCheckDialog
        open
        items={items}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
        sample={headSampler}
      />,
    );

    const confirmBtn = screen.getByTestId("spot-check-confirm");
    // 초기 상태 — 모두 pending → disabled
    expect(confirmBtn).toBeDisabled();

    // 5개 OK 클릭 — 각 샘플의 OK 버튼은 testid=spot-check-ok
    const okButtons = screen.getAllByTestId("spot-check-ok");
    expect(okButtons).toHaveLength(5);
    for (const btn of okButtons) {
      await user.click(btn);
    }

    // 활성화 후 클릭
    expect(confirmBtn).toBeEnabled();
    await user.click(confirmBtn);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("1건이라도 NG 를 누르면 '전체 승인' 이 disabled 이고 경고 배너가 노출된다", async () => {
    const user = userEvent.setup();
    const items: ReviewItemSummary[] = Array.from({ length: 5 }, (_, i) =>
      makeItem({ summaryId: `s-${i}`, title: `뉴스 ${i}` }),
    );

    render(
      <SpotCheckDialog
        open
        items={items}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        sample={headSampler}
      />,
    );

    // 먼저 4건 OK
    const okButtons = screen.getAllByTestId("spot-check-ok");
    for (let i = 0; i < 4; i++) {
      await user.click(okButtons[i]);
    }

    // 마지막 1건 NG
    const ngButtons = screen.getAllByTestId("spot-check-ng");
    await user.click(ngButtons[4]);

    // 경고 배너 + "전체 승인" disabled
    expect(screen.getByTestId("spot-check-ng-warning")).toBeInTheDocument();
    expect(screen.getByText(/품질 문제 발견/)).toBeInTheDocument();
    expect(screen.getByTestId("spot-check-confirm")).toBeDisabled();
  });
});
