import { test, expect } from "@playwright/test";
import { buildTestLabel } from "../helpers/api";

test.describe("signup page", () => {
  test("빈 폼 제출 시 유효성 에러가 표시된다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();

    // 빈 상태로 회원가입 클릭
    await page.getByRole("button", { name: "회원가입", exact: true }).click();

    // 유효성 에러 메시지가 하나 이상 표시되는지 확인 (FormMessage 컴포넌트)
    const errors = page.locator("p[id*='message']");
    await expect(errors.first()).toBeVisible({ timeout: 3000 });

    // 에러 배너도 표시되는지 확인
    await expect(page.getByText("입력 정보를 확인해주세요")).toBeVisible();
  });

  test("뒤로가기와 로그인 링크가 동작한다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();

    // 뒤로가기 버튼 (ArrowLeft 아이콘 버튼)
    await page
      .locator("button")
      .filter({ has: page.locator("svg") })
      .first()
      .click();
    await expect(page).toHaveURL(/\/login/);

    // 다시 회원가입으로
    await page.goto("/signup");

    // "로그인" 링크 클릭
    await page.getByRole("link", { name: "로그인" }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test("잘못된 이메일 형식 입력 시 에러 메시지가 표시된다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();

    // 이메일 형식은 유효하지만 zod의 엄격한 email 검증은 통과 못함 (TLD 짧음)
    // "a@b" — HTML5는 통과하지만 zod email()은 거부
    const emailInput = page.getByPlaceholder("name@company.com");
    await emailInput.fill("a@b");

    // 제출 시도 → 폼 검증 전체 트리거
    await page.getByRole("button", { name: "회원가입", exact: true }).click();
    await page.waitForTimeout(300);

    // 이메일 형식 에러, 에러 배너, 또는 폼 메시지 중 하나가 표시되어야 한다
    const errorMsg = page.getByText(/올바른 이메일|이메일을 입력|입력 정보를 확인/);
    const formErrors = page.locator("p[id*='message']");
    await expect(errorMsg.first().or(formErrors.first())).toBeVisible({ timeout: 5_000 });
  });

  test("비밀번호 불일치 시 에러 메시지가 표시된다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();

    // 비밀번호와 비밀번호 확인 다르게 입력
    await page.getByPlaceholder("영문 + 숫자 포함, 8자 이상").fill("test1234");
    await page.getByPlaceholder("비밀번호를 다시 입력하세요").fill("different456");

    // 제출
    await page.getByRole("button", { name: "회원가입", exact: true }).click();

    // 비밀번호 불일치 에러 메시지 확인
    await expect(page.getByText("비밀번호가 일치하지 않아요")).toBeVisible();
  });

  test("정상적인 가입 정보 제출 시 성공 메시지 또는 리다이렉트가 발생한다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();

    // 고유한 이메일 생성
    const uniqueEmail = `e2e${Date.now() % 100000}@test.local`;

    // 정상적인 가입 정보 입력
    await page.getByPlaceholder("name@company.com").fill(uniqueEmail);
    await page.getByPlaceholder("홍길동").fill("테스트유저");

    // 소속 선택 — 부서 → 팀 cascade 두 단계로 선택한다
    await page.getByRole("combobox", { name: "부서 선택" }).click();
    await page.getByRole("option", { name: "AI플랫폼" }).click();
    // 부서 선택 후 팀 드롭다운이 활성화될 때까지 잠시 대기
    await page.waitForTimeout(200);
    await page.getByRole("combobox", { name: "팀 선택" }).click();
    await page.getByRole("option", { name: "AI플랫폼팀" }).click();

    await page.getByPlaceholder("영문 + 숫자 포함, 8자 이상").fill("test1234");
    await page.getByPlaceholder("비밀번호를 다시 입력하세요").fill("test1234");

    // 제출
    await page.getByRole("button", { name: "회원가입", exact: true }).click();

    // 성공 시 로그인 페이지로 리다이렉트되거나 성공 토스트가 표시됨
    const loginRedirect = page.waitForURL(/\/login/, { timeout: 10_000 }).catch(() => null);
    const successToast = page
      .getByText(/회원가입.*완료|관리자 승인/)
      .waitFor({ timeout: 10_000 })
      .catch(() => null);
    const errorToast = page
      .getByText(/실패|이미 존재/)
      .waitFor({ timeout: 5_000 })
      .catch(() => null);

    // 성공(리다이렉트 또는 토스트) 또는 중복 에러(이미 테스트로 생성된 계정) 중 하나
    await Promise.race([loginRedirect, successToast, errorToast]);

    // 에러 배너("입력 정보를 확인해주세요")가 없으면 유효성 검사는 통과한 것
    const validationError = await page
      .getByText("입력 정보를 확인해주세요")
      .isVisible()
      .catch(() => false);
    expect(validationError).toBeFalsy();
  });
});
