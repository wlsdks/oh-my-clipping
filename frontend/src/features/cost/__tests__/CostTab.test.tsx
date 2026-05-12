import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { CostTab } from "../CostTab";
import { costService } from "@/services/costService";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { CostDetail } from "@/types/cost";

vi.mock("@/services/costService", () => ({
  costService: {
    getDetail: vi.fn(),
    getBudget: vi.fn(),
  },
}));

const mockCostService = vi.mocked(costService);

function renderCostTab() {
  return render(<CostTab categoryId="cat-tech" from="2026-05-01" to="2026-05-31" days={31} />, {
    wrapper: createQueryClientWrapper(),
  });
}

describe("CostTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCostService.getBudget.mockResolvedValue({
      monthlyBudgetUsd: 10,
      alertThresholdPercent: 80,
      slackAlertEnabled: true,
    });
  });

  it("기간과 카테고리 필터로 비용 상세 API를 조회하고 합계를 렌더링해야 한다", async () => {
    const detail: CostDetail = {
      from: "2026-05-01",
      to: "2026-05-31",
      inputCostPerMillionUsd: 0.3,
      outputCostPerMillionUsd: 2.5,
      rows: [
        {
          channelId: "C0123456789",
          categoryId: "cat-tech",
          categoryName: "기술",
          requestCount: 3,
          tokensIn: 1000,
          tokensOut: 200,
          estimatedUsd: 0.0008,
          costPercent: 40,
        },
        {
          channelId: "C0123456789",
          categoryId: "cat-tech-news",
          categoryName: "테크 뉴스",
          requestCount: 2,
          tokensIn: 500,
          tokensOut: 100,
          estimatedUsd: 0.00045,
          costPercent: 60,
        },
      ],
    };
    mockCostService.getDetail.mockResolvedValue(detail);

    renderCostTab();

    await waitFor(() => {
      expect(mockCostService.getDetail).toHaveBeenCalledWith("2026-05-01", "2026-05-31", "cat-tech");
    });
    expect(await screen.findByText("$0.00")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("1,500")).toBeInTheDocument();
    expect(screen.getByText("300")).toBeInTheDocument();
    expect(screen.getByText("기술")).toBeInTheDocument();
    expect(screen.getByText("테크 뉴스")).toBeInTheDocument();
  });
});
