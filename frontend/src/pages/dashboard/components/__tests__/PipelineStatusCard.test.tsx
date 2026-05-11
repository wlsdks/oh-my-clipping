import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import { PipelineStatusCard } from "../PipelineStatusCard";

function renderCard(props: Parameters<typeof PipelineStatusCard>[0]) {
  return render(
    <MemoryRouter>
      <PipelineStatusCard {...props} />
    </MemoryRouter>,
  );
}

describe("PipelineStatusCard", () => {
  it("카운트가 있으면 단계별 숫자를 표시한다", () => {
    renderCard({ collected: 142, summarized: 89, sent: 45, lastSuccessAt: "2026-04-08T09:00:00Z" });
    expect(screen.getByText("142")).toBeInTheDocument();
    expect(screen.getByText("89")).toBeInTheDocument();
    expect(screen.getByText("45")).toBeInTheDocument();
  });

  it("모두 0 이면 아직 실행되지 않았어요 메시지를 표시한다", () => {
    renderCard({ collected: 0, summarized: 0, sent: 0, lastSuccessAt: null });
    expect(screen.getByText(/아직 실행되지 않았어요/)).toBeInTheDocument();
  });

  it("모두 0 이어도 lastSuccessAt 이 있으면 마지막 실행 시각을 표시한다", () => {
    renderCard({ collected: 0, summarized: 0, sent: 0, lastSuccessAt: "2026-04-07T18:00:00Z" });
    expect(screen.getByText(/마지막 실행:/)).toBeInTheDocument();
  });

  it("카드 제목을 H2 로 렌더링한다", () => {
    renderCard({ collected: 0, summarized: 0, sent: 0, lastSuccessAt: null });
    const heading = screen.getByRole("heading", { level: 2, name: "파이프라인 상태" });
    expect(heading).toBeInTheDocument();
  });
});
