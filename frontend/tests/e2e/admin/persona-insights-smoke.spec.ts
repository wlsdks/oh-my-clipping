import { test, expect } from "../fixtures/auth";

/**
 * Persona Insights tab 은 마지막 배치가 실패했거나 24h 지났으면 전체 컨텐츠에
 * `aria-hidden="true"` 를 걸어 "배치 실패 배너" 모드로 전환한다 (PR #407 스펙).
 * Dev 환경은 주간 배치가 stale 하므로 실제 API 를 그대로 쓰면 모든 heading 이
 * a11y 트리에서 사라져 스모크 테스트가 실패한다. fresh-run 을 mock 해 banner
 * 비활성 상태에서 렌더 구조만 검증한다.
 */
async function mockFreshBatchRun(adminPage: import("@playwright/test").Page) {
  await adminPage.route("**/api/admin/analytics/personas/batch-runs*", async (route) => {
    const now = new Date().toISOString();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: "smoke-run",
          runId: "smoke-run",
          triggerType: "SCHEDULED",
          weekStart: new Date().toISOString().slice(0, 10),
          startedAt: now,
          finishedAt: now,
          overallStatus: "SUCCESS",
          snapshotStatus: null,
          personasScanned: 0,
          errorMessage: null,
        },
      ]),
    });
  });
}

test.describe("Analytics > Persona Insights tab (smoke)", () => {
  test("Analytics 페이지의 Persona Insights 탭이 렌더된다", async ({ adminPage }) => {
    await mockFreshBatchRun(adminPage);
    await adminPage.goto("/admin/analytics?tab=personas");

    // 탭이 활성화되어 있는지
    await expect(
      adminPage.getByRole("tab", { name: "페르소나 인사이트" })
    ).toBeVisible({ timeout: 10_000 });

    // PR #407 (페르소나 인사이트 redesign) 이후의 4 대 섹션 헤더.
    // 이전 "실시간 현황 / 템플릿 포트폴리오 / 최근 커스텀 스타일" 구조는 삭제됨.
    await expect(
      adminPage.getByRole("heading", { name: "주의가 필요한 페르소나" })
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      adminPage.getByRole("heading", { name: "잘 되고 있는 페르소나" })
    ).toBeVisible();
    await expect(
      adminPage.getByRole("heading", { name: "주간 추이" })
    ).toBeVisible();
    await expect(
      adminPage.getByRole("heading", { name: "포트폴리오" })
    ).toBeVisible();
  });

  test("StyleStatsTab 축소판에서 Analytics 열기 링크로 이동한다", async ({ adminPage }) => {
    await mockFreshBatchRun(adminPage);
    await adminPage.goto("/admin/personas");

    // 사용 통계 탭으로 전환
    await adminPage.getByText("사용 통계").click();

    // Analytics 열기 링크 클릭
    const link = adminPage.getByRole("link", { name: /Analytics 열기/ });
    await expect(link).toBeVisible({ timeout: 10_000 });
    await link.click();

    // Persona Insights 탭으로 이동했는지 — redesign 후 "주의가 필요한 페르소나" 가 최상단 섹션.
    await expect(adminPage).toHaveURL(/\/admin\/analytics.*tab=personas/);
    await expect(
      adminPage.getByRole("heading", { name: "주의가 필요한 페르소나" })
    ).toBeVisible({ timeout: 10_000 });
  });
});
