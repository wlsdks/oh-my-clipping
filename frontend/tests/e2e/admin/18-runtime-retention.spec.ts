import { test, expect } from "../fixtures/auth";
import type { Page } from "@playwright/test";

test.describe("runtime settings — data retention card", () => {
  /** 페이지 로딩 완료를 기다리는 헬퍼 */
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

  test("데이터 보관 정책 섹션을 펼칠 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    // 엔드포인트 오류 시 조용히 건너뛰지 않고 실패시켜 회귀를 명확히 드러낸다.
    expect(isError, "설정 엔드포인트 오류 — 서버 상태를 확인하세요").toBe(false);

    // 접힌 상태 — 필드가 보이지 않아야 함
    await expect(adminPage.locator("#retentionRssItemsDays")).not.toBeVisible();

    // "데이터 보관 정책" 섹션 헤더 클릭하여 펼침
    await adminPage.getByRole("button", { name: /데이터 보관 정책/ }).click();

    // 펼쳐진 후 필드 확인
    await expect(adminPage.locator("#retentionRssItemsDays")).toBeVisible({ timeout: 5_000 });
    await expect(adminPage.getByText("원본 기사 보관 (일)")).toBeVisible();
    await expect(adminPage.locator("#retentionBatchSummariesDays")).toBeVisible();
    await expect(adminPage.getByText("AI 요약 보관 (일)")).toBeVisible();
  });

  test("보관 기간을 수정하고 저장할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    expect(isError, "설정 엔드포인트 오류 — 서버 상태를 확인하세요").toBe(false);

    // 데이터 보관 정책 섹션 펼침
    const retentionButton = adminPage.getByRole("button", { name: /데이터 보관 정책/ });
    await expect(retentionButton).toBeVisible();
    await retentionButton.click();

    // 필드 변경
    const rssInput = adminPage.locator("#retentionRssItemsDays");
    const summaryInput = adminPage.locator("#retentionBatchSummariesDays");

    await expect(rssInput).toBeVisible({ timeout: 5_000 });
    await expect(summaryInput).toBeVisible();

    // 이전 값 저장
    const originalRssValue = await rssInput.inputValue();
    const originalSummaryValue = await summaryInput.inputValue();

    // 새 값으로 변경
    const newRssValue = String(Math.max(7, Number(originalRssValue) === 45 ? 30 : 45));
    const newSummaryValue = String(
      Math.max(7, Number(originalSummaryValue) === 120 ? 90 : 120)
    );

    await rssInput.clear();
    await rssInput.fill(newRssValue);
    await summaryInput.clear();
    await summaryInput.fill(newSummaryValue);

    // 저장 버튼 클릭 (CollapsibleSection 내 첫 번째 저장 버튼)
    const retentionCard = adminPage.locator("section").filter({ hasText: "데이터 보관 정책" });
    const saveResponsePromise = adminPage.waitForResponse(
      (response) =>
        response.url().includes("/api/admin/runtime-settings") &&
        response.request().method() === "PUT"
    );
    await retentionCard.getByRole("button", { name: "저장" }).click();
    const saveResponse = await saveResponsePromise;
    expect(saveResponse.ok(), `runtime settings save failed: ${saveResponse.status()}`).toBe(true);

    // 토스트 확인
    const successToast = adminPage.getByText(/설정을 저장했어요/);
    await expect(successToast).toBeVisible({ timeout: 10_000 });

    // 성공 토스트 확인
    // 페이지 재로드 후 값 영속성 확인
    await adminPage.reload();
    await waitForPageLoaded(adminPage);
    await retentionButton.click();

    await expect(rssInput).toBeVisible({ timeout: 5_000 });
    const persistedRssValue = await rssInput.inputValue();
    expect(persistedRssValue).toBe(newRssValue);

    const persistedSummaryValue = await summaryInput.inputValue();
    expect(persistedSummaryValue).toBe(newSummaryValue);
  });

  test("6일 입력 시 검증 에러가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");

    const { isError } = await waitForPageLoaded(adminPage);
    expect(isError, "설정 엔드포인트 오류 — 서버 상태를 확인하세요").toBe(false);

    // 데이터 보관 정책 섹션 펼침
    const retentionButton = adminPage.getByRole("button", { name: /데이터 보관 정책/ });
    await retentionButton.click();

    // RSS 필드에 6일(최소값 미만) 입력
    const rssInput = adminPage.locator("#retentionRssItemsDays");
    await expect(rssInput).toBeVisible({ timeout: 5_000 });

    await rssInput.clear();
    await rssInput.fill("6");

    const retentionCard = adminPage.locator("section").filter({ hasText: "데이터 보관 정책" });
    const saveButton = retentionCard.getByRole("button", { name: "저장" });
    await saveButton.click();

    // 검증 에러 메시지 확인
    const validationError = adminPage.locator(".text-destructive", { hasText: "최소 7일" });
    await expect(validationError).toBeVisible({ timeout: 5_000 });

    // 첫 수정 이후 저장 버튼은 활성화되지만, 유효한 값이 아니면 click 시 폼 validation으로 실패
    await expect(saveButton).toBeVisible();
  });
});
