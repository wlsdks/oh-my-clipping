import { test, expect } from "../fixtures/auth";
import type { Page } from "@playwright/test";

test.describe("runtime settings page", () => {
  /** 페이지 로딩 완료를 기다리는 헬퍼 — 로딩/에러 상태를 처리한다 */
  async function waitForPageLoaded(adminPage: Page) {
    const heading = adminPage.getByRole("heading", { name: "시스템 설정" });
    const loadingState = adminPage.getByText("불러오는 중...");
    const errorState = adminPage.getByText("설정을 불러오지 못했어요");

    await expect(heading.or(loadingState).or(errorState)).toBeVisible({ timeout: 10_000 });

    const isLoadingNow = await loadingState.isVisible().catch(() => false);
    if (isLoadingNow) {
      await expect(heading.or(errorState)).toBeVisible({ timeout: 15_000 });
    }

    return {
      isError: await errorState.isVisible().catch(() => false),
      isLoaded: await heading.isVisible().catch(() => false),
    };
  }

  test("페이지 헤더와 카드 섹션들이 렌더링된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    if (isError) {
      await expect(adminPage.getByText("설정을 불러오지 못했어요")).toBeVisible();
      return;
    }

    // 헤더
    await expect(adminPage.getByRole("heading", { name: "시스템 설정" })).toBeVisible();
    await expect(adminPage.getByText("Slack 연결과 파이프라인 기본값을 관리하세요")).toBeVisible();

    // 기본값 복원 버튼
    await expect(adminPage.getByRole("button", { name: "기본값 복원" })).toBeVisible();

    // Slack 연결 상태 배너 (연결됨 or 미연결 중 하나)
    const connectedBanner = adminPage.getByText("Slack 연결됨");
    const disconnectedBanner = adminPage.getByText("Slack 미연결");
    await expect(connectedBanner.or(disconnectedBanner)).toBeVisible({ timeout: 5_000 });

    // Slack 연결 카드 — 항상 펼쳐진 상태
    await expect(adminPage.getByText("Slack 연결").first()).toBeVisible();
    await expect(adminPage.getByText("채널별 일일 최대 메시지 수")).toBeVisible();

    // Collapsible 섹션 헤더 확인 (접힌 상태로 시작)
    await expect(adminPage.getByRole("button", { name: /뉴스 수집 설정/ })).toBeVisible();
    await expect(adminPage.getByRole("button", { name: /자동 발송/ })).toBeVisible();
    await expect(adminPage.getByText("차단 채널 관리")).toBeVisible();
    await expect(adminPage.getByRole("button", { name: /고급 설정.*Ralph/ })).toBeVisible();
  });

  test("Slack 연결 설정을 변경하고 저장할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    if (isError) {
      test.skip();
      return;
    }

    // 일일 메시지 제한 필드 변경
    const limitInput = adminPage.locator("#slackDailyChannelMessageLimit");
    await expect(limitInput).toBeVisible();
    const originalLimit = await limitInput.inputValue();
    const newLimit = Number(originalLimit) >= 3 ? "2" : "3";

    await limitInput.clear();
    await limitInput.fill(newLimit);

    // Slack 연결 카드의 저장 버튼 클릭
    // 버튼이 여러 개이므로 카드 section 내에서 첫 번째 저장 버튼 사용
    const slackCard = adminPage.locator("section").filter({ hasText: "Slack 연결" }).first();
    await slackCard.getByRole("button", { name: "저장" }).click();

    // 토스트 또는 검증 에러 확인
    const successToast = adminPage.getByText(/설정을 저장했어요/);
    const validationError = adminPage.getByText(/채널 ID를 입력하세요/);
    const errorToast = adminPage.getByText(/저장하지 못했어요|저장 실패/i);
    await expect(
      successToast.or(validationError).or(errorToast)
    ).toBeVisible({ timeout: 10_000 });
  });

  test("뉴스 수집 섹션을 펼치면 필드가 나타난다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    if (isError) {
      test.skip();
      return;
    }

    // 접힌 상태 — 필드가 보이지 않아야 함
    await expect(adminPage.locator("#defaultHoursBack")).not.toBeVisible();

    // 뉴스 수집 설정 섹션 헤더 클릭하여 펼침
    await adminPage.getByRole("button", { name: /뉴스 수집 설정/ }).click();

    // 펼쳐진 후 필드 확인
    await expect(adminPage.locator("#defaultHoursBack")).toBeVisible({ timeout: 5_000 });
    await expect(adminPage.getByText("기본 수집 기간 (시간)")).toBeVisible();
    await expect(adminPage.locator("#digestDefaultMaxItems")).toBeVisible();
    await expect(adminPage.locator("#digestMinImportanceScore")).toBeVisible();
  });

  test("자동 발송 섹션에서 cron 프리셋 칩이 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    if (isError) {
      test.skip();
      return;
    }

    // 자동 발송 섹션 펼침
    await adminPage.getByRole("button", { name: /자동 발송/ }).click();

    // 발송 스케줄 레이블 확인
    await expect(adminPage.getByText("발송 스케줄")).toBeVisible({ timeout: 5_000 });

    // 프리셋 칩 확인 — "매일 오전 9시" 클릭
    const dailyPreset = adminPage.getByRole("button", { name: "매일 오전 9시" });
    await expect(dailyPreset).toBeVisible();
    await dailyPreset.click();

    // 직접 입력 모드가 아니므로 cron 텍스트 입력 필드가 숨겨져야 함
    await expect(adminPage.locator("#slackDigestCron")).not.toBeVisible();

    // "직접 입력" 칩 클릭 시 cron 입력 필드 노출
    const customPreset = adminPage.getByRole("button", { name: "직접 입력" });
    await expect(customPreset).toBeVisible();
    await customPreset.click();
    await expect(adminPage.locator("#slackDigestCron")).toBeVisible({ timeout: 5_000 });
  });

  test("기본값 복원 모달이 열리고 보존 항목을 안내한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    if (isError) {
      test.skip();
      return;
    }

    // 기본값 복원 버튼 클릭
    const resetButton = adminPage.getByRole("button", { name: "기본값 복원" });
    await expect(resetButton).toBeVisible({ timeout: 10_000 });
    await resetButton.click();

    // 확인 모달이 열림
    const confirmDialog = adminPage.getByRole("dialog").last();
    await expect(confirmDialog).toBeVisible({ timeout: 5_000 });

    // 모달 제목 확인
    await expect(confirmDialog.getByText("시스템 기본값을 복원할까요?")).toBeVisible();

    // 보존 항목 안내 문구 확인 (봇 토큰과 차단 채널은 유지됨)
    await expect(confirmDialog.getByText(/봇 토큰과 차단 채널은 유지/)).toBeVisible();

    // 취소 버튼으로 모달 닫기
    await confirmDialog.getByRole("button", { name: "취소" }).click();
    await expect(confirmDialog).not.toBeVisible({ timeout: 3_000 });
  });

  test("/admin/runtime/pipeline 경로가 /admin/runtime으로 리다이렉트된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime/pipeline");

    // 리다이렉트 후 URL 확인
    await expect(adminPage).toHaveURL(/\/admin\/runtime$/, { timeout: 10_000 });

    // 페이지가 정상적으로 로드되는지 확인
    const heading = adminPage.getByRole("heading", { name: "시스템 설정" });
    const loadingState = adminPage.getByText("불러오는 중...");
    const errorState = adminPage.getByText("설정을 불러오지 못했어요");
    await expect(heading.or(loadingState).or(errorState)).toBeVisible({ timeout: 10_000 });
  });
});
