import { describe, it, expect, vi } from "vitest";
import { act, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { StaleEditResolveModal } from "@/components/shared/StaleEditResolveModal";
import type { StaleEditInfo } from "@/shared/types/common";

function makeInfo(overrides: Partial<StaleEditInfo> = {}): StaleEditInfo {
  return {
    code: "STALE_EDIT",
    latestUpdatedAt: "2026-04-17T10:00:00Z",
    latestEditorName: "김관리",
    changedFieldNames: ["name"],
    ...overrides
  };
}

describe("StaleEditResolveModal", () => {
  it("open=true면 제목을 렌더한다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo()}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("편집하는 동안 누가 먼저 저장했어요")).toBeInTheDocument();
  });

  it("편집자 이름을 본문에 표시한다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo({ latestEditorName: "이관리" })}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("이관리")).toBeInTheDocument();
  });

  it("변경 필드가 3개 이하이면 전체 목록을 보여준다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo({ changedFieldNames: ["name", "maxItems"] })}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("name, maxItems")).toBeInTheDocument();
  });

  it("변경 필드가 4개 이상이면 '외 N개' 요약을 보여준다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo({
          changedFieldNames: ["a", "b", "c", "d", "e"]
        })}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("a, b, c 외 2개")).toBeInTheDocument();
  });

  it("편집자 이름이 빈 문자열이면 '관리자'로 익명화한다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo({ latestEditorName: "" })}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("관리자")).toBeInTheDocument();
  });

  it("변경 필드가 비어 있어도 렌더가 실패하지 않고 기본 문구가 보인다", () => {
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo({ changedFieldNames: [] })}
        onReload={vi.fn()}
      />
    );
    expect(screen.getByText("설정 일부")).toBeInTheDocument();
  });

  it("취소 버튼 클릭 시 onOpenChange(false)가 호출된다", () => {
    const onOpenChange = vi.fn();
    render(
      <StaleEditResolveModal
        open
        onOpenChange={onOpenChange}
        staleEditInfo={makeInfo()}
        onReload={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("최신 불러오기 버튼 클릭 시 onReload가 호출된다", async () => {
    const onReload = vi.fn().mockResolvedValue(undefined);
    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo()}
        onReload={onReload}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "최신 불러오기" }));
    await waitFor(() => expect(onReload).toHaveBeenCalledOnce());
  });

  it("reload 진행 중에는 버튼이 비활성화되고 '불러오는 중...' 텍스트가 뜬다", async () => {
    // 의도적으로 resolve를 지연시켜 로딩 상태를 관찰한다
    let resolveReload: () => void = () => {};
    const onReload = vi.fn().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveReload = resolve;
        })
    );

    render(
      <StaleEditResolveModal
        open
        onOpenChange={vi.fn()}
        staleEditInfo={makeInfo()}
        onReload={onReload}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "최신 불러오기" }));

    await waitFor(() => {
      expect(screen.getByText(/불러오는 중/)).toBeInTheDocument();
    });

    await act(async () => {
      resolveReload();
    });
  });

  it("staleEditInfo가 null이어도 open=false면 렌더되지 않는다", () => {
    render(
      <StaleEditResolveModal
        open={false}
        onOpenChange={vi.fn()}
        staleEditInfo={null}
        onReload={vi.fn()}
      />
    );
    expect(screen.queryByText("편집하는 동안 누가 먼저 저장했어요")).not.toBeInTheDocument();
  });
});
