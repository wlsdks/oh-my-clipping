import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmojiWithText } from "@/components/shared/EmojiWithText";

describe("EmojiWithText", () => {
  it("emoji와 text를 함께 렌더링한다", () => {
    render(<EmojiWithText emoji="📰" text="뉴스" />);
    expect(screen.getByText("📰")).toBeInTheDocument();
    expect(screen.getByText("뉴스")).toBeInTheDocument();
  });

  it("emoji가 없으면 fallbackIcon을 렌더링한다", () => {
    render(
      <EmojiWithText
        emoji={null}
        text="카테고리"
        fallbackIcon={<svg data-testid="fallback" />}
      />,
    );
    expect(screen.getByTestId("fallback")).toBeInTheDocument();
    expect(screen.getByText("카테고리")).toBeInTheDocument();
  });

  it("emoji가 빈 문자열이면 fallbackIcon을 렌더링한다", () => {
    render(
      <EmojiWithText
        emoji=""
        text="라벨"
        fallbackIcon={<svg data-testid="fallback-empty" />}
      />,
    );
    expect(screen.getByTestId("fallback-empty")).toBeInTheDocument();
  });

  it("size 변형(sm/md/lg)에 따라 박스 크기 클래스가 바뀐다", () => {
    const { container, rerender } = render(<EmojiWithText emoji="🎯" text="small" size="sm" />);
    expect(container.querySelector(".h-6.w-6")).toBeInTheDocument();

    rerender(<EmojiWithText emoji="🎯" text="medium" size="md" />);
    expect(container.querySelector(".h-9.w-9")).toBeInTheDocument();

    rerender(<EmojiWithText emoji="🎯" text="large" size="lg" />);
    expect(container.querySelector(".h-12.w-12")).toBeInTheDocument();
  });

  it("lines=2일 때 텍스트에 line-clamp-2가 적용된다", () => {
    render(<EmojiWithText emoji="📦" text="두 줄 텍스트" lines={2} />);
    expect(screen.getByText("두 줄 텍스트").className).toContain("line-clamp-2");
  });
});
