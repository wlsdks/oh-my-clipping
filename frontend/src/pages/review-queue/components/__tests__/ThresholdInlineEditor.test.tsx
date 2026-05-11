import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

import { toast } from "sonner";
import { ThresholdInlineEditor } from "../ThresholdInlineEditor";

describe("ThresholdInlineEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("현재 값을 소숫점 두 자리로 표시하고, 편집 클릭 시 input 이 노출된다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);

    render(<ThresholdInlineEditor categoryId="c1" currentValue={0.5} onSave={onSave} />);

    // 표시 상태
    const display = screen.getByTestId("threshold-display");
    expect(display).toHaveTextContent("0.50");
    expect(screen.queryByTestId("threshold-input")).not.toBeInTheDocument();

    // 편집 클릭 → 인풋 노출
    await userEvent.click(display);

    const input = screen.getByTestId("threshold-input") as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.value).toBe("0.50");
  });

  it("currentValue=null 이면 '설정 안 됨' 으로 표시한다", () => {
    const onSave = vi.fn().mockResolvedValue(undefined);

    render(<ThresholdInlineEditor categoryId="c1" currentValue={null} onSave={onSave} />);

    expect(screen.getByTestId("threshold-display")).toHaveTextContent("설정 안 됨");
  });

  it("유효값 저장 시 onSave 가 호출되고 성공 후 값이 반영된다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();

    render(<ThresholdInlineEditor categoryId="c1" currentValue={0.5} onSave={onSave} />);

    await user.click(screen.getByTestId("threshold-display"));
    const input = screen.getByTestId("threshold-input");

    await user.clear(input);
    await user.type(input, "0.75");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledTimes(1);
    });
    expect(onSave).toHaveBeenCalledWith(0.75);

    // 저장 후 표시 상태로 복귀 + 새 값 반영 (optimistic)
    await waitFor(() => {
      expect(screen.getByTestId("threshold-display")).toHaveTextContent("0.75");
    });
    expect(toast.success).toHaveBeenCalledWith("임계값이 저장되었습니다");
  });

  it("onSave 가 reject 되면 toast.error 를 띄우고 원래 값으로 롤백한다", async () => {
    const onSave = vi.fn().mockRejectedValue(new Error("서버 오류"));
    const user = userEvent.setup();

    render(<ThresholdInlineEditor categoryId="c1" currentValue={0.5} onSave={onSave} />);

    await user.click(screen.getByTestId("threshold-display"));
    const input = screen.getByTestId("threshold-input");

    await user.clear(input);
    await user.type(input, "0.75");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledTimes(1);
    });

    // 롤백: display 가 다시 0.50
    await waitFor(() => {
      expect(screen.getByTestId("threshold-display")).toHaveTextContent("0.50");
    });
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("0~1 범위 밖 입력은 저장되지 않고 invalid 표시가 된다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();

    render(<ThresholdInlineEditor categoryId="c1" currentValue={0.5} onSave={onSave} />);

    await user.click(screen.getByTestId("threshold-display"));
    const input = screen.getByTestId("threshold-input") as HTMLInputElement;

    await user.clear(input);
    await user.type(input, "1.5");

    // aria-invalid=true + border-destructive 클래스
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input.className).toContain("border-destructive");

    // Enter 로 커밋 시도해도 onSave 는 호출되지 않아야 함
    await user.keyboard("{Enter}");
    expect(onSave).not.toHaveBeenCalled();
  });

  it("Escape 를 누르면 편집이 취소되고 원래 값이 유지된다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();

    render(<ThresholdInlineEditor categoryId="c1" currentValue={0.5} onSave={onSave} />);

    await user.click(screen.getByTestId("threshold-display"));
    const input = screen.getByTestId("threshold-input");

    await user.clear(input);
    await user.type(input, "0.99");
    await user.keyboard("{Escape}");

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByTestId("threshold-display")).toHaveTextContent("0.50");
  });
});
