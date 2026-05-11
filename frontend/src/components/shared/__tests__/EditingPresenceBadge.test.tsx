import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EditingPresenceBadge } from "@/components/shared/EditingPresenceBadge";
import type { EditingSession } from "@/types/editingPresence";

function session(overrides: Partial<EditingSession> = {}): EditingSession {
  return {
    userId: "u-1",
    displayName: "앨리스",
    startedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
    ...overrides
  };
}

describe("EditingPresenceBadge", () => {
  it("편집자가 없으면 아무 것도 렌더링하지 않는다", () => {
    const { container } = render(<EditingPresenceBadge editors={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("한 명이면 'A님이 … 편집 중이에요' 문구를 보여준다", () => {
    render(<EditingPresenceBadge editors={[session({ displayName: "앨리스" })]} />);
    expect(screen.getByTestId("editing-presence-badge")).toHaveTextContent(/앨리스님이/);
    expect(screen.getByTestId("editing-presence-badge")).toHaveTextContent(/편집 중이에요/);
  });

  it("여러 명이면 '외 N명이' 라는 요약이 보인다", () => {
    render(
      <EditingPresenceBadge
        editors={[
          session({ userId: "u-1", displayName: "앨리스" }),
          session({ userId: "u-2", displayName: "밥" }),
          session({ userId: "u-3", displayName: "찰리" })
        ]}
      />
    );
    expect(screen.getByTestId("editing-presence-badge")).toHaveTextContent(/앨리스님 외 2명이/);
  });
});
