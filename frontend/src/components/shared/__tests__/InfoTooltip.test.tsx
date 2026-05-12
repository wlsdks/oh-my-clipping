import { describe, it, expect } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { InfoTooltip } from "@/components/shared/InfoTooltip";

describe("InfoTooltip", () => {
  it("기본 aria-label로 Info 아이콘 버튼을 렌더링해야 한다", () => {
    render(<InfoTooltip content="테스트 내용" />);
    const trigger = screen.getByRole("button", { name: "설명 보기" });
    expect(trigger).toBeInTheDocument();
  });

  it("커스텀 aria-label이 적용되어야 한다", () => {
    render(<InfoTooltip content="테스트 내용" ariaLabel="DAU 설명" />);
    expect(screen.getByRole("button", { name: "DAU 설명" })).toBeInTheDocument();
  });

  it("트리거 버튼이 포커스 가능하고 aria-label을 가진다", async () => {
    render(<InfoTooltip content="유저 사이트에서 오늘 발생한 이벤트" />);
    const trigger = screen.getByRole("button", { name: "설명 보기" });
    // 트리거 버튼이 정상 렌더링되고 포커스 가능한지 확인
    expect(trigger).toBeInTheDocument();
    await act(async () => {
      trigger.focus();
    });
    expect(trigger).toHaveFocus();
    await act(async () => {
      trigger.blur();
    });
  });
});
