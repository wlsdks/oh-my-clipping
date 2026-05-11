import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TruncatedText } from "@/components/shared/TruncatedText";

describe("TruncatedText", () => {
  it("기본적으로 text를 렌더링하고 truncate 클래스가 적용된다", () => {
    render(<TruncatedText>매우 긴 제목입니다</TruncatedText>);
    const el = screen.getByText("매우 긴 제목입니다");
    expect(el).toBeInTheDocument();
    expect(el.className).toContain("truncate");
  });

  it("lines=2/3이면 line-clamp 클래스가 적용된다", () => {
    const { rerender } = render(<TruncatedText lines={2}>두 줄 텍스트</TruncatedText>);
    expect(screen.getByText("두 줄 텍스트").className).toContain("line-clamp-2");

    rerender(<TruncatedText lines={3}>세 줄 텍스트</TruncatedText>);
    expect(screen.getByText("세 줄 텍스트").className).toContain("line-clamp-3");
  });

  it("showTitle=true이면 title 속성이 전체 텍스트로 설정된다", () => {
    render(<TruncatedText>hover 전체 텍스트</TruncatedText>);
    expect(screen.getByText("hover 전체 텍스트")).toHaveAttribute("title", "hover 전체 텍스트");
  });

  it("showTitle=false이면 title 속성이 없다", () => {
    render(
      <TruncatedText showTitle={false}>
        no title
      </TruncatedText>,
    );
    expect(screen.getByText("no title")).not.toHaveAttribute("title");
  });

  it("as prop으로 렌더 태그를 변경할 수 있다", () => {
    render(<TruncatedText as="h3">제목</TruncatedText>);
    const h3 = screen.getByText("제목");
    expect(h3.tagName).toBe("H3");
  });

  it("className이 기본 클래스와 병합된다", () => {
    render(<TruncatedText className="text-xl custom">텍스트</TruncatedText>);
    const el = screen.getByText("텍스트");
    expect(el.className).toContain("custom");
    expect(el.className).toContain("break-words");
  });
});
