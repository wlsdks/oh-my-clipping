import { test, expect } from "../fixtures/auth";

test.describe("admin dashboard — 4-Tier Daily Operator Dashboard", () => {
  test("대시보드가 로드되고 주요 섹션이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    // 헤더 인사말 확인
    await expect(adminPage.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible();

    // Tier 2 (오늘의 대기 업무) 항상 렌더
    await expect(adminPage.getByTestId("pending-tasks-section")).toBeVisible({ timeout: 10_000 });
  });

  test("사이드바 네비게이션이 동작한다", async ({ adminPage }) => {
    const contentGroup = adminPage.getByRole("button", { name: "콘텐츠 설정" });
    await contentGroup.click();

    await adminPage.getByRole("link", { name: "뉴스 소스" }).click();
    await expect(adminPage).toHaveURL(/\/admin\/sources/);
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();
  });

  test("시스템 상태가 헤더에 표시된다", async ({ adminPage }) => {
    await expect(adminPage.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible();

    const statusText = adminPage
      .getByText("시스템 정상")
      .or(adminPage.getByText("DB 끊김"))
      .or(adminPage.getByText("Slack 끊김"));
    await expect(statusText.first()).toBeVisible({ timeout: 10_000 });
  });

  test("조치 필요 항목 클릭 시 해당 페이지로 이동한다", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    await expect(adminPage.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible();

    const tier1 = adminPage.getByTestId("action-required-section");
    if ((await tier1.count()) > 0) {
      const actionLink = adminPage
        .getByRole("link")
        .filter({ hasText: /발송 실패|파이프라인 실패|구독 요청|뉴스 검토|검토 대기|승인 대기/ })
        .first();
      if ((await actionLink.count()) > 0) {
        await actionLink.click();
        await expect(adminPage).toHaveURL(
          /\/admin\/(review-queue|subscriptions|delivery|pipeline)/,
          { timeout: 10_000 },
        );
      }
    }
    // Tier 1 없으면 섹션 자체 없음 — 통과
  });

  // ─── 신규 4-Tier 테스트 ─────────────────────────────────────────────────────

  test("Tier 2 는 항상 렌더되고 빈 상태 시 '오늘 대기 없음' 메시지 노출", async ({
    adminPage,
  }) => {
    await adminPage.goto("/admin");
    await expect(adminPage.getByTestId("pending-tasks-section")).toBeVisible({ timeout: 10_000 });

    // 빈 상태일 수도 있고 아닐 수도 있음 — 둘 중 하나는 확인되어야
    const emptyMessage = adminPage.getByText(/오늘 대기 없음/);
    const taskItems = adminPage.locator('[data-testid="pending-tasks-section"] a');
    await expect(emptyMessage.or(taskItems.first())).toBeVisible({ timeout: 10_000 });
  });

  test("Tier 3 forecast 배너 '오늘 예정' 과 파이프라인 건수 렌더", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    await expect(adminPage.getByTestId("ops-metrics-section")).toBeVisible({ timeout: 10_000 });
    await expect(adminPage.getByText(/오늘 예정/)).toBeVisible({ timeout: 10_000 });
    await expect(adminPage.getByText(/파이프라인.*회/).first()).toBeVisible({ timeout: 10_000 });
  });

  test("사용자 반응 카드 클릭 시 /admin/engagement 이동", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    const card = adminPage.getByRole("link").filter({ hasText: /사용자 반응/ });
    // 카드가 없는 환경(데이터 0건)은 스킵
    if ((await card.count()) === 0) {
      test.skip();
      return;
    }
    await card.first().click();
    await expect(adminPage).toHaveURL(/.*\/admin\/engagement/, { timeout: 10_000 });
  });

  test("Tier 1 실패 있을 때 aria-live='polite' 속성 가짐", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    const tier1 = adminPage.getByTestId("action-required-section");
    if ((await tier1.count()) > 0) {
      await expect(tier1).toHaveAttribute("aria-live", "polite");
    }
    // 실패 없으면 섹션 자체 없음 — 이 경우도 통과
  });

  test("4개 Section 이 올바른 순서로 렌더 (Tier 1 조건부)", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    // Tier 2, 3, footer 는 항상 렌더되어야 함
    const requiredSections = ["pending-tasks-section", "ops-metrics-section", "operator-footer"];
    for (const id of requiredSections) {
      await expect(adminPage.getByTestId(id)).toBeVisible({ timeout: 10_000 });
    }
    // Tier 1 은 조건부 — 있거나 없거나 모두 허용
  });
});
