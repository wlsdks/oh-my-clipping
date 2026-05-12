import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DataRetentionCard } from "../DataRetentionCard";
import type { RuntimeSettings } from "@/types/runtime";

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

describe("DataRetentionCard", () => {
  const onSave = vi.fn();

  beforeEach(() => {
    onSave.mockReset();
  });

  /** 카드를 펼쳐서 폼 필드에 접근하는 헬퍼 */
  async function openCard() {
    fireEvent.click(screen.getByRole("button", { name: /데이터 보관 정책/ }));
    await screen.findByLabelText("원본 기사 보관 (일)");
  }

  it("폼 유효성 — 6 입력 시 '최소 7일' 에러를 렌더링한다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    const input = screen.getByLabelText("원본 기사 보관 (일)");
    fireEvent.change(input, { target: { value: "6" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("최소 7일")).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("폼 유효성 — 원본 기사 366일 입력 시 '최대 365일' 에러를 렌더링한다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    const input = screen.getByLabelText("원본 기사 보관 (일)");
    fireEvent.change(input, { target: { value: "366" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("최대 365일")).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("폼 유효성 — AI 요약 731일 입력 시 '최대 730일' 에러를 렌더링한다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    const summaryInput = screen.getByLabelText("AI 요약 보관 (일)");
    fireEvent.change(summaryInput, { target: { value: "731" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("최대 730일")).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("저장 해피패스 — 올바른 값 입력 후 onSave가 올바른 payload로 호출된다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    fireEvent.change(screen.getByLabelText("원본 기사 보관 (일)"), { target: { value: "60" } });
    fireEvent.change(screen.getByLabelText("AI 요약 보관 (일)"), { target: { value: "180" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith({
        retentionRssItemsDays: 60,
        retentionBatchSummariesDays: 180,
      });
    });
  });

  it("더티 상태 아닐 때 저장 버튼이 비활성화된다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    const submit = screen.getByRole("button", { name: "저장" }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it("값을 변경하면 저장 버튼이 활성화된다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={false} onSave={onSave} />);
    await openCard();

    fireEvent.change(screen.getByLabelText("원본 기사 보관 (일)"), { target: { value: "60" } });

    const submit = screen.getByRole("button", { name: "저장" }) as HTMLButtonElement;
    expect(submit.disabled).toBe(false);
  });

  it("isSaving=true이면 저장 버튼 텍스트가 '저장 중...'이고 비활성화된다", async () => {
    render(<DataRetentionCard settings={makeSettings()} isSaving={true} onSave={onSave} />);
    await openCard();

    // dirty 상태 만들기
    fireEvent.change(screen.getByLabelText("원본 기사 보관 (일)"), { target: { value: "60" } });

    const submit = screen.getByRole("button", { name: "저장 중..." }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });
});
