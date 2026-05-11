import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { OpsNotificationSection } from "../OpsNotificationSection";
import type { RuntimeSettings } from "@/types/runtime";

// Radix Switch / Select는 ResizeObserver를 참조 (jsdom 미지원)
beforeAll(() => {
  global.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
});

/** 테스트용 기본 RuntimeSettings (전체 필드 포함) */
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
    // 운영 알림 프로필 기본값
    opsNotificationProfile: "FULL",
    opsDailyForecastHour: 8,
    opsWeeklyReportDay: "MONDAY",
    opsWeeklyReportHour: 9,
    opsPipelineCooldownMinutes: 30,
    opsIncidentWindowMinutes: 60,
    opsIncidentThresholdCategories: 2,
    opsScheduleMissGraceMinutes: 15,
    opsBudgetWarnPct: 70,
    opsBudgetCriticalPct: 90,
    opsAdminBaseUrl: null,
    opsSilentHoursEnabled: true,
    opsRecoveryStreakThreshold: 3,
    opsLogsEnabled: true,
    ...overrides,
  };
}

describe("OpsNotificationSection", () => {
  const onSave = vi.fn();

  beforeEach(() => {
    onSave.mockReset();
  });

  function openSection() {
    // CollapsibleSection 헤더 버튼 클릭 → 폼 노출
    fireEvent.click(screen.getByRole("button", { name: /운영 알림 프로필/ }));
  }

  it("접힌 상태에서 FULL 프로필 요약을 표시한다", () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    expect(screen.getByText(/FULL/)).toBeInTheDocument();
  });

  it("Kill Switch off 상태에서는 경고 요약을 표시한다", () => {
    render(
      <OpsNotificationSection
        settings={makeSettings({ opsLogsEnabled: false })}
        isSaving={false}
        onSave={onSave}
      />
    );
    expect(screen.getByText(/Kill Switch 활성/)).toBeInTheDocument();
  });

  it("섹션을 열면 Kill Switch 스위치가 렌더링된다", () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();
    expect(screen.getByRole("switch", { name: /Kill Switch/ })).toBeInTheDocument();
  });

  it("고급 옵션 버튼 클릭 시 쿨다운 입력이 표시된다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    expect(screen.queryByLabelText(/쿨다운/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /고급 옵션 보기/ }));
    expect(screen.getByLabelText(/쿨다운/i)).toBeInTheDocument();
  });

  it("고급 옵션 버튼을 다시 클릭하면 숨겨진다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    fireEvent.click(screen.getByRole("button", { name: /고급 옵션 보기/ }));
    expect(screen.getByLabelText(/쿨다운/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /고급 옵션 숨기기/ }));
    expect(screen.queryByLabelText(/쿨다운/i)).not.toBeInTheDocument();
  });

  it("Forecast 시간을 변경하고 저장하면 onSave가 opsDailyForecastHour=10으로 호출된다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    const input = screen.getByLabelText(/일일 Forecast 시간/i);
    fireEvent.change(input, { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith(
        expect.objectContaining({ opsDailyForecastHour: 10 })
      );
    });
  });

  it("예산 경고 임계값에 음수를 입력하면 onSave를 호출하지 않는다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    // 고급 옵션 열기
    fireEvent.click(screen.getByRole("button", { name: /고급 옵션 보기/ }));

    const input = screen.getByLabelText(/예산 경고 임계/i);
    fireEvent.change(input, { target: { value: "-5" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).not.toHaveBeenCalled();
    });
  });

  it("관리자 URL이 https가 아니면 에러를 표시하고 onSave를 호출하지 않는다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    const input = screen.getByLabelText(/Base URL/i);
    fireEvent.change(input, { target: { value: "http://example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText(/https:\/\/로 시작해야 해요/)).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("관리자 URL을 비워두면 onSave에 opsAdminBaseUrl=null이 전달된다", async () => {
    render(
      <OpsNotificationSection
        settings={makeSettings({ opsAdminBaseUrl: "https://prev.example.com" })}
        isSaving={false}
        onSave={onSave}
      />
    );
    openSection();

    const input = screen.getByLabelText(/Base URL/i);
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith(
        expect.objectContaining({ opsAdminBaseUrl: null })
      );
    });
  });

  it("dirty 상태가 아니면 저장 버튼이 비활성화된다", async () => {
    render(<OpsNotificationSection settings={makeSettings()} isSaving={false} onSave={onSave} />);
    openSection();

    const submit = screen.getByRole("button", { name: "저장" }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it("Kill Switch를 끄면 저장 버튼이 활성화된다", async () => {
    render(<OpsNotificationSection settings={makeSettings({ opsLogsEnabled: true })} isSaving={false} onSave={onSave} />);
    openSection();

    const killSwitch = screen.getByRole("switch", { name: /Kill Switch/ });
    fireEvent.click(killSwitch);

    const submit = screen.getByRole("button", { name: "저장" }) as HTMLButtonElement;
    expect(submit.disabled).toBe(false);
  });
});
