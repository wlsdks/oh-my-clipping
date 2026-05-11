import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import { ActionItemsCard } from "../ActionItemsCard";
import type { ActionItem } from "../../model/dashboardState";

function renderCard(items: ActionItem[]) {
  return render(
    <MemoryRouter>
      <ActionItemsCard items={items} />
    </MemoryRouter>,
  );
}

describe("ActionItemsCard", () => {
  it("빈 리스트일 때 성공 상태를 표시한다", () => {
    renderCard([]);
    expect(screen.getByText("모든 항목이 정상이에요")).toBeInTheDocument();
  });

  it("항목을 count 와 함께 렌더링한다", () => {
    renderCard([
      { id: "pending-accounts", label: "가입 승인", count: 3, href: "/admin/x", severity: "danger" },
    ]);
    expect(screen.getByText("가입 승인")).toBeInTheDocument();
    expect(screen.getByText("3건")).toBeInTheDocument();
  });

  it("각 항목의 href 를 링크로 연결한다", () => {
    renderCard([
      { id: "failed-runs", label: "파이프라인 실패", count: 1, href: "/admin/pipeline?status=FAILED", severity: "danger" },
    ]);
    const link = screen.getByRole("link", { name: /파이프라인 실패/ });
    expect(link).toHaveAttribute("href", "/admin/pipeline?status=FAILED");
  });

  it("severity 에 해당하는 aria-label 을 가진 점을 렌더링한다", () => {
    renderCard([
      { id: "x", label: "가입 승인", count: 2, href: "/admin/x", severity: "danger" },
    ]);
    expect(screen.getByRole("img", { name: "긴급" })).toBeInTheDocument();
  });

  it("카드 제목을 H2 로 렌더링한다", () => {
    renderCard([]);
    const heading = screen.getByRole("heading", { level: 2, name: "지금 확인이 필요해요" });
    expect(heading).toBeInTheDocument();
  });
});
