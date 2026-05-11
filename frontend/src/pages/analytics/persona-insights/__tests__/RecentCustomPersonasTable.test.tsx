import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { RecentCustomPersonasTable } from "../RecentCustomPersonasTable";

describe("RecentCustomPersonasTable", () => {
  it("빈 리스트는 안내 메시지를 표시한다", () => {
    render(<RecentCustomPersonasTable personas={[]} />);
    expect(screen.getByText("커스텀 스타일이 없어요")).toBeInTheDocument();
  });

  it("데이터가 있으면 행을 렌더링한다", () => {
    render(
      <RecentCustomPersonasTable
        personas={[
          {
            id: "1",
            personaName: "VC 스타일",
            userName: "홍길동",
            systemPromptPreview: "VC 관점으로 요약해줘",
            createdAt: "2026-04-01T00:00:00Z",
          },
        ]}
      />
    );
    expect(screen.getByText("VC 스타일")).toBeInTheDocument();
    expect(screen.getByText("홍길동")).toBeInTheDocument();
  });

  it("긴 prompt preview 셀에 truncate 클래스가 적용된다", () => {
    render(
      <RecentCustomPersonasTable
        personas={[
          {
            id: "1",
            personaName: "Long",
            userName: "User",
            systemPromptPreview: "x".repeat(500),
            createdAt: "2026-04-01T00:00:00Z",
          },
        ]}
      />
    );
    const cell = screen.getByText("x".repeat(500));
    expect(cell).toHaveClass("truncate");
  });
});
