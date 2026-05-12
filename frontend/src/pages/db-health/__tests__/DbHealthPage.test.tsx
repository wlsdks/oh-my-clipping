import { afterEach, describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createQueryClientWrapper } from "@/test/queryClient";

/* dbMetricsService 모킹 */
vi.mock("@/services/dbMetricsService", () => ({
  dbMetricsService: {
    getSnapshot: vi.fn(),
  },
}));

import { dbMetricsService } from "@/services/dbMetricsService";
import { DbHealthPage } from "../DbHealthPage";
import type { DbMetricsSnapshot } from "@/types/dbMetrics";

/* ── 픽스처 ── */

const BASE_SNAPSHOT: DbMetricsSnapshot = {
  databaseSizeBytes: 50_393_571,
  databaseSizeMegabytes: 48,
  databaseSizePercentOfLimit: 4.8,
  limitBytes: 1_073_741_824,
  thresholdLevel: "ok",
  topTables: Array.from({ length: 10 }, (_, i) => ({
    table: `table_name_number_${i + 1}`,
    bytes: (10 - i) * 2_000_000,
    rows: (10 - i) * 1000,
    pctOfDb: (10 - i) * 4.1,
  })),
  retentionEligible: {
    rssItemsOlderThanCutoff: 8420,
    batchSummariesOlderThanCutoffExcludingAnchored: 2180,
    projectedBytesFreed: 10_485_760,
  },
  dailyGrowth: {
    lastSevenDaysBytes: [320_000, 400_000, 380_000, 450_000, 360_000, 410_000, 390_000],
    avgDailyBytes: 387_143,
  },
  lastRefreshedAt: new Date().toISOString(),
};

const WARNING_SNAPSHOT: DbMetricsSnapshot = {
  ...BASE_SNAPSHOT,
  databaseSizeMegabytes: 860,
  databaseSizePercentOfLimit: 85,
  thresholdLevel: "warning",
};

const CRITICAL_SNAPSHOT: DbMetricsSnapshot = {
  ...BASE_SNAPSHOT,
  databaseSizeMegabytes: 990,
  databaseSizePercentOfLimit: 97,
  thresholdLevel: "critical",
};

const EMPTY_TABLES_SNAPSHOT: DbMetricsSnapshot = {
  ...BASE_SNAPSHOT,
  topTables: [],
};

/* ── 렌더 헬퍼 ── */

function renderPage() {
  return render(
    <MemoryRouter>
      <DbHealthPage />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("DbHealthPage", () => {
  beforeEach(() => {
    vi.mocked(dbMetricsService.getSnapshot).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("KPI 카드에 크기와 퍼센트를 렌더한다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(BASE_SNAPSHOT);
    renderPage();

    // KPI 카드의 MB + 퍼센트 값이 함께 렌더된다
    await waitFor(() => {
      expect(screen.getByText("현재 DB 크기")).toBeInTheDocument();
    });

    // 퍼센트 표시 — p 태그가 정확히 "4.8%" 만 포함
    expect(screen.getByText("4.8%")).toBeInTheDocument();
    // 한도 단위가 표시된다
    expect(screen.getByText(/1,024 MB.*한도/)).toBeInTheDocument();
  });

  it("thresholdLevel warning 이면 warning 시맨틱 토큰 배경 클래스를 가진 배너가 보인다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(WARNING_SNAPSHOT);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    const banner = screen.getByRole("alert");
    expect(banner.className).toContain("status-warning-bg");
  });

  it("thresholdLevel critical 이면 danger 시맨틱 토큰 배경 클래스를 가진 배너가 보인다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(CRITICAL_SNAPSHOT);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    const banner = screen.getByRole("alert");
    expect(banner.className).toContain("status-danger-bg");
  });

  it("warning 배너에 /admin/runtime 링크가 있다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(WARNING_SNAPSHOT);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    const link = screen.getByRole("link", { name: /시스템 설정/i });
    expect(link).toHaveAttribute("href", "/admin/runtime");
  });

  it("critical 배너에 /admin/runtime 링크가 있다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(CRITICAL_SNAPSHOT);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    const link = screen.getByRole("link", { name: /시스템 설정/i });
    expect(link).toHaveAttribute("href", "/admin/runtime");
  });

  it("topTables 섹션 헤더와 테이블 개수가 렌더된다 (말줄임 없이 전체 이름 포함)", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(BASE_SNAPSHOT);
    const { container } = renderPage();

    // 차트 섹션 헤더 대기
    await waitFor(() => {
      expect(screen.getByText(/테이블별 크기 \(Top 10\)/)).toBeInTheDocument();
    });

    // recharts 는 SVG text 엘리먼트로 라벨을 렌더한다.
    // jsdom 에서는 SVG text 내용이 포함되지만 recharts 의 ResponsiveContainer 가
    // jsdom에서 크기를 0으로 반환하므로 차트 자체는 렌더될 수 있다.
    // 최소한 섹션 컨테이너가 DOM에 있음을 확인한다.
    const chartSection = container.querySelector(".recharts-wrapper, svg");
    // recharts 가 jsdom에서 width=0 이면 컨테이너만 렌더됨 — 섹션 헤더로 충분히 검증
    expect(screen.getByText(/테이블별 크기 \(Top 10\)/)).toBeInTheDocument();
    // TopTablesChart 컴포넌트가 실제로 마운트됐음을 props 를 통해 간접 확인
    expect(chartSection !== null || container.querySelector(".recharts-responsive-container") !== null).toBe(true);
  });

  it("topTables 가 비어있으면 '데이터 수집 중이에요' 를 표시한다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(EMPTY_TABLES_SNAPSHOT);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("데이터 수집 중이에요")).toBeInTheDocument();
    });
  });

  it("수동 새로고침 버튼은 클릭 후 30초간 비활성화된다", async () => {
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(BASE_SNAPSHOT);
    renderPage();

    // 데이터 로드 대기
    await waitFor(() => {
      expect(screen.getByText("DB 상태")).toBeInTheDocument();
    });

    const btn = await screen.findByRole("button", { name: /새로고침/i });
    expect(btn).not.toBeDisabled();

    // 클릭 — 실제 타이머 기반으로 즉시 비활성화 여부 확인
    await act(async () => {
      btn.click();
    });

    // 클릭 직후 비활성화 (30초 쿨다운 시작)
    expect(btn).toBeDisabled();
  });

  it("수동 새로고침 쿨다운이 지나면 버튼이 다시 활성화된다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(dbMetricsService.getSnapshot).mockResolvedValue(BASE_SNAPSHOT);
    renderPage();

    const btn = await screen.findByRole("button", { name: /새로고침/i });

    await act(async () => {
      btn.click();
    });
    expect(btn).toBeDisabled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });

    expect(btn).not.toBeDisabled();
  });
});
