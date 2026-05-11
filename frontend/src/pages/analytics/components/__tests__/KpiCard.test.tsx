import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { KpiCard } from "../KpiCard";

describe("KpiCard", () => {
  it("label과 value를 렌더링해야 한다", () => {
    render(<KpiCard label="평균 DAU" value="123명" />);
    expect(screen.getByText("평균 DAU")).toBeInTheDocument();
    expect(screen.getByText("123명")).toBeInTheDocument();
  });

  it("subtitle이 있으면 렌더링해야 한다", () => {
    render(<KpiCard label="DAU" value="10" subtitle="일평균 접속자" />);
    expect(screen.getByText("일평균 접속자")).toBeInTheDocument();
  });

  it("subtitle이 없으면 렌더링하지 않아야 한다", () => {
    render(<KpiCard label="DAU" value="10" />);
    expect(screen.queryByText("일평균 접속자")).not.toBeInTheDocument();
  });

  it("trend up 방향을 렌더링해야 한다", () => {
    render(
      <KpiCard
        label="DAU"
        value="10"
        trend={{ value: "+15%", direction: "up" }}
      />,
    );
    expect(screen.getByText("+15%")).toBeInTheDocument();
  });

  it("trend down 방향을 렌더링해야 한다", () => {
    render(
      <KpiCard
        label="DAU"
        value="10"
        trend={{ value: "-5%", direction: "down" }}
      />,
    );
    expect(screen.getByText("-5%")).toBeInTheDocument();
  });

  it("trend neutral 방향을 렌더링해야 한다", () => {
    render(
      <KpiCard
        label="DAU"
        value="10"
        trend={{ value: "0%", direction: "neutral" }}
      />,
    );
    expect(screen.getByText("0%")).toBeInTheDocument();
  });

  it("loading이면 스켈레톤을 렌더링해야 한다", () => {
    const { container } = render(<KpiCard label="DAU" value="10" loading />);
    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
    expect(screen.queryByText("DAU")).not.toBeInTheDocument();
  });
});
