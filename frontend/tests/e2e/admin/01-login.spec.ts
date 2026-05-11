import { test, expect } from "../fixtures/auth";

test.describe("admin login / logout", () => {
  test("wrong password shows error message", async ({ page }) => {
    await page.goto("/login");
    // dev shortcuts 로드 대기 — 로그인 버튼이 나타날 때까지 기다린다
    await expect(page.getByRole("button", { name: "로그인", exact: true })).toBeVisible({ timeout: 10_000 });
    await page.getByLabel("아이디").fill("wronguser");
    await page.getByLabel("비밀번호").fill("wrong-pass");
    await page.getByRole("button", { name: "로그인", exact: true }).click();

    // 에러 시 toast 알림 표시 — userFriendlyMessage가 "로그인에 실패했어요" 컨텍스트로 변환
    await expect(page.getByText(/로그인에 실패했어요|로그인 실패|아이디 또는 비밀번호가 올바르지 않습니다/)).toBeVisible({
      timeout: 10_000
    });
  });

  test("dev shortcut login reaches dashboard", async ({ adminPage }) => {
    await expect(adminPage).toHaveURL(/\/admin/);
    // Admin shell 은 desktop sidebar(<aside>) + mobile bottom nav(<nav>) 둘 다 DOM에 둔다
    // (CSS breakpoint 로만 show/hide). 사이드바 존재만 확인해 strict mode 위반 회피.
    await expect(adminPage.getByRole("complementary", { name: "사이드바 내비게이션" })).toBeAttached();
  });

  test("logout redirects to login page", async ({ adminPage }) => {
    await adminPage.getByRole("button", { name: "로그아웃" }).click();

    await expect(adminPage).toHaveURL(/\/login/);
    await expect(adminPage.getByRole("button", { name: "로그인", exact: true })).toBeVisible();
  });

  test("미인증 상태에서 /admin 접근 시 로그인으로 리다이렉트된다", async ({ page }) => {
    // 로그인하지 않은 상태로 /admin 직접 접근
    await page.goto("/admin");

    // 로그인 페이지로 리다이렉트
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
    await expect(page.getByRole("button", { name: "로그인", exact: true })).toBeVisible();
  });

  test("유저 권한으로 /admin 접근 시 접근이 거부된다", async ({ page }) => {
    // 유저 권한으로 로그인
    await page.goto("/login");
    await expect(page.getByLabel("아이디")).toBeVisible({ timeout: 10_000 });
    await page.getByLabel("아이디").fill("dev.user@clipping.local");
    await page.getByLabel("비밀번호").fill("LocalPass123!");
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    await expect(page).toHaveURL(/\/user/, { timeout: 10_000 });

    // 유저 권한으로 /admin 접근 시도
    await page.goto("/admin");

    // Spring Security 403 페이지("Access Denied" 텍스트), 로그인 리다이렉트, 또는 유저 페이지 리다이렉트 중 하나
    const accessDenied = page.getByText("Access Denied");
    const loginPage = page.getByRole("button", { name: "로그인", exact: true });
    const userHome = page.getByRole("heading", { name: "홈" });

    await expect(accessDenied.or(loginPage).or(userHome)).toBeVisible({ timeout: 10_000 });
  });
});
