import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ConfirmModal } from "@/components/shared/ConfirmModal";

describe("ConfirmModal", () => {
  it("open=true 시 제목을 렌더링해야 한다", () => {
    render(<ConfirmModal open onOpenChange={vi.fn()} title="삭제 확인" onConfirm={vi.fn()} />);
    expect(screen.getByText("삭제 확인")).toBeInTheDocument();
  });

  it("확인 버튼 클릭 시 onConfirm이 호출되어야 한다", () => {
    const onConfirm = vi.fn();
    render(<ConfirmModal open onOpenChange={vi.fn()} title="확인" onConfirm={onConfirm} />);
    fireEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });
});
