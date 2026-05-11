import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { LiveTotalsCards } from "@/components/shared/LiveTotalsCards";
import type { TotalsCard } from "@/types/personaAnalytics";

const baseTotals: TotalsCard = {
  totalStyles: 18,
  presetCount: 12,
  customCount: 6,
  activeSubscriptions: 247,
  presetUsageRate: 0.78,
  customStyleRatio: 0.33,
  weekOverWeekDelta: null,
};

describe("LiveTotalsCards", () => {
  it("4 개 카드를 렌더링한다", () => {
    render(<LiveTotalsCards totals={baseTotals} />);
    expect(screen.getByText("18개")).toBeInTheDocument();
    expect(screen.getByText("247건")).toBeInTheDocument();
    expect(screen.getByText("78%")).toBeInTheDocument();
    expect(screen.getByText("33%")).toBeInTheDocument();
  });

  it("프리셋/커스텀 카운트 sub 표시", () => {
    render(<LiveTotalsCards totals={baseTotals} />);
    expect(screen.getByText(/템플릿 12.*커스텀 6/)).toBeInTheDocument();
  });

  it("weekOverWeekDelta 양수면 상승 표시", () => {
    render(<LiveTotalsCards totals={{ ...baseTotals, weekOverWeekDelta: 12 }} />);
    expect(screen.getByText(/▲ 12/)).toBeInTheDocument();
  });

  it("weekOverWeekDelta 음수면 하락 표시", () => {
    render(<LiveTotalsCards totals={{ ...baseTotals, weekOverWeekDelta: -5 }} />);
    expect(screen.getByText(/▼ 5/)).toBeInTheDocument();
  });

  it("weekOverWeekDelta null 일 때 delta 행 미표시", () => {
    render(<LiveTotalsCards totals={baseTotals} />);
    expect(screen.queryByText(/전주 대비/)).not.toBeInTheDocument();
  });
});
