import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ReviewQueueFeatureCard } from "../ReviewQueueFeatureCard";
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

describe("ReviewQueueFeatureCard", () => {
  const onSave = vi.fn();

  beforeEach(() => {
    onSave.mockReset();
  });

  it("접힌 상태에서 요약 문자열로 현재 설정을 표시한다 (배치 off + 샘플링 20)", () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );
    // 접힌 summary 텍스트
    expect(screen.getByText(/단건 모드/)).toBeInTheDocument();
    expect(screen.getByText(/카테고리별 20건/)).toBeInTheDocument();
  });

  it("배치 플래그 on이면 '일괄 UI 켜짐' 요약을 표시한다", () => {
    render(
      <ReviewQueueFeatureCard
        settings={makeSettings({ reviewBatchUxEnabled: true, defaultReviewPerCategory: 0 })}
        isSaving={false}
        onSave={onSave}
      />
    );
    expect(screen.getByText(/일괄 UI 켜짐/)).toBeInTheDocument();
    expect(screen.getByText(/샘플링 비활성/)).toBeInTheDocument();
  });

  it("카드를 열고 숫자 입력을 변경한 뒤 저장하면 onSave가 호출된다", async () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );

    // 카드 펼치기
    fireEvent.click(screen.getByRole("button", { name: /뉴스 검토 UX/ }));

    const input = await screen.findByLabelText("카테고리별 샘플 수 (0~100)");
    fireEvent.change(input, { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith({
        reviewBatchUxEnabled: false,
        defaultReviewPerCategory: 10,
      });
    });
  });

  it("숫자 입력 범위(0~100)를 벗어나면 zod 에러를 렌더링한다", async () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );
    fireEvent.click(screen.getByRole("button", { name: /뉴스 검토 UX/ }));

    const input = await screen.findByLabelText("카테고리별 샘플 수 (0~100)");
    fireEvent.change(input, { target: { value: "200" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText(/최대 100까지/)).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("음수 입력 시 에러를 렌더링한다", async () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );
    fireEvent.click(screen.getByRole("button", { name: /뉴스 검토 UX/ }));

    const input = await screen.findByLabelText("카테고리별 샘플 수 (0~100)");
    fireEvent.change(input, { target: { value: "-1" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText(/0 이상이어야 해요/)).toBeInTheDocument();
    });
    expect(onSave).not.toHaveBeenCalled();
  });

  it("토글을 눌러 배치 UX를 켜고 저장하면 reviewBatchUxEnabled=true로 저장된다", async () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );
    fireEvent.click(screen.getByRole("button", { name: /뉴스 검토 UX/ }));

    // Radix Switch는 role="switch"
    const toggle = await screen.findByRole("switch", { name: "일괄 검토 UI 사용" });
    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith({
        reviewBatchUxEnabled: true,
        defaultReviewPerCategory: 20,
      });
    });
  });

  it("dirty 상태가 아니면 저장 버튼이 비활성화된다", async () => {
    render(
      <ReviewQueueFeatureCard settings={makeSettings()} isSaving={false} onSave={onSave} />
    );
    fireEvent.click(screen.getByRole("button", { name: /뉴스 검토 UX/ }));

    const submit = (await screen.findByRole("button", { name: "저장" })) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });
});
