import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { BackfillButton } from "../BackfillButton";
import { createQueryClientWrapper } from "@/test/queryClient";

// personaAnalyticsService 모킹
vi.mock("@/services/personaAnalyticsService", () => ({
  personaAnalyticsService: {
    runBackfill: vi.fn(),
    getLive: vi.fn(),
    getTrends: vi.fn(),
    getBatchRuns: vi.fn(),
  },
}));

// sonner toast 모킹
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { toast } from "sonner";

beforeEach(() => {
  vi.clearAllMocks();
});

describe("BackfillButton", () => {
  it("기본 텍스트로 렌더링된다", () => {
    render(<BackfillButton weeks={12} />, { wrapper: createQueryClientWrapper() });
    expect(screen.getByRole("button")).toHaveTextContent("지난 12주 데이터 집계");
  });

  it("weeks prop 이 다르면 해당 값을 표시한다", () => {
    render(<BackfillButton weeks={26} />, { wrapper: createQueryClientWrapper() });
    expect(screen.getByRole("button")).toHaveTextContent("지난 26주 데이터 집계");
  });

  it("클릭 시 runBackfill 을 호출한다", async () => {
    const user = userEvent.setup();
    vi.mocked(personaAnalyticsService.runBackfill).mockResolvedValue({
      weeksProcessed: 12,
      personasAggregated: 5,
      snapshotRowsCreated: 60,
      durationMs: 1200,
    });

    render(<BackfillButton weeks={12} />, { wrapper: createQueryClientWrapper() });
    await user.click(screen.getByRole("button"));

    await vi.waitFor(() => {
      expect(personaAnalyticsService.runBackfill).toHaveBeenCalledWith(12);
    });
  });

  it("성공 시 toast.success 가 호출된다", async () => {
    const user = userEvent.setup();
    vi.mocked(personaAnalyticsService.runBackfill).mockResolvedValue({
      weeksProcessed: 12,
      personasAggregated: 5,
      snapshotRowsCreated: 60,
      durationMs: 1200,
    });

    render(<BackfillButton weeks={12} />, { wrapper: createQueryClientWrapper() });
    await user.click(screen.getByRole("button"));

    // mutation resolve 후 확인
    await vi.waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(
        expect.stringContaining("과거 데이터 집계 완료"),
      );
    });
  });

  it("실패 시 toast.error 가 호출된다", async () => {
    const user = userEvent.setup();
    vi.mocked(personaAnalyticsService.runBackfill).mockRejectedValue(
      new Error("network error"),
    );

    render(<BackfillButton weeks={12} />, { wrapper: createQueryClientWrapper() });
    await user.click(screen.getByRole("button"));

    await vi.waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        expect.stringContaining("과거 데이터 집계에 실패"),
      );
    });
  });
});
