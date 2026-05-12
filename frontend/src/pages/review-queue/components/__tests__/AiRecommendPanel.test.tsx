import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

import { AiRecommendPanel } from "../AiRecommendPanel";
import type { ReviewItemSummary } from "../types";

function makeItems(count: number): ReviewItemSummary[] {
  return Array.from({ length: count }, (_, i) => ({
    summaryId: `s-${i}`,
    title: `뉴스 ${i}`,
    score: 0.7 + (i % 10) * 0.01,
    eventType: "policy_update",
  }));
}

describe("AiRecommendPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("12건 필터 → '12개 항목' + 상위 10건 미리보기 + '외 2건' 표기", () => {
    render(
      <AiRecommendPanel filteredItems={makeItems(12)} onBulkApprove={vi.fn()} />,
    );

    expect(screen.getByTestId("ai-recommend-count")).toHaveTextContent(
      "12개 항목이 승인 후보",
    );

    // 상위 10건만 미리보기에 노출
    const previewItems = screen.getAllByTestId("ai-recommend-preview-item");
    expect(previewItems).toHaveLength(10);

    // 외 2건 텍스트
    expect(screen.getByText(/외 2건/)).toBeInTheDocument();
  });

  it("30건 필터 → '승인' 클릭 시 간단 확인 다이얼로그만 뜨고 스팟체크는 없다", async () => {
    const user = userEvent.setup();
    render(
      <AiRecommendPanel filteredItems={makeItems(30)} onBulkApprove={vi.fn()} />,
    );

    await user.click(screen.getByTestId("ai-recommend-approve"));

    // 간단 확인 다이얼로그가 열림
    expect(
      await screen.findByTestId("ai-recommend-simple-confirm"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /30건을 일괄 승인할까요/ }),
    ).toBeInTheDocument();

    // 스팟체크 다이얼로그는 열리지 않아야 함
    expect(screen.queryByTestId("spot-check-dialog")).not.toBeInTheDocument();
  });

  it("100건 필터 → '승인' 클릭 시 SpotCheckDialog 가 열린다", async () => {
    const user = userEvent.setup();
    render(
      <AiRecommendPanel filteredItems={makeItems(100)} onBulkApprove={vi.fn()} />,
    );

    await user.click(screen.getByTestId("ai-recommend-approve"));

    // 스팟체크 다이얼로그의 제목에 "스팟체크" 포함
    expect(
      await screen.findByRole("heading", { name: /스팟체크/ }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("spot-check-dialog")).toBeInTheDocument();

    // 간단 확인 다이얼로그는 열리지 않음
    expect(
      screen.queryByTestId("ai-recommend-simple-confirm"),
    ).not.toBeInTheDocument();
  });

  it("30건 간단 확인 → '확인' 클릭 시 onBulkApprove 에 전체 summaryId 배열이 전달된다", async () => {
    const user = userEvent.setup();
    const onBulkApprove = vi.fn().mockResolvedValue(undefined);
    const items = makeItems(30);
    render(<AiRecommendPanel filteredItems={items} onBulkApprove={onBulkApprove} />);

    await user.click(screen.getByTestId("ai-recommend-approve"));
    await screen.findByTestId("ai-recommend-simple-confirm");
    await user.click(screen.getByTestId("ai-recommend-simple-confirm-button"));

    await waitFor(() => {
      expect(onBulkApprove).toHaveBeenCalledTimes(1);
    });
    // 전체 ID 배열 (원본 순서 유지)
    expect(onBulkApprove).toHaveBeenCalledWith(items.map((i) => i.summaryId));
  });
});
