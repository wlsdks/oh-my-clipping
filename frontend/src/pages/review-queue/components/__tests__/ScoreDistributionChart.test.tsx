import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeAll, vi } from "vitest";
import type { ScoreDistribution } from "@/types/reviewPolicy";
import { ScoreDistributionChart } from "../ScoreDistributionChart";

// recharts 는 ResponsiveContainer 내부에서 부모 크기를 측정한다.
// jsdom 에서 layout 이 0 이면 BarChart 가 렌더되지 않으므로 ResizeObserver/크기 측정을 스텁한다.
beforeAll(() => {
  class RO {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  global.ResizeObserver = (global.ResizeObserver ?? RO) as typeof ResizeObserver;

  // ResponsiveContainer 가 부모의 clientWidth/Height 를 참조한다.
  Object.defineProperty(HTMLElement.prototype, "offsetWidth", {
    configurable: true,
    value: 600,
  });
  Object.defineProperty(HTMLElement.prototype, "offsetHeight", {
    configurable: true,
    value: 200,
  });
  // SVG text measurement
  // @ts-expect-error — SVGElement getBBox 가 jsdom 에 없다.
  window.SVGElement.prototype.getBBox = vi.fn(() => ({ x: 0, y: 0, width: 0, height: 0 }));
});

function buildBuckets(counts: number[]): ScoreDistribution["buckets"] {
  return counts.map((count, i) => {
    const low = (i / 10).toFixed(1);
    const high = ((i + 1) / 10).toFixed(1);
    return { range: `${low}-${high}`, count };
  });
}

const nonEmptyDistribution: ScoreDistribution = {
  buckets: buildBuckets([0, 1, 3, 5, 7, 12, 9, 4, 2, 1]),
  totalCount: 44,
  meanScore: 0.47,
  medianScore: 0.52,
};

const emptyDistribution: ScoreDistribution = {
  buckets: buildBuckets([0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
  totalCount: 0,
  meanScore: 0,
  medianScore: 0,
};

describe("ScoreDistributionChart", () => {
  it("평균/중앙값/총 건수를 요약 라인에 노출한다", () => {
    render(<ScoreDistributionChart distribution={nonEmptyDistribution} threshold={null} />);

    expect(screen.getByText("0.47")).toBeInTheDocument();
    expect(screen.getByText("0.52")).toBeInTheDocument();
    expect(screen.getByText("44")).toBeInTheDocument();
  });

  it("totalCount=0 이면 EmptyState 를 노출한다", () => {
    render(<ScoreDistributionChart distribution={emptyDistribution} threshold={0.5} />);

    expect(screen.getByText("데이터가 없습니다")).toBeInTheDocument();
    // 데이터가 없을 때는 요약 숫자(평균 0.00 같은)를 굳이 노출하지 않는다.
    expect(screen.queryByText("평균")).not.toBeInTheDocument();
  });

  it("threshold 가 buckets 범위 안에 있으면 data-testid=\"threshold-line\" 요소가 렌더된다", () => {
    const { container } = render(
      <ScoreDistributionChart distribution={nonEmptyDistribution} threshold={0.55} />,
    );

    // recharts ReferenceLine 은 props 를 SVG DOM 에 전파한다.
    const line = container.querySelector('[data-testid="threshold-line"]');
    expect(line).toBeTruthy();
  });

  it("threshold=null 이면 ReferenceLine 을 렌더하지 않는다", () => {
    const { container } = render(
      <ScoreDistributionChart distribution={nonEmptyDistribution} threshold={null} />,
    );

    expect(container.querySelector('[data-testid="threshold-line"]')).toBeNull();
  });
});
