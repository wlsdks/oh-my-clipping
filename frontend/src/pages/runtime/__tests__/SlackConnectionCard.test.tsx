import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { SlackConnectionCard } from "../SlackConnectionCard";
import type { RuntimeSettings } from "@/types/runtime";

// runtimeService.verifySlackConnection만 목. 실제 HTTP 호출 차단.
const verifyMock = vi.fn();
vi.mock("@/services/runtimeService", () => ({
  runtimeService: {
    verifySlackConnection: (...args: unknown[]) => verifyMock(...args),
  },
}));

/** 테스트용 기본 RuntimeSettings. 필수 필드만 채우고 나머지는 최소값. */
function makeSettings(overrides: Partial<RuntimeSettings> = {}): RuntimeSettings {
  return {
    defaultHoursBack: 24,
    summaryInputMaxChars: 5000,
    digestMinImportanceScore: 0.5,
    digestDefaultMaxItems: 5,
    digestMaxMessageChars: 3000,
    digestItemSummaryMaxChars: 200,
    digestKeywordMaxCount: 5,
    jobWorkerBatchSize: 10,
    jobMaxAttempts: 3,
    jobInitialBackoffSeconds: 30,
    slackBotToken: "",
    slackBotTokenConfigured: false,
    slackDigestBlockKitTemplate: "",
    slackAutoDigestEnabled: false,
    slackDigestCron: "0 9 * * 1-5",
    slackAutoDigestMaxItems: 5,
    slackAutoDigestUnsentOnly: true,
    slackDailyChannelMessageLimit: 50,
    ralphOrchestrationEnabled: false,
    ralphLoopEnabled: false,
    ralphLoopMaxIterations: 3,
    ralphLoopStopPhrase: "stop",
    maintenanceMode: false,
    maintenanceMessage: "",
    opsLogChannelId: "",
    opsRequestChannelId: "",
    securityAlertChannelId: "",
    competitorWeeklyEnabled: false,
    competitorWeeklyChannelId: "",
    competitorWeeklyDmMode: "off",
    competitorWeeklyDmUserIds: "",
    competitorWeeklyDay: "MONDAY",
    competitorWeeklyHour: 9,
    reviewBatchUxEnabled: false,
    defaultReviewPerCategory: 20,
    retentionRssItemsDays: 30,
    retentionBatchSummariesDays: 90,
    ...overrides,
  };
}

describe("SlackConnectionCard", () => {
  beforeEach(() => {
    verifyMock.mockReset();
  });

  it("xoxb- 접두어가 아닌 토큰을 입력하면 zod 에러를 렌더링한다", async () => {
    render(
      <SlackConnectionCard settings={makeSettings()} isSaving={false} onSave={() => {}} />,
    );

    const tokenInput = screen.getByLabelText("봇 토큰") as HTMLInputElement;
    fireEvent.change(tokenInput, { target: { value: "invalid-token" } });
    // 저장 버튼 클릭으로 zod 검증 트리거
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText(/봇 토큰은 xoxb-로 시작해야 해요/)).toBeInTheDocument();
    });
  });

  it("dirty 변경이 없으면 저장 버튼이 비활성화된다", () => {
    render(
      <SlackConnectionCard settings={makeSettings()} isSaving={false} onSave={() => {}} />,
    );

    const submitButton = screen.getByRole("button", { name: "저장" }) as HTMLButtonElement;
    expect(submitButton.disabled).toBe(true);
  });

  it("missing_scope 응답 시 neededScopes 안내 문구를 노출한다", async () => {
    verifyMock.mockResolvedValueOnce({
      ok: false,
      message: "missing_scope",
      neededScopes: "channels:read,groups:read",
    });

    render(
      <SlackConnectionCard
        settings={makeSettings({ slackBotTokenConfigured: true })}
        isSaving={false}
        onSave={() => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /연결 검증/ }));

    await waitFor(() => {
      expect(screen.getByText("연결 실패")).toBeInTheDocument();
    });
    // neededScopes 안내
    expect(screen.getByText("channels:read,groups:read")).toBeInTheDocument();
    expect(screen.getByText(/Slack 앱 OAuth Scopes에 추가하세요/)).toBeInTheDocument();
  });
});
