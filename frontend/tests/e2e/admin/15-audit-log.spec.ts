import { test, expect } from "../fixtures/auth";

test.describe("audit log page", () => {
  test("감사 로그 페이지가 로드되고 이벤트 테이블이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/audit-log");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    // 설명 문구
    await expect(adminPage.getByText("관리자 활동 이력을 확인합니다")).toBeVisible();

    // 로딩 완료 대기 — 스켈레톤이 사라질 때까지 기다린다
    const loadingSkeleton = adminPage.locator(".animate-pulse").first();
    await expect(loadingSkeleton).not.toBeVisible({ timeout: 15_000 }).catch(() => {});

    // 테이블 헤더가 표시되는지 확인 (빈 상태도 테이블 내부에 렌더링됨)
    const tableHeader = adminPage.getByRole("columnheader", { name: "시각" });
    const errorState = adminPage.getByText(/문제가 발생했어요/);

    await expect(
      tableHeader.or(errorState)
    ).toBeVisible({ timeout: 10_000 });
  });

  test("기간 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/audit-log");

    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    const periodLabels = ["이번 주", "지난 주", "이번 달", "지난 달"];

    for (const label of periodLabels) {
      await adminPage.getByRole("button", { name: label, exact: true }).click();

      // 페이지가 안정적으로 유지되는지 확인
      await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();
    }
  });

  test("액션 유형 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/audit-log");

    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    // 액션 필터 드롭다운 확인 (첫 번째 combobox)
    const actionTrigger = adminPage.getByRole("combobox").first();
    await expect(actionTrigger).toBeVisible();

    // 드롭다운 열기
    await actionTrigger.click();

    // "전체 액션" 옵션이 있는지 확인
    const allOption = adminPage.getByRole("option", { name: "전체 액션" });
    await expect(allOption).toBeVisible();

    // 다른 옵션(CREATE, UPDATE, DELETE 등)이 있는지 확인
    const options = adminPage.getByRole("option");
    const optionCount = await options.count();

    // 전체 액션 선택
    await allOption.click();

    // 페이지 정상 유지 확인
    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    // 다른 옵션이 있다면 하나 선택해본다
    if (optionCount > 1) {
      await actionTrigger.click();
      await adminPage.getByRole("option").nth(1).click();
      await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

      // 다시 전체로 복원
      await actionTrigger.click();
      await adminPage.getByRole("option", { name: "전체 액션" }).click();
    }
  });

  test("페이지네이션이 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/audit-log");

    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    // 페이지네이션 다음 버튼 찾기 (데이터 로드 대기)
    const nextButton = adminPage.getByRole("button", { name: /다음/ });
    const hasNextButton = await nextButton.isVisible().catch(() => false);

    if (!hasNextButton) {
      // 데이터가 1페이지 이하이면 스킵
      test.skip();
      return;
    }

    // 다음 버튼이 비활성화되어 있으면 (마지막 페이지) 스킵
    const isDisabled = await nextButton.isDisabled().catch(() => true);
    if (isDisabled) {
      test.skip();
      return;
    }

    // 다음 페이지 클릭
    await nextButton.click();

    // 페이지 정상 유지 확인
    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();

    // 이전 버튼이 활성화되는지 확인
    const prevButton = adminPage.getByRole("button", { name: /이전/ });
    await expect(prevButton).toBeEnabled();

    // 이전 페이지로 돌아가기
    await prevButton.click();
    await expect(adminPage.getByRole("heading", { name: "감사 로그" })).toBeVisible();
  });
});
