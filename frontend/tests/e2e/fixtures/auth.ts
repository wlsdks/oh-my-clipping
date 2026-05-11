import { test as base, expect, type Page } from "@playwright/test";

const ADMIN_USERNAME = "dev.admin@clipping.local";
const ADMIN_PASSWORD = "LocalPass123!";

export async function adminLogin(page: Page) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login");
    const idInput = page.getByLabel("아이디");
    const pwInput = page.getByLabel("비밀번호");
    const submitBtn = page.getByRole("button", { name: "로그인", exact: true });
    await expect(idInput).toBeVisible({ timeout: 15_000 });
    await idInput.fill(ADMIN_USERNAME);
    await pwInput.fill(ADMIN_PASSWORD);
    await submitBtn.click();
    try {
      await expect(page).toHaveURL(/\/admin/, { timeout: 20_000 });
      await page.waitForTimeout(500);
      return;
    } catch {
      if (attempt === 3) throw new Error("Admin login failed after 3 attempts");
      await page.waitForTimeout(2000);
    }
  }
}

export const test = base.extend<{ adminPage: Page }>({
  adminPage: async ({ page }, use) => {
    await adminLogin(page);
    await use(page);
  }
});

export { expect };
