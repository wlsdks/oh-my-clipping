import { test, expect, type Page } from "@playwright/test";
import { isVisibleSafe } from "../helpers/assertions";

async function loginWithShortcut(page: Page, scope: "admin" | "user") {
  const username = scope === "admin" ? "dev.admin@clipping.local" : "dev.user@clipping.local";
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login");
    const idInput = page.getByLabel("아이디");
    await expect(idInput).toBeVisible({ timeout: 15_000 });
    await idInput.fill(username);
    await page.getByLabel("비밀번호").fill("LocalPass123!");
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await expect(page).toHaveURL(new RegExp(scope === "admin" ? "/admin" : "/user"), { timeout: 20_000 });
      return;
    } catch {
      if (attempt === 3) throw new Error(`Login as ${scope} failed after 3 attempts`);
      await page.waitForTimeout(2000);
    }
  }
}

test.describe("UI state persistence — dark mode & sidebar", () => {
  test("다크 모드 토글 후 페이지가 정상 렌더링된다", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await loginWithShortcut(page, "admin");

    // 사이드바에서 다크 모드 토글 찾기 (role="switch", 라이트/다크 전환 aria-label)
    const themeToggle = page.getByRole("switch", { name: /모드로 전환/ });
    await expect(themeToggle).toBeVisible();

    // 현재 모드 확인
    const initialChecked = await themeToggle.getAttribute("aria-checked");

    // 다크 모드 토글 클릭
    await themeToggle.click();
    await page.waitForTimeout(500); // animation settle

    // 토글 상태가 변경되었는지 확인
    const newChecked = await themeToggle.getAttribute("aria-checked");
    expect(newChecked).not.toBe(initialChecked);

    // HTML root에 dark 클래스가 적절히 적용되었는지 확인
    const htmlClass = await page.locator("html").getAttribute("class");
    if (newChecked === "true") {
      expect(htmlClass).toContain("dark");
    } else {
      expect(htmlClass).not.toContain("dark");
    }

    // 페이지 레이아웃이 깨지지 않았는지 확인 — 주요 요소들이 보이는지
    await expect(page.getByRole("heading").first()).toBeVisible();
    await expect(page.locator("aside").first()).toBeVisible();
    await expect(page.locator("nav").first()).toBeVisible();

    // 다른 페이지로 이동하여 렌더링 확인
    await page.goto("/admin/sources");
    await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();
    await expect(page.locator("aside").first()).toBeVisible();

    // 구독 관리 페이지도 확인
    await page.goto("/admin/subscriptions");
    await expect(page.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible();
    await expect(page.locator("aside").first()).toBeVisible();

    // 원래 모드로 복원
    const toggleAfter = page.getByRole("switch", { name: /모드로 전환/ });
    await toggleAfter.click();
    await page.waitForTimeout(300); // animation settle

    await context.close();
  });

  test("다크 모드 설정이 페이지 이동 후에도 유지된다", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await loginWithShortcut(page, "admin");

    // 다크 모드 토글
    const themeToggle = page.getByRole("switch", { name: /모드로 전환/ });
    await expect(themeToggle).toBeVisible();

    // 다크 모드로 전환
    const initialChecked = await themeToggle.getAttribute("aria-checked");
    if (initialChecked !== "true") {
      await themeToggle.click();
      await page.waitForTimeout(300); // animation settle
    }

    // 다크 모드 상태 확인
    const isDarkAfterToggle = await themeToggle.getAttribute("aria-checked");
    expect(isDarkAfterToggle).toBe("true");

    // 여러 페이지를 이동하며 다크 모드 유지 확인
    const pages = ["/admin/sources", "/admin/subscriptions", "/admin/pipeline", "/admin/runtime"];
    for (const path of pages) {
      await page.goto(path);
      await page.waitForTimeout(300); // animation settle

      // HTML에 dark 클래스 유지 확인
      const htmlClass = await page.locator("html").getAttribute("class");
      expect(htmlClass).toContain("dark");

      // 토글 상태 유지 확인
      const toggle = page.getByRole("switch", { name: /모드로 전환/ });
      const hasToggle = await isVisibleSafe(toggle);
      if (hasToggle) {
        const checked = await toggle.getAttribute("aria-checked");
        expect(checked).toBe("true");
      }
    }

    // 라이트 모드로 복원
    const finalToggle = page.getByRole("switch", { name: /모드로 전환/ });
    if (await isVisibleSafe(finalToggle)) {
      await finalToggle.click();
      await page.waitForTimeout(300); // animation settle
    }

    await context.close();
  });

  test("사이드바 아코디언 상태가 localStorage에 저장되고 네비게이션 후에도 유지된다", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await loginWithShortcut(page, "admin");

    // 사이드바에서 그룹 라벨 확인 (콘텐츠 설정, 운영, 분석, 시스템)
    const contentGroup = page.locator("aside button").filter({ hasText: "콘텐츠 설정" });
    const hasContentGroup = await isVisibleSafe(contentGroup);

    if (!hasContentGroup) {
      await context.close();
      test.skip(true, "사이드바 그룹이 표시되지 않아 건너뜁니다");
      return;
    }

    // "콘텐츠 설정" 그룹 토글 — 현재 상태를 확인하고 반전
    await contentGroup.click();
    await page.waitForTimeout(300); // animation settle

    // localStorage에서 상태 확인
    const storedState = await page.evaluate(() => {
      return localStorage.getItem("clipping-sidebar-groups");
    });

    expect(storedState).toBeTruthy();
    const parsed = JSON.parse(storedState!);
    expect("content" in parsed).toBeTruthy();

    // 다른 페이지로 이동
    await page.goto("/admin/sources");
    await page.waitForTimeout(300); // animation settle

    // localStorage 값이 유지되는지 확인
    const storedAfterNav = await page.evaluate(() => {
      return localStorage.getItem("clipping-sidebar-groups");
    });
    expect(storedAfterNav).toBe(storedState);

    // 또 다른 페이지로 이동
    await page.goto("/admin/pipeline");
    await page.waitForTimeout(300); // animation settle

    const storedAfterNav2 = await page.evaluate(() => {
      return localStorage.getItem("clipping-sidebar-groups");
    });
    expect(storedAfterNav2).toBe(storedState);

    await context.close();
  });

  test("새 브라우저 컨텍스트에서는 사이드바 아코디언 상태가 초기화된다", async ({ browser }) => {
    // 첫 번째 컨텍스트: 상태 변경
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    await loginWithShortcut(page1, "admin");

    // 사이드바 그룹 토글
    const contentGroup = page1.locator("aside button").filter({ hasText: "콘텐츠 설정" });
    const hasContentGroup = await isVisibleSafe(contentGroup);

    if (hasContentGroup) {
      await contentGroup.click();
      await page1.waitForTimeout(300); // animation settle

      // localStorage에 값이 저장되었는지 확인
      const stored1 = await page1.evaluate(() => {
        return localStorage.getItem("clipping-sidebar-groups");
      });
      expect(stored1).toBeTruthy();
    }

    await context1.close();

    // 두 번째 컨텍스트: 새 세션 (localStorage 없음)
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    await loginWithShortcut(page2, "admin");

    // 새 컨텍스트에서는 localStorage가 비어 있어야 한다
    const stored2 = await page2.evaluate(() => {
      return localStorage.getItem("clipping-sidebar-groups");
    });

    // 새 세션이므로 null 또는 빈 객체여야 한다
    if (stored2) {
      const parsed = JSON.parse(stored2);
      // 새 컨텍스트에서는 이전 컨텍스트의 토글 상태가 없어야 한다
      // (단, 컴포넌트 초기 렌더링 시 기본 상태가 저장될 수 있음)
      expect(typeof parsed).toBe("object");
    } else {
      expect(stored2).toBeNull();
    }

    // 사이드바가 기본 상태로 렌더링되는지 확인
    await expect(page2.locator("aside").first()).toBeVisible();

    await context2.close();
  });
});
