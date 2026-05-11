import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BackfillButton } from "../BackfillButton";

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

function createWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("BackfillButton", () => {
  it("기본 텍스트로 렌더링된다", () => {
    render(<BackfillButton weeks={12} />, { wrapper: createWrapper() });
    expect(screen.getByRole("button")).toHaveTextContent("지난 12주 데이터 집계");
  });

  it("weeks prop 이 다르면 해당 값을 표시한다", () => {
    render(<BackfillButton weeks={26} />, { wrapper: createWrapper() });
    expect(screen.getByRole("button")).toHaveTextContent("지난 26주 데이터 집계");
  });

  it("클릭 시 runBackfill 을 호출한다", async () => {
    vi.mocked(personaAnalyticsService.runBackfill).mockResolvedValue({
      weeksProcessed: 12,
      personasAggregated: 5,
      snapshotRowsCreated: 60,
      durationMs: 1200,
    });

    render(<BackfillButton weeks={12} />, { wrapper: createWrapper() });
    fireEvent.click(screen.getByRole("button"));

    await vi.waitFor(() => {
      expect(personaAnalyticsService.runBackfill).toHaveBeenCalledWith(12);
    });
  });

  it("성공 시 toast.success 가 호출된다", async () => {
    vi.mocked(personaAnalyticsService.runBackfill).mockResolvedValue({
      weeksProcessed: 12,
      personasAggregated: 5,
      snapshotRowsCreated: 60,
      durationMs: 1200,
    });

    render(<BackfillButton weeks={12} />, { wrapper: createWrapper() });
    fireEvent.click(screen.getByRole("button"));

    // mutation resolve 후 확인
    await vi.waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(
        expect.stringContaining("과거 데이터 집계 완료"),
      );
    });
  });

  it("실패 시 toast.error 가 호출된다", async () => {
    vi.mocked(personaAnalyticsService.runBackfill).mockRejectedValue(
      new Error("network error"),
    );

    render(<BackfillButton weeks={12} />, { wrapper: createWrapper() });
    fireEvent.click(screen.getByRole("button"));

    await vi.waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        expect.stringContaining("과거 데이터 집계에 실패"),
      );
    });
  });
});
