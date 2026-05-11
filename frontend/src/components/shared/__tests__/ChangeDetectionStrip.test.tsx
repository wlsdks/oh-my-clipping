import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ChangeDetectionStrip } from "@/components/shared/ChangeDetectionStrip";

describe("ChangeDetectionStrip", () => {
  it("initial 과 current 가 같으면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(
      <ChangeDetectionStrip
        initialUpdatedAt="2026-04-17T09:00:00Z"
        currentUpdatedAt="2026-04-17T09:00:00Z"
        onReload={vi.fn()}
      />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("initial 이 null 이면 렌더링하지 않는다 — 신규 생성 모달 방어", () => {
    const { container } = render(
      <ChangeDetectionStrip
        initialUpdatedAt={null}
        currentUpdatedAt="2026-04-17T09:05:00Z"
        onReload={vi.fn()}
      />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("변경 감지 시 띠와 재조회 버튼을 보여준다", () => {
    render(
      <ChangeDetectionStrip
        initialUpdatedAt="2026-04-17T09:00:00Z"
        currentUpdatedAt="2026-04-17T09:05:00Z"
        onReload={vi.fn()}
      />
    );
    expect(screen.getByTestId("change-detection-strip")).toHaveTextContent(/방금 다른 관리자가 저장했어요/);
    expect(screen.getByRole("button", { name: /최신 불러오기/ })).toBeInTheDocument();
  });

  it("버튼 클릭 시 onReload 를 호출하고 이후 띠가 사라진다", async () => {
    const onReload = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(
      <ChangeDetectionStrip
        initialUpdatedAt="2026-04-17T09:00:00Z"
        currentUpdatedAt="2026-04-17T09:05:00Z"
        onReload={onReload}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /최신 불러오기/ }));
    await waitFor(() => expect(onReload).toHaveBeenCalledTimes(1));

    // reload 성공 후 내부 baselineRef 가 현재 값을 가리키므로 같은 props 로 re-render 해도 띠가 사라진다.
    rerender(
      <ChangeDetectionStrip
        initialUpdatedAt="2026-04-17T09:00:00Z"
        currentUpdatedAt="2026-04-17T09:05:00Z"
        onReload={onReload}
      />
    );
    expect(screen.queryByTestId("change-detection-strip")).not.toBeInTheDocument();
  });
});
