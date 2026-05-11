import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MetaDot } from "@/components/shared/MetaDot";

describe("MetaDot", () => {
  it("3개 항목을 모두 렌더링하고 사이에 구분자가 2개 삽입된다", () => {
    render(<MetaDot items={["#channel", "페르소나", "국내"]} />);
    expect(screen.getByText("#channel")).toBeInTheDocument();
    expect(screen.getByText("페르소나")).toBeInTheDocument();
    expect(screen.getByText("국내")).toBeInTheDocument();
    expect(screen.getAllByText("·")).toHaveLength(2);
  });

  it("null/undefined/빈문자열이 섞여 있으면 필터링한다", () => {
    render(<MetaDot items={["A", null, undefined, "", "B"]} />);
    expect(screen.getByText("A")).toBeInTheDocument();
    expect(screen.getByText("B")).toBeInTheDocument();
    // A와 B만 남으므로 구분자는 1개
    expect(screen.getAllByText("·")).toHaveLength(1);
  });

  it("모든 항목이 null/빈이면 null을 반환한다 (아무것도 렌더 안됨)", () => {
    const { container } = render(<MetaDot items={[null, undefined, ""]} />);
    expect(container.firstChild).toBeNull();
  });

  it("단일 항목일 때 구분자가 없다", () => {
    render(<MetaDot items={["혼자"]} />);
    expect(screen.getByText("혼자")).toBeInTheDocument();
    expect(screen.queryByText("·")).not.toBeInTheDocument();
  });

  it("separator prop으로 구분자를 변경할 수 있다", () => {
    render(<MetaDot items={["A", "B"]} separator="|" />);
    expect(screen.getByText("|")).toBeInTheDocument();
  });

  it("itemMaxLength 초과 시 항목이 말줄임 처리된다", () => {
    render(<MetaDot items={["매우매우매우매우긴텍스트"]} itemMaxLength={5} />);
    expect(screen.getByText("매우매우매…")).toBeInTheDocument();
  });
});
