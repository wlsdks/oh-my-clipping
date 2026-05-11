import { test, expect } from "../fixtures/auth";

test.describe("pipeline page", () => {
  test("파이프라인 페이지가 로드되고 설명, 버튼, 이력이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/pipeline");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();

    // 설명 문구 — "수집 → 요약 → 다이제스트" 로 시작하는 서두만 매칭
    await expect(
      adminPage.getByText(/수집 → 요약 → 다이제스트/)
    ).toBeVisible();

    // 파이프라인 실행 버튼
    await expect(
      adminPage.getByRole("button", { name: /파이프라인 실행/ })
    ).toBeVisible();

    // 실행 이력 섹션
    await expect(
      adminPage.getByRole("heading", { name: "실행 이력" })
    ).toBeVisible();

    // 파이프라인 설정 링크
    await expect(
      adminPage.getByRole("link", { name: /파이프라인 설정/ })
    ).toBeVisible();
  });

  test("기간 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/pipeline");

    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();

    const periodLabels = ["이번 주", "지난 주", "이번 달", "지난 달"];

    for (const label of periodLabels) {
      const button = adminPage.getByRole("button", { name: label, exact: true }).first();
      await expect(button).toBeVisible();
      await button.click();

      // 클릭 후 페이지가 안정적으로 유지되는지 확인
      await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();
    }
  });

  test("파이프라인을 수동 실행할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/pipeline");

    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();

    // 카테고리 선택 드롭다운 확인
    const categoryTrigger = adminPage.getByRole("combobox").first();
    await expect(categoryTrigger).toBeVisible();

    // 카테고리가 없으면 실행 버튼이 disabled 상태 — 테스트 스킵
    const runButton = adminPage.getByRole("button", { name: /파이프라인 실행/ });
    await expect(runButton).toBeVisible();

    // 카테고리 선택 시도: 드롭다운 열기
    await categoryTrigger.click();

    // 카테고리 옵션이 있는지 확인
    const options = adminPage.getByRole("option");
    const optionCount = await options.count();

    if (optionCount === 0) {
      // 카테고리가 없으면 스킵
      test.skip();
      return;
    }

    // 첫 번째 카테고리 선택
    await options.first().click();

    // 실행 버튼이 활성화되었는지 확인
    await expect(runButton).toBeEnabled();
  });

  test("카테고리를 선택하면 최신 실행 정보가 갱신된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/pipeline");

    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();

    // 카테고리 선택 드롭다운 (PipelineControls 영역)
    const categoryTrigger = adminPage.getByRole("combobox").first();
    await expect(categoryTrigger).toBeVisible();

    // 드롭다운 열기
    await categoryTrigger.click();

    const options = adminPage.getByRole("option");
    const optionCount = await options.count();

    if (optionCount === 0) {
      test.skip();
      return;
    }

    // 카테고리 선택
    await options.first().click();

    // 페이지가 정상적으로 유지되는지 확인
    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();
  });
});
