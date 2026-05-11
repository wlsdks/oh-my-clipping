import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";

import { UserEngagementCard } from "../UserEngagementCard";

function renderCard(props: Parameters<typeof UserEngagementCard>[0]) {
  return render(
    <MemoryRouter>
      <UserEngagementCard {...props} />
    </MemoryRouter>,
  );
}

describe("UserEngagementCard", () => {
  it("renders click rate and 7-day avg", () => {
    renderCard({ yesterdayClickRate: 55, sevenDayAvg: 50, stdDev: 2, positive: 10, negative: 3 });
    expect(screen.getByText("55%")).toBeInTheDocument();
    expect(screen.getByText(/7일 평균 50%/)).toBeInTheDocument();
  });

  it("shows trend-up icon when yesterday significantly above avg", () => {
    // delta = 10, stdDev = 2, zScore = 5 > 1 → up
    renderCard({ yesterdayClickRate: 60, sevenDayAvg: 50, stdDev: 2, positive: 5, negative: 1 });
    expect(screen.getByTestId("trend-up")).toBeInTheDocument();
  });

  it("shows trend-down when significantly below", () => {
    // delta = -10, stdDev = 2, zScore = 5 > 1 → down
    renderCard({ yesterdayClickRate: 40, sevenDayAvg: 50, stdDev: 2, positive: 1, negative: 9 });
    expect(screen.getByTestId("trend-down")).toBeInTheDocument();
  });

  it("shows trend-neutral within ±1σ", () => {
    // delta = 1, stdDev = 5, zScore = 0.2 ≤ 1 → neutral
    renderCard({ yesterdayClickRate: 51, sevenDayAvg: 50, stdDev: 5, positive: 4, negative: 4 });
    expect(screen.getByTestId("trend-neutral")).toBeInTheDocument();
  });

  it("renders feedback positive/negative counts", () => {
    renderCard({ yesterdayClickRate: 50, sevenDayAvg: 50, stdDev: 3, positive: 12, negative: 7 });
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });
});
