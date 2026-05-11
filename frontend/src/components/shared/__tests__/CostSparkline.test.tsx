import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { CostSparkline } from "../CostSparkline";

const sampleData = [
  { date: "04/10", value: 1.23 },
  { date: "04/11", value: 2.45 },
  { date: "04/12", value: 0.98 },
];

describe("CostSparkline", () => {
  it("renders recharts container for non-empty data", () => {
    const { container } = render(<CostSparkline data={sampleData} />);
    // ResponsiveContainer renders a div wrapper; empty state text is absent
    expect(container.firstChild).toBeTruthy();
    expect(screen.queryByText("데이터 없음")).toBeNull();
  });

  it("renders empty-state text when data is empty", () => {
    render(<CostSparkline data={[]} />);
    expect(screen.getByText("데이터 없음")).toBeInTheDocument();
  });
});
