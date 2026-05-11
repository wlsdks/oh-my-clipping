import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeAll } from "vitest";
import { ZeroSignalActivationWarning } from "../ZeroSignalActivationWarning";

// Radix Dialog 는 jsdom 환경에서 PointerEvents / scroll API 를 요구한다 (AGENTS.md §5.1.2)
beforeAll(() => {
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
  window.HTMLElement.prototype.scrollTo =
    window.HTMLElement.prototype.scrollTo || (() => {});
});

describe("ZeroSignalActivationWarning", () => {
  it("previewCount 가 주어지면 '지난 30일 N건 제외 예정' 문구를 보여준다", () => {
    render(
      <ZeroSignalActivationWarning
        open
        categoryId="cat-a"
        proposedIncludeKeywords={["반도체", "AI"]}
        previewCount={42}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    // 건수 노출 + 숫자 확인
    const preview = screen.getByTestId("zero-signal-preview-count");
    expect(preview).toHaveTextContent("지난 30일");
    expect(preview).toHaveTextContent("42");
    expect(preview).toHaveTextContent("제외될 예정입니다");

    // 키워드 칩 2개 — 공백/빈 문자열 없음
    const list = screen.getByTestId("zero-signal-keyword-list");
    expect(list).toHaveTextContent("반도체");
    expect(list).toHaveTextContent("AI");

    // previewCount 없을 때 관찰할 selector 는 일단 있어야 함 (assertion 이 아닌 sanity)
    expect(screen.getByTestId("zero-signal-confirm")).toBeInTheDocument();
  });

  it("previewCount 가 없으면 건수 문구를 숨긴다", () => {
    render(
      <ZeroSignalActivationWarning
        open
        categoryId="cat-a"
        proposedIncludeKeywords={["반도체"]}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    // 건수 문구 영역이 존재하지 않아야 함
    expect(screen.queryByTestId("zero-signal-preview-count")).not.toBeInTheDocument();
    // 경고 다이얼로그 자체는 여전히 렌더됨
    expect(screen.getByTestId("zero-signal-activation-warning")).toBeInTheDocument();
  });

  it("취소 버튼을 누르면 onCancel 만 호출된다", async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    const user = userEvent.setup();

    render(
      <ZeroSignalActivationWarning
        open
        categoryId="cat-a"
        proposedIncludeKeywords={["반도체"]}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    await user.click(screen.getByTestId("zero-signal-cancel"));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("'이해했습니다' 를 누르면 onConfirm 이 호출된다", async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    const user = userEvent.setup();

    render(
      <ZeroSignalActivationWarning
        open
        categoryId="cat-a"
        proposedIncludeKeywords={["반도체"]}
        previewCount={7}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    await user.click(screen.getByTestId("zero-signal-confirm"));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });
});
