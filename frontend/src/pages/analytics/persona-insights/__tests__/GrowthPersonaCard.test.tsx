import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { GrowthPersonaCard } from "../GrowthPersonaCard";
import type { GrowthSignalItem } from "@/types/personaAnalytics";

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>(
    "react-router-dom",
  );
  return { ...actual, useNavigate: () => vi.fn() };
});

function renderCard(item: GrowthSignalItem) {
  return render(
    <MemoryRouter>
      <GrowthPersonaCard item={item} />
    </MemoryRouter>,
  );
}

describe("GrowthPersonaCard", () => {
  it("SUBS_SURGE + 커스텀: 프리셋화 제안 CTA 를 노출한다 (disabled)", () => {
    const item: GrowthSignalItem = {
      personaId: "c1",
      personaName: "스타트업 레이더",
      isPreset: false,
      signalType: "SUBS_SURGE",
      persistentWeeks: 1,
      details: {
        type: "SUBS_SURGE",
        activeSubs: 12,
        prevActiveSubs: 7,
        deltaAbs: 5,
        deltaPct: 71,
      },
    };
    renderCard(item);

    expect(screen.getByText("구독 급증")).toBeInTheDocument();
    expect(screen.getByText("커스텀")).toBeInTheDocument();
    expect(screen.getByText(/7 → 12 \(\+5\)/)).toBeInTheDocument();
    expect(screen.getByText(/\+71%/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /최근 구독자 보기/ }),
    ).toBeInTheDocument();

    const suggestButton = screen.getByRole("button", {
      name: /템플릿화 제안/,
    });
    expect(suggestButton).toBeInTheDocument();
    expect(suggestButton).toBeDisabled();
  });

  it("SUBS_SURGE + 프리셋: 프리셋화 제안 버튼을 노출하지 않는다", () => {
    const item: GrowthSignalItem = {
      personaId: "p1",
      personaName: "테크 에디터",
      isPreset: true,
      signalType: "SUBS_SURGE",
      persistentWeeks: 2,
      details: {
        type: "SUBS_SURGE",
        activeSubs: 20,
        prevActiveSubs: 10,
        deltaAbs: 10,
        deltaPct: 100,
      },
    };
    renderCard(item);

    expect(screen.getByText("템플릿")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /프리셋화 제안/ }),
    ).not.toBeInTheDocument();
  });

  it("FIRST_SUBSCRIPTION: 첫 구독 카피와 생성 경과 일 수를 노출한다", () => {
    const item: GrowthSignalItem = {
      personaId: "p2",
      personaName: "갓 태어난 스타일",
      isPreset: false,
      signalType: "FIRST_SUBSCRIPTION",
      persistentWeeks: 1,
      details: {
        type: "FIRST_SUBSCRIPTION",
        activeSubs: 3,
        daysSinceCreation: 5,
      },
    };
    renderCard(item);

    expect(screen.getByText("첫 구독 진입")).toBeInTheDocument();
    expect(screen.getByText(/첫 구독 3명/)).toBeInTheDocument();
    expect(screen.getByText(/생성 5일차/)).toBeInTheDocument();
    // FIRST_SUBSCRIPTION 은 프리셋화 제안 CTA 를 두지 않는다.
    expect(
      screen.queryByRole("button", { name: /프리셋화 제안/ }),
    ).not.toBeInTheDocument();
  });
});
