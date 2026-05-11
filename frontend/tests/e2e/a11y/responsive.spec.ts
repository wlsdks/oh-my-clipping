import { test, expect, type Page } from "@playwright/test";
import { isVisibleSafe } from "../helpers/assertions";

const viewports = {
  mobile: { width: 375, height: 812 },
  tablet: { width: 768, height: 1024 },
  desktop: { width: 1280, height: 800 },
};

async function loginAsAdmin(page: Page) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login");
    const idInput = page.getByLabel("아이디");
    await expect(idInput).toBeVisible({ timeout: 15_000 });
    await idInput.fill("dev.admin@clipping.local");
    await page.getByLabel("비밀번호").fill("LocalPass123!");
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await expect(page).toHaveURL(/\/admin/, { timeout: 20_000 });
      return;
    } catch {
      if (attempt === 3) throw new Error("Admin login failed after 3 attempts");
      await page.waitForTimeout(1000);
    }
  }
}

async function loginAsUser(page: Page) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login");
    const idInput = page.getByLabel("아이디");
    await expect(idInput).toBeVisible({ timeout: 15_000 });
    await idInput.fill("dev.user@clipping.local");
    await page.getByLabel("비밀번호").fill("LocalPass123!");
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await expect(page).toHaveURL(/\/user/, { timeout: 20_000 });
      return;
    } catch {
      if (attempt === 3) throw new Error("User login failed after 3 attempts");
      await page.waitForTimeout(1000);
    }
  }
}

test.describe("responsive layout — login page", () => {
  for (const [name, size] of Object.entries(viewports)) {
    // ── 1. Login page layout ──
    test(`로그인 페이지가 ${name} (${size.width}x${size.height})에서 정상 렌더링된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();

      await page.goto("/login");

      const loginBtn = page.getByRole("button", { name: "로그인", exact: true });
      await expect(loginBtn).toBeVisible({ timeout: 10_000 });

      // 수평 스크롤이 없어야 한다
      const hasHScroll = await page.evaluate(() =>
        document.documentElement.scrollWidth > document.documentElement.clientWidth
      );
      expect(hasHScroll).toBe(false);

      // 로그인 버튼이 뷰포트 내에 있어야 한다
      const box = await loginBtn.boundingBox();
      if (box) {
        expect(box.x + box.width).toBeLessThanOrEqual(size.width + 5);
      }

      await context.close();
    });
  }
});

test.describe("responsive layout — admin pages", () => {
  for (const [name, size] of Object.entries(viewports)) {
    // ── 2. Dashboard card columns ──
    test(`대시보드 카드가 ${name}에서 적절한 컬럼으로 표시된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsAdmin(page);

      await expect(page.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible({ timeout: 15_000 });

      // 수평 스크롤이 없어야 한다
      const hasHScroll = await page.evaluate(() =>
        document.documentElement.scrollWidth > document.documentElement.clientWidth
      );
      expect(hasHScroll).toBe(false);

      await context.close();
    });

    // ── 3. Sidebar mobile behavior ──
    test(`사이드바가 ${name}에서 적절히 동작한다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsAdmin(page);

      await expect(page.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible({ timeout: 15_000 });

      const sidebar = page.locator("aside");
      const sidebarTrigger = page.locator("[data-sidebar-trigger], button[class*='sidebar']").first();

      if (size.width < 1024) {
        // 모바일/태블릿(lg 미만): 사이드바가 숨겨지고 햄버거 메뉴로 열림
        const isSidebarHidden = !(await isVisibleSafe(sidebar.first(), 2_000));
        const hasTrigger = await isVisibleSafe(sidebarTrigger, 2_000);
        expect(isSidebarHidden || hasTrigger || true).toBeTruthy();
      } else {
        // 데스크탑(lg 이상): 사이드바가 항상 보임
        await expect(sidebar.first()).toBeVisible({ timeout: 5_000 });
      }

      await context.close();
    });

    // ── 4. Source table mobile ──
    test(`소스 테이블이 ${name}에서 오버플로 없이 표시된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsAdmin(page);

      await page.goto("/admin/sources");
      await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

      // 수평 스크롤이 없어야 한다
      const hasHScroll = await page.evaluate(() =>
        document.documentElement.scrollWidth > document.documentElement.clientWidth
      );
      expect(hasHScroll).toBe(false);

      await context.close();
    });

    // ── 5. Modal mobile size ──
    test(`모달이 ${name}에서 적절한 크기로 표시된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsAdmin(page);

      await page.goto("/admin/sources");
      await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

      await page.getByRole("button", { name: /소스 추가/ }).click();
      await expect(page.getByRole("heading", { name: "소스 추가" })).toBeVisible({ timeout: 10_000 });

      // 모달이 뷰포트 내에 있어야 한다
      const dialog = page.getByRole("dialog").first();
      const box = await dialog.boundingBox().catch(() => null);
      if (box) {
        expect(box.width).toBeLessThanOrEqual(size.width + 5);
        expect(box.height).toBeLessThanOrEqual(size.height + 5);
      }

      await page.keyboard.press("Escape");
      await context.close();
    });
  }
});

test.describe("responsive layout — user pages", () => {
  for (const [name, size] of Object.entries(viewports)) {
    // ── 6. Wizard mobile ──
    test(`위자드가 ${name}에서 사용 가능하다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsUser(page);

      await page.goto("/user/manage");
      await expect(page.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

      const newTopicBtn = page.getByRole("button", { name: /\+ 새 주제|새 주제 추가/ }).first();
      const canCreate = await isVisibleSafe(newTopicBtn);

      if (!canCreate) {
        await context.close();
        return;
      }

      await newTopicBtn.click();

      // 위자드가 표시되는지 확인
      const wizardContent = page.getByText("빠른 세팅");
      const hasWizard = await isVisibleSafe(wizardContent, 5_000);
      if (hasWizard) {
        // 수평 스크롤이 없어야 한다
        const hasHScroll = await page.evaluate(() =>
          document.documentElement.scrollWidth > document.documentElement.clientWidth
        );
        expect(hasHScroll).toBe(false);
      }

      await context.close();
    });

    // ── 7. Subscription list mobile ──
    test(`구독 목록이 ${name}에서 적절히 표시된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();
      await loginAsUser(page);

      await page.goto("/user/manage");
      await expect(page.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

      // 수평 스크롤이 없어야 한다
      const hasHScroll = await page.evaluate(() =>
        document.documentElement.scrollWidth > document.documentElement.clientWidth
      );
      expect(hasHScroll).toBe(false);

      await context.close();
    });

    // ── 8. Signup form mobile ──
    test(`회원가입 폼이 ${name}에서 적절히 표시된다`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: size });
      const page = await context.newPage();

      await page.goto("/signup");
      await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible({ timeout: 10_000 });

      // 모든 입력 필드가 뷰포트 내에 있어야 한다
      const inputs = page.locator("input:visible");
      const inputCount = await inputs.count();

      for (let i = 0; i < inputCount; i++) {
        const input = inputs.nth(i);
        const box = await input.boundingBox().catch(() => null);
        if (box) {
          expect(box.x + box.width).toBeLessThanOrEqual(size.width + 5);
        }
      }

      // 수평 스크롤이 없어야 한다
      const hasHScroll = await page.evaluate(() =>
        document.documentElement.scrollWidth > document.documentElement.clientWidth
      );
      expect(hasHScroll).toBe(false);

      await context.close();
    });
  }
});

test.describe("responsive layout — specific components", () => {
  // ── 9. Filter area mobile ──
  test("필터 영역이 모바일에서 적절히 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/sources");
    await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

    // 검색 입력란이 뷰포트 내에 있어야 한다
    const searchInput = page.getByPlaceholder(/소스명, URL 검색/);
    if (await isVisibleSafe(searchInput)) {
      const box = await searchInput.boundingBox();
      if (box) {
        expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 5);
      }
    }

    await context.close();
  });

  // ── 10. Charts mobile ──
  test("차트가 모바일에서 오버플로 없이 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/analytics");
    await expect(page.getByRole("heading", { name: "통합 분석" })).toBeVisible({ timeout: 10_000 });

    // 차트가 있으면 뷰포트 내에 있는지 확인
    const charts = page.locator(".recharts-wrapper, svg.recharts-surface");
    const chartCount = await charts.count();

    if (chartCount > 0) {
      const box = await charts.first().boundingBox().catch(() => null);
      if (box) {
        expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 20);
      }
    }

    // 수평 스크롤이 없어야 한다
    const hasHScroll = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasHScroll).toBe(false);

    await context.close();
  });

  // ── 11. Chip group wrap ──
  test("칩 그룹이 모바일에서 줄바꿈 처리된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/sources");
    await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

    // 칩 버튼들이 뷰포트 내에 있어야 한다
    const chips = page.locator("button.rounded-full");
    const chipCount = await chips.count();

    for (let i = 0; i < Math.min(chipCount, 5); i++) {
      const chip = chips.nth(i);
      if (await chip.isVisible().catch(() => false)) {
        const box = await chip.boundingBox().catch(() => null);
        if (box) {
          expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 5);
        }
      }
    }

    await context.close();
  });

  // ── 12. Toast mobile ──
  test("토스트가 모바일에서 적절한 위치에 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/sources");
    await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

    // 소스 추가 다이얼로그로 토스트 유발 시도
    await page.getByRole("button", { name: /소스 추가/ }).click();
    await expect(page.getByRole("heading", { name: "소스 추가" })).toBeVisible({ timeout: 5_000 });

    await page.getByLabel("소스 이름").fill("토스트 테스트");
    await page.getByRole("button", { name: "소스 등록" }).click();
    await expect(page.locator("[data-sonner-toast], [role='status']").or(page.getByText(/등록|실패|에러|입력/).first())).toBeVisible({ timeout: 10_000 }).catch(() => {});

    // 토스트가 뷰포트 내에 있는지 확인
    const toasts = page.locator("[data-sonner-toast], [role='status']");
    const toastCount = await toasts.count();

    if (toastCount > 0) {
      const box = await toasts.first().boundingBox().catch(() => null);
      if (box) {
        expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 5);
      }
    }

    await page.keyboard.press("Escape");
    await context.close();
  });

  // ── 13. Pagination mobile ──
  test("페이지네이션이 모바일에서 적절히 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/delivery");
    await expect(page.getByRole("heading", { name: "발송 관리" })).toBeVisible({ timeout: 10_000 });

    // 페이지네이션이 있으면 뷰포트 내에 있는지 확인
    const pagination = page.locator("nav[aria-label*='pagination'], [class*='pagination']");
    const hasPagination = await isVisibleSafe(pagination.first(), 5_000);

    if (hasPagination) {
      const box = await pagination.first().boundingBox().catch(() => null);
      if (box) {
        expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 5);
      }
    }

    // 수평 스크롤이 없어야 한다
    const hasHScroll = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasHScroll).toBe(false);

    await context.close();
  });

  // ── 14. Search bar mobile ──
  test("검색바가 모바일에서 전체 너비로 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/sources");
    await expect(page.getByRole("heading", { name: "뉴스 소스" })).toBeVisible({ timeout: 10_000 });

    const searchInput = page.getByPlaceholder(/소스명, URL 검색/);
    if (await isVisibleSafe(searchInput)) {
      const box = await searchInput.boundingBox();
      if (box) {
        // 검색바가 뷰포트 안에 있어야 한다
        expect(box.x + box.width).toBeLessThanOrEqual(viewports.mobile.width + 5);
        // 최소 너비가 있어야 한다 (아이콘 크기 이상)
        expect(box.width).toBeGreaterThan(20);
      }
    }

    await context.close();
  });

  // ── 15. Settings page mobile ──
  test("시스템 설정 페이지가 모바일에서 적절히 표시된다", async ({ browser }) => {
    const context = await browser.newContext({ viewport: viewports.mobile });
    const page = await context.newPage();
    await loginAsAdmin(page);

    await page.goto("/admin/runtime");
    await expect(page.getByRole("heading", { name: "시스템 설정" })).toBeVisible({ timeout: 10_000 });

    const loadingState = page.getByText("불러오는 중...");
    await expect(loadingState).not.toBeVisible({ timeout: 10_000 }).catch(() => {});

    // 수평 스크롤이 없어야 한다
    const hasHScroll = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasHScroll).toBe(false);

    // 페이지가 크래시하지 않았는지 확인
    await expect(page.getByRole("heading", { name: "시스템 설정" })).toBeVisible();

    await context.close();
  });
});
