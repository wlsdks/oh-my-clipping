import { test, expect } from "./fixtures/auth";

test.describe("Source Quality Page", () => {
  test("페이지 노출 — heading + KPI + 테이블", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources/quality");
    await expect(adminPage.getByRole("heading", { name: "RSS 소스 품질" })).toBeVisible();
    await expect(adminPage.getByTestId("source-quality-kpi-cards")).toBeVisible();
    await expect(adminPage.getByTestId("source-quality-table")).toBeVisible();
  });

  test("구 URL 은 새 경로로 redirect 된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/content-levers");
    await expect(adminPage).toHaveURL(/\/admin\/sources\/quality/);
  });

  test("상태 필터 변경 → 행 수 변화", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources/quality");
    await expect(adminPage.getByTestId("source-quality-table")).toBeVisible();
    const initialCount = await adminPage.getByTestId("source-row").count();
    await adminPage.getByRole("radio", { name: "검토 필요" }).click();
    const filteredCount = await adminPage.getByTestId("source-row").count();
    expect(filteredCount).toBeLessThanOrEqual(initialCount);
  });
});
