import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CardTitle } from "@/components/shared/CardTitle";

describe("CardTitle", () => {
  it("제목을 h3로 렌더링하고 truncate가 적용된다", () => {
    render(<CardTitle>카드 제목</CardTitle>);
    const title = screen.getByText("카드 제목");
    expect(title.tagName).toBe("H3");
    expect(title.className).toContain("truncate");
  });

  it("rightSlot을 우측에 렌더링한다", () => {
    render(
      <CardTitle rightSlot={<span data-testid="badge">활성</span>}>구독명</CardTitle>,
    );
    expect(screen.getByText("구독명")).toBeInTheDocument();
    expect(screen.getByTestId("badge")).toBeInTheDocument();
  });

  it("rightSlot이 없으면 슬롯 div가 렌더되지 않는다", () => {
    const { container } = render(<CardTitle>Only title</CardTitle>);
    // rightSlot은 마지막 자식이어야 하고, 없으면 flex 컨테이너에 자식이 1개
    const flex = container.firstChild as HTMLElement;
    expect(flex.children.length).toBe(1);
  });

  it("size=lg일 때 text-lg 클래스가 적용된다", () => {
    render(<CardTitle size="lg">큰 제목</CardTitle>);
    expect(screen.getByText("큰 제목").className).toContain("text-lg");
  });

  it("lines=2이면 line-clamp-2가 적용된다", () => {
    render(<CardTitle lines={2}>두 줄까지 보이는 제목</CardTitle>);
    expect(screen.getByText("두 줄까지 보이는 제목").className).toContain("line-clamp-2");
  });

  it("제목에 title 속성(전체 텍스트)이 hover용으로 설정된다", () => {
    render(<CardTitle>전체 텍스트</CardTitle>);
    expect(screen.getByText("전체 텍스트")).toHaveAttribute("title", "전체 텍스트");
  });
});
