import { test } from "@playwright/test";
import { checkA11y } from "../helpers/a11y";

test.describe("Accessibility", () => {
  test("login page has no a11y violations", async ({ page }) => {
    await page.goto("/login");
    // 페이지가 완전히 로드될 때까지 대기
    await page.waitForLoadState("networkidle");
    // framer-motion stagger 애니메이션 완료까지 대기 (총 ~1s: 0.3s delay + 0.2s stagger*3 + 0.4s duration)
    await page.waitForTimeout(1200);
    await checkA11y(page, "login page");
  });

  test("signup page has no a11y violations", async ({ page }) => {
    await page.goto("/signup");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1200);
    await checkA11y(page, "signup page");
  });
});
