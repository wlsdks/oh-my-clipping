// frontend/src/pages/source-quality/components/__tests__/SourceQualityKpiCards.test.tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SourceQualityKpiCards } from "../SourceQualityKpiCards";
import type { SourceQualityRow } from "@/types/sourceQuality";

// Mock row helper — 기본값은 statusLabel="normal" + clickRate 10%
const row = (overrides: Partial<SourceQualityRow> = {}): SourceQualityRow => ({
  sourceId: "s1",
  sourceName: "Test Source",
  delivered: 100,
  uniqueUserClicks: 10,
  clickRatePct: 10.0,
  likes: 1,
  dislikes: 0,
  likeRatePct: 1.0,
  statusLabel: "normal",
  isActive: true,
  updatedAt: "2026-04-20T00:00:00Z",
  ...overrides,
});

describe("SourceQualityKpiCards", () => {
  it("검토 필요 + 신호 부족 + 평균 클릭률 + 총 발송 계산", () => {
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", statusLabel: "normal", clickRatePct: 15, delivered: 100 }),
      row({ sourceId: "s2", statusLabel: "review", clickRatePct: 3, delivered: 50 }),
      row({ sourceId: "s3", statusLabel: "default", clickRatePct: null, delivered: 8 }),
    ];
    render(<SourceQualityKpiCards rows={rows} period="28d" />);

    // 검토 필요: review 1개
    const reviewCard = screen.getByText(/검토 필요/).closest('[data-testid="kpi-card-review"]');
    expect(reviewCard).toHaveTextContent("1");

    // 신호 부족: default 1개
    const defaultCard = screen.getByText(/신호 부족/).closest('[data-testid="kpi-card-default"]');
    expect(defaultCard).toHaveTextContent("1");

    // 평균 클릭률: (15*100 + 3*50) / (100+50) = 11.0%
    expect(screen.getByText(/11\.0/)).toBeInTheDocument();

    // 총 발송: 100+50+8 = 158
    expect(screen.getByText(/158/)).toBeInTheDocument();
  });

  it("rows 빈 배열 → 모든 카운트 0, 평균 — 표시", () => {
    render(<SourceQualityKpiCards rows={[]} period="28d" />);

    // 평균 클릭률 fallback — 적어도 하나의 em-dash 표시
    const dashes = screen.getAllByText("—");
    expect(dashes.length).toBeGreaterThanOrEqual(1);

    // 검토 필요, 신호 부족, 총 발송 모두 0
    const reviewCard = screen.getByText(/검토 필요/).closest('[data-testid="kpi-card-review"]');
    expect(reviewCard).toHaveTextContent("0");
    const defaultCard = screen.getByText(/신호 부족/).closest('[data-testid="kpi-card-default"]');
    expect(defaultCard).toHaveTextContent("0");
  });

  it("모든 행의 delivered = 0 → 평균 null fallback", () => {
    const rows = [row({ clickRatePct: 10, delivered: 0 })];
    render(<SourceQualityKpiCards rows={rows} period="28d" />);
    // 평균 클릭률 카드에서 — fallback 확인
    const avgCard = screen.getByText(/평균 클릭률/).closest('[data-testid="kpi-card-avg-click-rate"]');
    expect(avgCard).toHaveTextContent("—");
  });

  it("null clickRate 행은 평균 weighted 계산에서 제외", () => {
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", clickRatePct: 10, delivered: 100 }),
      row({ sourceId: "s2", clickRatePct: null, delivered: 50 }), // 제외됨
    ];
    render(<SourceQualityKpiCards rows={rows} period="28d" />);
    // 평균 = 10.0 (100만 반영, 50은 null 로 제외)
    expect(screen.getByText(/10\.0/)).toBeInTheDocument();
    // 총 발송은 여전히 150
    expect(screen.getByText(/150/)).toBeInTheDocument();
  });

  it("최상위 섹션에 data-testid='source-quality-kpi-cards' 부여", () => {
    render(<SourceQualityKpiCards rows={[]} period="28d" />);
    expect(screen.getByTestId("source-quality-kpi-cards")).toBeInTheDocument();
  });
});
