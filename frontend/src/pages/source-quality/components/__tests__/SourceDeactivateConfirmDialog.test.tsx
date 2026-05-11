import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeAll } from "vitest";
import { SourceDeactivateConfirmDialog } from "../SourceDeactivateConfirmDialog";

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

const source = { sourceId: "src-123", sourceName: "Example Wire IT" };

describe("SourceDeactivateConfirmDialog", () => {
  it("소스명과 안내 문구를 노출한다", () => {
    render(
      <SourceDeactivateConfirmDialog
        open
        source={source}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    // 제목 ("수집 일시중지")
    expect(
      screen.getByRole("heading", { name: "수집 일시중지" }),
    ).toBeInTheDocument();

    // 소스명이 강조되어 표시됨
    expect(screen.getByText("Example Wire IT")).toBeInTheDocument();

    // 핵심 안내 문구들
    expect(
      screen.getByText(/더 이상 기사를 수집하지 않습니다/),
    ).toBeInTheDocument();
    expect(screen.getByText(/이미 수집된 기사는 유지됩니다/)).toBeInTheDocument();
    expect(
      screen.getByText(/재활성화 시 실패 카운트가 초기화됩니다/),
    ).toBeInTheDocument();

    // 두 개 버튼 존재
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "수집 일시중지" }),
    ).toBeInTheDocument();
  });

  it("'수집 일시중지' 클릭 시 onConfirm 이 1회 호출된다", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <SourceDeactivateConfirmDialog
        open
        source={source}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    await user.click(screen.getByRole("button", { name: "수집 일시중지" }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("'취소' 클릭 시 onCancel 이 1회 호출된다", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <SourceDeactivateConfirmDialog
        open
        source={source}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("ESC 키 입력 시 onCancel 이 1회 호출된다 (Radix 기본 동작)", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <SourceDeactivateConfirmDialog
        open
        source={source}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    await user.keyboard("{Escape}");

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
