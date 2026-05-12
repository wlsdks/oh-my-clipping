import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AtRiskPersonaCard } from "../AtRiskPersonaCard";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { RiskSignalItem } from "@/types/personaAnalytics";

// react-router-dom 의 useNavigate 만 별도 spy — 나머지는 실제 모듈 유지.
const navigateSpy = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>(
    "react-router-dom",
  );
  return { ...actual, useNavigate: () => navigateSpy };
});

function renderCard(item: RiskSignalItem) {
  return render(
    <MemoryRouter>
      <AtRiskPersonaCard item={item} currentWeekIso="2026-W16" />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

beforeEach(() => {
  navigateSpy.mockClear();
});

describe("AtRiskPersonaCard", () => {
  it("CHURN_EXCESS 2주차: 편집 · 구독자 보기 · 알림 7일 끄기 를 노출한다", () => {
    const item: RiskSignalItem = {
      personaId: "p1",
      personaName: "깊이 있는 분석",
      isPreset: true,
      riskType: "CHURN_EXCESS",
      persistentWeeks: 2,
      details: {
        type: "CHURN_EXCESS",
        churnedSubs: 5,
        newSubs: 1,
        activeSubs: 15,
      },
    };
    renderCard(item);

    expect(screen.getByText("이탈 초과")).toBeInTheDocument();
    expect(screen.getByText(/2주차/)).toBeInTheDocument();
    expect(screen.getByText(/이탈 5 · 신규 1/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /편집/ })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /구독자 보기/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /알림 7일 끄기/ }),
    ).toBeInTheDocument();
    // 3주차+ 전용 CTA 는 나오지 않아야 한다.
    expect(
      screen.queryByRole("button", { name: /비활성화 제안/ }),
    ).not.toBeInTheDocument();
  });

  it("ENGAGEMENT_DROP 3주차: 비활성화 제안 CTA 를 노출한다", () => {
    const item: RiskSignalItem = {
      personaId: "p2",
      personaName: "시장 속보",
      isPreset: true,
      riskType: "ENGAGEMENT_DROP",
      persistentWeeks: 3,
      details: {
        type: "ENGAGEMENT_DROP",
        engagementRate: 0.3,
        prevEngagementRate: 0.45,
        deltaPp: -15,
        deliveredCount: 40,
        totalClicks: 12,
      },
    };
    renderCard(item);

    expect(screen.getByText("참여 하락")).toBeInTheDocument();
    expect(screen.getByText(/3주차/)).toBeInTheDocument();
    expect(screen.getByText(/45% → 30% \(-15pp\)/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /비활성화 제안/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /최근 발송 보기/ }),
    ).toBeInTheDocument();
    // 3주차+ 에서는 알림 끄기 버튼이 사라진다.
    expect(
      screen.queryByRole("button", { name: /알림 7일 끄기/ }),
    ).not.toBeInTheDocument();
  });

  it("IDLE 1주차: 비활성화 CTA (제안 아님) 를 노출한다", () => {
    const item: RiskSignalItem = {
      personaId: "p3",
      personaName: "옛날 스타일",
      isPreset: true,
      riskType: "IDLE",
      persistentWeeks: 1,
      details: { type: "IDLE", consecutiveWeeks: 4, activeSubs: 0 },
    };
    renderCard(item);

    expect(screen.getByText("유휴")).toBeInTheDocument();
    expect(screen.getByText(/NEW/)).toBeInTheDocument();
    expect(screen.getByText(/4주 연속 발송 없음/)).toBeInTheDocument();
    // IDLE 은 1주차부터 "비활성화" (제안 아님). destructive 변형.
    const deactivate = screen.getByRole("button", { name: "비활성화" });
    expect(deactivate).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /알림 7일 끄기/ }),
    ).toBeInTheDocument();
  });
});
