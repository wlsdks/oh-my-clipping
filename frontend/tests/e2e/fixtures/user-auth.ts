import { test as base, expect, type Page } from "@playwright/test";

const USER_USERNAME = "dev.user@clipping.local";
const USER_PASSWORD = "LocalPass123!";

export async function userLogin(page: Page) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login", { waitUntil: "networkidle" });
    const idInput = page.getByLabel("아이디");
    const pwInput = page.getByLabel("비밀번호");
    const submitBtn = page.getByRole("button", { name: "로그인", exact: true });
    await expect(idInput).toBeVisible({ timeout: 10_000 });
    await idInput.fill(USER_USERNAME);
    await pwInput.fill(USER_PASSWORD);
    await submitBtn.click();
    try {
      await expect(page).toHaveURL(/\/user/, { timeout: 15_000 });
      await page.waitForTimeout(500);
      return;
    } catch {
      if (attempt === 3) throw new Error("User login failed after 3 attempts");
      await page.waitForTimeout(1000);
    }
  }
}

export const test = base.extend<{ userPage: Page }>({
  userPage: async ({ page }, use) => {
    await userLogin(page);
    await use(page);
  },
});

export { expect };
