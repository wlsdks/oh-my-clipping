import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { SystemStatusResponse } from "@/types/systemStatus";
import { createQueryClientWrapper } from "@/test/queryClient";

// Mock all Section-level hooks so we test page composition only
vi.mock("../hooks/useActionRequiredData");
vi.mock("../hooks/usePendingTasksData");
vi.mock("../hooks/useOpsMetricsData");
vi.mock("../hooks/useOperatorFooterData");
vi.mock("@/services/systemStatusService", () => ({
  systemStatusService: {
    getStatus: vi.fn(),
  },
}));

import { useActionRequiredData } from "../hooks/useActionRequiredData";
import { usePendingTasksData } from "../hooks/usePendingTasksData";
import { useOpsMetricsData } from "../hooks/useOpsMetricsData";
import { useOperatorFooterData } from "../hooks/useOperatorFooterData";
import { AdminDashboardPage } from "../AdminDashboardPage";
import { systemStatusService } from "@/services/systemStatusService";

const mockActionRequired = vi.mocked(useActionRequiredData);
const mockPendingTasks = vi.mocked(usePendingTasksData);
const mockOpsMetrics = vi.mocked(useOpsMetricsData);
const mockOperatorFooter = vi.mocked(useOperatorFooterData);
const mockSystemStatusService = vi.mocked(systemStatusService);

const okSystemStatus: SystemStatusResponse = {
  server: {
    uptime: "1d",
    javaVersion: "21.0.2",
    activeProfiles: ["test"],
    memoryUsedMb: 128,
    memoryMaxMb: 512,
  },
  database: {
    connected: true,
    poolActive: 1,
    poolIdle: 4,
    poolTotal: 5,
  },
  slack: {
    botTokenConfigured: true,
    defaultChannelId: "C123",
    healthy: true,
    lastCheckTime: "2026-05-13T00:00:00Z",
  },
  ai: {
    circuitBreakerState: "CLOSED",
    canCall: true,
    consecutiveOpenCount: 0,
    totalOpenCount: 0,
    lastOpenedAt: null,
  },
  jobQueue: {
    pendingJobs: 0,
    threshold: 100,
  },
  schedulers: [],
};

function makeEmptyActionRequired() {
  return { items: [], isLoading: false, error: null, refetch: vi.fn() };
}

function makeEmptyPendingTasks() {
  return {
    userAccounts: { count: 0, urgencyPreview: "" },
    clippingRequests: { count: 0, urgencyPreview: "" },
    reviewItems: { count: 0, urgencyPreview: "" },
    isLoading: false,
    error: null,
  };
}

function makeEmptyOpsMetrics() {
  return {
    forecast: undefined,
    pipelineSummary: { content: [], totalCount: 0, page: 0, size: 100 },
    deliverySummary: { content: [], totalCount: 0, page: 0, size: 100 },
    engagement: undefined,
    opsSummary: {
      delivery: { total: 0, sent: 0, failed: 0 },
      pipeline: { total: 0, success: 0, failed: 0 },
    },
    isLoading: false,
    error: null,
  };
}

function makeEmptyOperatorFooter() {
  return {
    activeSubscriptions: undefined,
    showGettingStarted: false,
    isLoading: false,
    error: null,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminDashboardPage />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("AdminDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockActionRequired.mockReturnValue(makeEmptyActionRequired());
    mockPendingTasks.mockReturnValue(makeEmptyPendingTasks());
    mockOpsMetrics.mockReturnValue(makeEmptyOpsMetrics());
    mockOperatorFooter.mockReturnValue(makeEmptyOperatorFooter());
    mockSystemStatusService.getStatus.mockResolvedValue(okSystemStatus);
  });

  it("홈은 4 Section 을 렌더링", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId("pending-tasks-section")).toBeInTheDocument();
      expect(screen.getByTestId("ops-metrics-section")).toBeInTheDocument();
      expect(screen.getByTestId("operator-footer")).toBeInTheDocument();
    });
  });

  it("시스템 상태 배지와 오늘 날짜 헤더 렌더", () => {
    renderPage();
    expect(screen.getByRole("heading", { name: "홈" })).toBeInTheDocument();
  });

  it("실패 0건 상태에서 ActionRequiredSection 은 DOM 에 없음", async () => {
    mockActionRequired.mockReturnValue(makeEmptyActionRequired());
    renderPage();
    await waitFor(() => expect(screen.getByTestId("pending-tasks-section")).toBeInTheDocument());
    expect(screen.queryByTestId("action-required-section")).not.toBeInTheDocument();
  });
});
