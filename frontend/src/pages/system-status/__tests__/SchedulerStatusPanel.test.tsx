import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { createQueryClientWrapper } from "@/test/queryClient";

vi.mock("@/services/schedulerStatusService", () => ({
  schedulerStatusService: {
    list: vi.fn(),
  },
}));

import { schedulerStatusService } from "@/services/schedulerStatusService";
import { SchedulerStatusPanel } from "../SchedulerStatusPanel";
import type { SchedulerStatusItem } from "@/types/schedulerStatus";

function renderPanel() {
  return render(<SchedulerStatusPanel />, { wrapper: createQueryClientWrapper() });
}

describe("SchedulerStatusPanel", () => {
  beforeEach(() => {
    vi.mocked(schedulerStatusService.list).mockReset();
  });

  it("정상 데이터가 오면 테이블과 스케줄러 이름을 렌더한다", async () => {
    const mockData: SchedulerStatusItem[] = [
      {
        name: "AutoReportScheduler",
        trackerKey: "auto_report",
        schedule: "매시 정각",
        description: "주간/월간 자동 리포트 생성",
        lastRunAt: "2026-04-10T09:00:00Z",
        lastDurationMs: 450,
        lastResult: "success",
        lastError: null,
        nextRunAt: "2026-04-10T10:00:00Z",
        status: "IDLE",
        stalenessSeconds: 3600,
      },
    ];
    vi.mocked(schedulerStatusService.list).mockResolvedValue(mockData);

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("AutoReportScheduler")).toBeInTheDocument();
    });
    expect(screen.getByText("주간/월간 자동 리포트 생성")).toBeInTheDocument();
    expect(screen.getByText("매시 정각")).toBeInTheDocument();
  });

  it("FAILED 상태가 있으면 헤더에 실패 건수 뱃지를 표시한다", async () => {
    const mockData: SchedulerStatusItem[] = [
      {
        name: "DataCleanupScheduler",
        trackerKey: "data_cleanup",
        schedule: "매일 03:00",
        description: "오래된 데이터 정리",
        lastRunAt: "2026-04-10T03:00:00Z",
        lastDurationMs: 120,
        lastResult: "failure",
        lastError: "Database connection timed out",
        nextRunAt: "2026-04-11T03:00:00Z",
        status: "FAILED",
        stalenessSeconds: 7200,
      },
    ];
    vi.mocked(schedulerStatusService.list).mockResolvedValue(mockData);

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("실패 1건")).toBeInTheDocument();
    });
    expect(screen.getByText(/Database connection timed out/)).toBeInTheDocument();
  });

  it("lastRunAt이 null이면 대기 뱃지 표시", async () => {
    const mockData: SchedulerStatusItem[] = [
      {
        name: "NeverRanScheduler",
        trackerKey: "never",
        schedule: "매일 04:00",
        description: "아직 실행 안 됨",
        lastRunAt: null,
        lastDurationMs: null,
        lastResult: null,
        lastError: null,
        nextRunAt: "2026-04-11T04:00:00Z",
        status: "IDLE",
        stalenessSeconds: null,
      },
    ];
    vi.mocked(schedulerStatusService.list).mockResolvedValue(mockData);

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("NeverRanScheduler")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("미실행")).toBeInTheDocument();
  });

  it("데이터가 빈 배열이면 안내 메시지 표시", async () => {
    vi.mocked(schedulerStatusService.list).mockResolvedValue([]);

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("등록된 스케줄러가 없어요")).toBeInTheDocument();
    });
  });

  it("API 에러 시 재시도 안내 메시지 표시", async () => {
    vi.mocked(schedulerStatusService.list).mockRejectedValue(new Error("network"));

    renderPanel();

    await waitFor(() => {
      expect(
        screen.getByRole("alert"),
      ).toHaveTextContent(/스케줄러 상태를 불러오지 못했어요/);
    });
  });

  it("긴 에러 메시지는 140자에서 잘려 표시된다", async () => {
    const longError = "에러".repeat(200); // 약 400자
    const mockData: SchedulerStatusItem[] = [
      {
        name: "LongErrorScheduler",
        trackerKey: "long_error",
        schedule: "fixedDelay 1분",
        description: "긴 에러 테스트",
        lastRunAt: "2026-04-10T09:00:00Z",
        lastDurationMs: 50,
        lastResult: "failure",
        lastError: longError,
        nextRunAt: null,
        status: "FAILED",
        stalenessSeconds: 60,
      },
    ];
    vi.mocked(schedulerStatusService.list).mockResolvedValue(mockData);

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("LongErrorScheduler")).toBeInTheDocument();
    });
    // 말줄임표가 포함되어야 함
    const error = screen.getByText((content) => content.includes("…"));
    expect(error).toBeInTheDocument();
  });
});
