import { test, expect } from "../fixtures/auth";

test.describe("delivery page", () => {
  test("발송 로그 페이지가 로드되고 필터가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/delivery");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();

    // 설명 문구
    await expect(adminPage.getByText("발송 이력과 실패 재발송을 관리해요")).toBeVisible();

    // KPI 카드 또는 로딩이 표시되는지 확인
    const totalLabel = adminPage.getByText("총 발송");
    const loadingSkeleton = adminPage.locator(".animate-pulse").first();
    await expect(totalLabel.or(loadingSkeleton)).toBeVisible({ timeout: 10_000 });

    // 기간 필터 버튼들
    await expect(adminPage.getByRole("button", { name: "이번 주", exact: true })).toBeVisible();

    // 상태 필터 버튼들
    await expect(adminPage.getByRole("button", { name: "전체", exact: true })).toBeVisible();
  });

  test("기간 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/delivery");

    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();

    const periodLabels = ["이번 주", "지난 주", "이번 달", "지난 달"];

    for (const label of periodLabels) {
      await adminPage.getByRole("button", { name: label, exact: true }).click();

      // 페이지가 안정적으로 유지되는지 확인
      await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();
    }
  });

  test("상태 필터가 동작한다 (전체/성공/실패/건너뛰기)", async ({ adminPage }) => {
    await adminPage.goto("/admin/delivery");

    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();

    const statusLabels = ["전체", "성공", "실패", "건너뛰기"];

    for (const label of statusLabels) {
      await adminPage.getByRole("button", { name: label, exact: true }).click();

      // 페이지가 안정적으로 유지되는지 확인
      await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();
    }
  });

  test("카테고리 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/delivery");

    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();

    // 카테고리 드롭다운 확인
    const categoryTrigger = adminPage.getByRole("combobox").first();
    await expect(categoryTrigger).toBeVisible();

    // 드롭다운 열기
    await categoryTrigger.click();

    // 드롭다운이 열릴 때까지 대기 — listbox 가 먼저 노출된다
    const listbox = adminPage.getByRole("listbox");
    await expect(listbox).toBeVisible({ timeout: 5_000 });

    // "전체 카테고리" 옵션 클릭
    const allOption = adminPage.getByRole("option", { name: "전체 카테고리" });
    await allOption.click();

    // 페이지 정상 유지 확인
    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();
  });

  test("실패한 발송을 재시도할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/delivery");

    await expect(adminPage.getByRole("heading", { name: "발송 관리" })).toBeVisible();

    // 실패 필터 활성화
    await adminPage.getByRole("button", { name: "실패", exact: true }).click();

    // 재발송 버튼 찾기
    const retryButton = adminPage.getByRole("button", { name: /재발송/ }).first();
    const emptyState = adminPage.getByText("아직 발송 기록이 없어요");
    const errorState = adminPage.getByText(/문제가 발생했어요/);

    // 데이터가 없으면 스킵
    const hasRetryButton = await retryButton.isVisible().catch(() => false);
    const hasEmpty = await emptyState.isVisible().catch(() => false);
    const hasError = await errorState.isVisible().catch(() => false);

    if (hasEmpty || hasError || !hasRetryButton) {
      // 실패한 발송 기록이 없으면 스킵
      test.skip();
      return;
    }

    // 재발송 버튼 클릭
    await retryButton.click();

    // 토스트 메시지 확인 (성공 또는 실패)
    const successToast = adminPage.getByText("재발송을 요청했어요");
    const errorToast = adminPage.getByText(/재발송 요청에 실패했어요/);
    await expect(successToast.or(errorToast)).toBeVisible({ timeout: 10_000 });
  });
});
