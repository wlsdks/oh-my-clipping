import { expect, type Page, type Locator } from "@playwright/test";

/** Assert a toast notification appears with given text */
export async function expectToast(page: Page, message: string | RegExp, timeout = 10_000) {
  const toast = page.getByText(message);
  await expect(toast.first()).toBeVisible({ timeout });
}

/** Assert either content or empty state is visible */
export async function expectContentOrEmpty(content: Locator, empty: Locator, timeout = 10_000) {
  await expect(content.first().or(empty.first())).toBeVisible({ timeout });
}

/** Assert page loads with a heading */
export async function expectPageLoad(page: Page, heading: string | RegExp, timeout = 10_000) {
  const h = page.getByRole("heading", { name: heading });
  const fallback = page.getByText(heading);
  await expect(h.first().or(fallback.first())).toBeVisible({ timeout });
}

/** Assert no English error messages are visible (AGENTS.md section 8.3.5 compliance) */
export async function expectNoEnglishError(page: Page) {
  const englishPatterns = [
    "scheme is required",
    "parsing failed",
    "Connection timed out",
    "validation failed",
    "Internal Server Error",
    "Bad Request",
    "Not Found",
    "Forbidden",
  ];
  for (const pattern of englishPatterns) {
    await expect(page.getByText(pattern, { exact: false })).toHaveCount(0);
  }
}

/** Check element is visible with fallback for flaky states */
export async function isVisibleSafe(locator: Locator, timeout = 5_000): Promise<boolean> {
  return locator
    .isVisible({ timeout })
    .catch(() => false);
}
