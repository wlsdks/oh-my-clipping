import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ComingSoonPage } from "../ComingSoonPage";

describe("ComingSoonPage", () => {
  it("label을 렌더링한다", () => {
    render(<ComingSoonPage label="뉴스 리포트" />);
    expect(screen.getByText("뉴스 리포트")).toBeInTheDocument();
  });

  it("준비 중 메시지를 렌더링한다", () => {
    render(<ComingSoonPage label="테스트" />);
    expect(screen.getByText("준비 중이에요")).toBeInTheDocument();
  });

  it("빈 label도 렌더링한다 (준비 중 메시지는 항상 표시)", () => {
    render(<ComingSoonPage label="" />);
    expect(screen.getByText("준비 중이에요")).toBeInTheDocument();
  });
});
