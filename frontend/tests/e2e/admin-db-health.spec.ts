import { test, expect, adminPage } from "./fixtures/auth";

test.describe("Admin DB Health Dashboard", () => {
  test("DB 상태 페이지 접근 + 기본 렌더링", async ({ adminPage }) => {
    // Navigate to DB health page
    await adminPage.goto("/admin/db-health");

    // Assert KPI card heading "현재 DB 크기" is visible — KpiCard renders an <h2> inside a plain div
    const kpiHeading = adminPage.getByRole("heading", { name: /현재 DB 크기/ });
    await expect(kpiHeading).toBeVisible({ timeout: 10_000 });
    // MB and % values appear within the card section
    const kpiSection = adminPage.locator("div").filter({ hasText: /현재 DB 크기/ }).first();
    const kpiText = await kpiSection.textContent();
    expect(kpiText).toMatch(/MB/);
    expect(kpiText).toMatch(/%/);

    // Assert top tables section is visible — heading reads "테이블별 크기 (Top N)"
    const topTablesSection = adminPage.locator('h2, h3', { hasText: /테이블별 크기/ });
    await expect(topTablesSection.first()).toBeVisible();

    // Assert manual refresh button is visible
    const refreshBtn = adminPage.getByRole("button", { name: /새로고침|Refresh|갱신/ });
    await expect(refreshBtn).toBeVisible();
  });

  test("manual refresh cooldown — 클릭 후 30초간 비활성", async ({ adminPage }) => {
    // Navigate to DB health page
    await adminPage.goto("/admin/db-health");

    // Wait for page to load
    const refreshBtn = adminPage.getByRole("button", { name: /새로고침|Refresh|갱신/ });
    await expect(refreshBtn).toBeVisible({ timeout: 10_000 });

    // Click refresh button
    await refreshBtn.click();

    // Immediately assert button is disabled (cooldown active)
    await expect(refreshBtn).toBeDisabled();
  });
});
