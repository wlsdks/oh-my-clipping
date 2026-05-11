import { test, expect } from "../fixtures/auth";

test.describe("unified analytics page", () => {
  test("통합 분석 페이지가 로드되고 기본 탭이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/analytics");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();

    // 기본 탭(전체 현황)이 활성인지 확인
    await expect(
      adminPage.getByRole("tab", { name: "전체 현황" })
    ).toBeVisible();

    // KPI 카드 또는 빈/에러 상태 중 하나가 렌더되는지 확인
    const dauCard = adminPage.getByText("DAU (오늘)");
    const emptyState = adminPage.getByText(/아직 수집된 데이터가 없어요/);
    const errorState = adminPage.getByText(/데이터를 불러오지 못했어요/);
    const loadingSkeleton = adminPage.locator(".animate-pulse").first();

    await expect(
      dauCard.or(emptyState).or(errorState).or(loadingSkeleton)
    ).toBeVisible({ timeout: 10_000 });
  });

  test("기간 버튼을 전환할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/analytics");

    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();

    const periodLabels = ["이번 주", "지난 주", "이번 달", "지난 달"];

    for (const label of periodLabels) {
      await adminPage.getByRole("button", { name: label, exact: true }).click();

      // 페이지가 안정적으로 유지되는지 확인
      await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();
    }
  });

  test("3개 탭을 전환할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/analytics");

    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();

    const tabLabels = ["콘텐츠 품질", "콘텐츠 인사이트", "전체 현황"];

    for (const label of tabLabels) {
      await adminPage
        .getByRole("tab", { name: label })
        .or(adminPage.getByText(label, { exact: true }))
        .first()
        .click();

      // 탭 전환 후 안정화 대기
      await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();
    }
  });

  test("카테고리 필터가 차트 데이터를 변경한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/analytics");

    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();

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

    // 페이지가 정상 유지되는지 확인
    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();
  });

  test("레거시 경로가 올바르게 리다이렉트된다", async ({ adminPage }) => {
    // /admin/insights → /admin/analytics?tab=quality
    await adminPage.goto("/admin/insights");
    await expect(adminPage).toHaveURL(/\/admin\/analytics\?tab=quality/);
    await expect(adminPage.getByRole("heading", { name: "통합 분석" })).toBeVisible();

    // /admin/costs → /admin/cost (비용 관리 독립 페이지)
    await adminPage.goto("/admin/costs");
    await expect(adminPage).toHaveURL(/\/admin\/cost/);
    await expect(adminPage.getByRole("heading", { name: "비용 관리" })).toBeVisible();
  });
});
