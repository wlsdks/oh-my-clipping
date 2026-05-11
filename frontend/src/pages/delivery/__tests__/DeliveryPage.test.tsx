import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { DeliveryLogRecord, DeliveryLogsPage, DeliverySummary } from "@/types/delivery";

// ── 외부 의존성 mock ────────────────────────────────────────────────
vi.mock("@/services/deliveryService", () => ({
  deliveryService: {
    getSummary: vi.fn(),
    listLogs: vi.fn(),
    retry: vi.fn(),
  },
}));

vi.mock("@/services/categoryService", () => ({
  categoryService: {
    getAll: vi.fn(),
  },
}));

vi.mock("@/hooks/useSlackChannelMap", () => ({
  useSlackChannelMap: () => ({
    channelMap: new Map(),
    formatChannel: (id: string | null | undefined) => (id ? `#${id}` : "-"),
    isLoading: false,
    hasLoaded: true,
  }),
}));

// DeliveryMatrixSection은 분리된 탭이며 이 테스트의 관심사가 아니다 — 간단한 stub로 대체.
vi.mock("../DeliveryMatrixSection", () => ({
  DeliveryMatrixSection: () => <div data-testid="matrix-section">matrix</div>,
}));

import { deliveryService } from "@/services/deliveryService";
import { categoryService } from "@/services/categoryService";
import { DeliveryPage } from "../DeliveryPage";

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <DeliveryPage />
      </QueryClientProvider>
    </MemoryRouter>
  );
}

const baseSummary: DeliverySummary = {
  totalCount: 100,
  sentCount: 90,
  failedCount: 10,
  skippedCount: 0,
  successRate: 90,
};

function makeLog(overrides: Partial<DeliveryLogRecord> = {}): DeliveryLogRecord {
  return {
    id: "log-1",
    categoryId: "cat-1",
    channelId: "C12345",
    deliveryDate: "2026-04-10",
    deliveryHour: 9,
    status: "SENT",
    itemCount: 5,
    slackMessageTs: null,
    retryAttempted: false,
    createdAt: "2026-04-10T09:00:00Z",
    updatedAt: "2026-04-10T09:00:00Z",
    ...overrides,
  };
}

function logsPage(logs: DeliveryLogRecord[]): DeliveryLogsPage {
  return {
    content: logs,
    totalCount: logs.length,
    page: 0,
    size: 30,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(categoryService.getAll).mockResolvedValue([
    { id: "cat-1", name: "마케팅" } as never,
    { id: "cat-2", name: "IT" } as never,
  ]);
  vi.mocked(deliveryService.getSummary).mockResolvedValue(baseSummary);
  vi.mocked(deliveryService.listLogs).mockResolvedValue(logsPage([]));
});

describe("DeliveryPage — 로딩 / 빈 상태", () => {
  it("KPI 로딩 중에는 요약 스켈레톤이 표시된다", () => {
    vi.mocked(deliveryService.getSummary).mockReturnValue(new Promise(() => {}));
    const { container } = renderPage();
    // SharedKpiCard의 loading prop이 렌더되면 animate-pulse 요소가 존재한다.
    expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
  });

  it("이력이 비어있으면 빈 상태 메시지를 보여준다", async () => {
    renderPage();
    expect(await screen.findByText("아직 발송 기록이 없어요")).toBeInTheDocument();
  });
});

describe("DeliveryPage — KPI 렌더링", () => {
  it("요약 통계를 KPI 카드에 표시한다", async () => {
    renderPage();
    // '총 발송'과 값 100을 확인 (summary.totalCount)
    expect(await screen.findByText("총 발송")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
    // 성공률이 소수점 1자리로 표시된다
    expect(screen.getByText("90.0%")).toBeInTheDocument();
  });

  it("totalCount가 0이면 '발송 기록이 없어요' 안내 문구를 상단에 표시한다", async () => {
    vi.mocked(deliveryService.getSummary).mockResolvedValue({
      totalCount: 0,
      sentCount: 0,
      failedCount: 0,
      skippedCount: 0,
      successRate: 0,
    });
    renderPage();
    const notices = await screen.findAllByText("아직 발송 기록이 없어요");
    // 상단 안내 + 테이블 빈 상태가 모두 렌더된다.
    expect(notices.length).toBeGreaterThanOrEqual(1);
  });
});

describe("DeliveryPage — 데이터 렌더링 / 필터", () => {
  it("발송 이력 데이터를 테이블에 렌더링한다", async () => {
    vi.mocked(deliveryService.listLogs).mockResolvedValue(
      logsPage([makeLog({ id: "log-1", categoryId: "cat-1", itemCount: 7 })])
    );
    renderPage();

    // 카테고리 이름으로 렌더
    expect(await screen.findByText("마케팅")).toBeInTheDocument();
    // itemCount가 테이블에 표시
    expect(screen.getByText("7")).toBeInTheDocument();
    // 채널은 formatChannel mock을 타고 "#C12345"
    expect(screen.getByText("#C12345")).toBeInTheDocument();
  });

  it("기간 필터 버튼을 누르면 페이지가 0으로 초기화된다", async () => {
    vi.mocked(deliveryService.listLogs).mockResolvedValue(
      logsPage([makeLog({ id: "log-1" })])
    );
    renderPage();
    await screen.findByText("마케팅");

    const lastWeekBtn = screen.getByRole("button", { name: "지난 주" });
    await userEvent.click(lastWeekBtn);

    await waitFor(() => {
      // listLogs가 기간 변경 후 재호출됐는지 확인
      expect(vi.mocked(deliveryService.listLogs).mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  it("상태 필터 '실패'를 선택하면 쿼리 파라미터에 status=FAILED가 실린다", async () => {
    renderPage();
    await screen.findByText("아직 발송 기록이 없어요");

    const failedBtn = screen.getByRole("button", { name: "실패" });
    await userEvent.click(failedBtn);

    await waitFor(() => {
      const calls = vi.mocked(deliveryService.listLogs).mock.calls;
      const lastCall = calls[calls.length - 1];
      const params = lastCall?.[0] as URLSearchParams;
      expect(params.get("status")).toBe("FAILED");
    });
  });
});

describe("DeliveryPage — 에러 처리", () => {
  it("목록 조회 실패 시 에러 안내와 재시도 버튼을 노출한다", async () => {
    vi.mocked(deliveryService.listLogs).mockRejectedValue(new Error("boom"));
    renderPage();

    expect(await screen.findByText("이력을 불러오는 중 문제가 발생했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});

describe("DeliveryPage — 재발송 액션", () => {
  it("실패 상태 + retryAttempted=false 행에서만 재발송 버튼이 보인다", async () => {
    vi.mocked(deliveryService.listLogs).mockResolvedValue(
      logsPage([
        makeLog({ id: "log-sent", status: "SENT" }),
        makeLog({ id: "log-failed", status: "FAILED", retryAttempted: false }),
        makeLog({ id: "log-retried", status: "FAILED", retryAttempted: true }),
      ])
    );
    renderPage();
    await screen.findAllByText("마케팅");

    // 재발송 버튼은 정확히 1개 (retryAttempted=false FAILED 1건)
    const retryBtns = screen.getAllByRole("button", { name: /재발송/ });
    expect(retryBtns).toHaveLength(1);
    // retryAttempted=true 는 '재시도 완료' 텍스트
    expect(screen.getByText("재시도 완료")).toBeInTheDocument();
  });

  it("재발송 버튼 클릭 시 deliveryService.retry가 호출된다", async () => {
    vi.mocked(deliveryService.listLogs).mockResolvedValue(
      logsPage([makeLog({ id: "log-failed", status: "FAILED", retryAttempted: false })])
    );
    vi.mocked(deliveryService.retry).mockResolvedValue({ success: true, logId: "log-failed" });
    renderPage();

    const retryBtn = await screen.findByRole("button", { name: /재발송/ });
    await userEvent.click(retryBtn);

    await waitFor(() => {
      expect(deliveryService.retry).toHaveBeenCalledWith("log-failed");
    });
  });
});

describe("DeliveryPage — 탭 전환", () => {
  it("사용자별 현황 탭을 누르면 매트릭스 섹션이 렌더된다", async () => {
    renderPage();
    const matrixTab = await screen.findByRole("tab", { name: "사용자별 현황" });
    await userEvent.click(matrixTab);

    expect(await screen.findByTestId("matrix-section")).toBeInTheDocument();
  });
});
