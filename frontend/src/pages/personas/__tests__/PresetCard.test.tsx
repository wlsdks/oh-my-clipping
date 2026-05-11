import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PresetCard } from "../PresetCard";
import type { Persona } from "@/types/persona";

function makePersona(overrides: Partial<Persona> = {}): Persona {
  return {
    id: "p1",
    name: "경영진 브리핑",
    description: "핵심만 짧게",
    systemPrompt: "test prompt",
    summaryStyle: null,
    targetAudience: "경영진",
    maxItems: 5,
    language: "ko",
    isActive: true,
    isPreset: true,
    currentVersion: 3,
    previewTitle: "📌 MegaCorp 뉴스",
    previewSource: null,
    previewBody: null,
    tone: null,
    lengthPref: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides
  };
}

describe("PresetCard", () => {
  it("프리셋 이름, 버전, 구독수를 표시한다", () => {
    const { container } = render(<PresetCard persona={makePersona()} subscriptionCount={7} onClick={() => {}} />);

    expect(screen.getByText("경영진 브리핑")).toBeInTheDocument();
    expect(screen.getByText("v3")).toBeInTheDocument();
    expect(container.textContent).toContain("구독 7건");
  });

  it("비활성 프리셋은 opacity가 낮다", () => {
    const { container } = render(
      <PresetCard persona={makePersona({ isActive: false })} subscriptionCount={0} onClick={() => {}} />
    );

    const card = container.firstElementChild as HTMLElement;
    expect(card.className).toContain("opacity-55");
  });

  it("카드 클릭 시 onClick 호출", () => {
    const onClick = vi.fn();
    render(<PresetCard persona={makePersona()} subscriptionCount={0} onClick={onClick} />);

    fireEvent.click(screen.getByText("경영진 브리핑"));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
