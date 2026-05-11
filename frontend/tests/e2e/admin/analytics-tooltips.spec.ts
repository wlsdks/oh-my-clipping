import { test, expect } from "../fixtures/auth";

test.describe("analytics tooltip smoke", () => {
  test("Overview 탭 DAU InfoTooltip이 hover 시 내용을 표시한다", async ({
    adminPage,
  }) => {
    await adminPage.goto("/admin/analytics");

    // 전체 현황 탭이 보일 때까지 대기
    await expect(
      adminPage.getByRole("tab", { name: "전체 현황" })
    ).toBeVisible({ timeout: 15_000 });

    // DAU 카드 또는 빈/에러 상태가 렌더되길 기다림
    const dauCard = adminPage.getByText("DAU (오늘)");
    const emptyState = adminPage.getByText(/아직 수집된 데이터가 없어요/);
    const errorState = adminPage.getByText(/데이터를 불러오지 못했어요/);

    await expect(dauCard.or(emptyState).or(errorState)).toBeVisible({
      timeout: 12_000,
    });

    // DAU 카드가 없으면 (빈/에러 상태) 툴팁 검증 스킵
    const dauVisible = await dauCard.isVisible();
    if (!dauVisible) {
      test.skip();
      return;
    }

    // DAU 카드 인근의 InfoTooltip 트리거 버튼 hover
    const dauSection = adminPage.getByText("DAU (오늘)").locator("..");
    const tooltipTrigger = dauSection.getByRole("button", {
      name: "DAU (오늘) 설명",
    });

    await tooltipTrigger.hover();

    // 툴팁 내용 확인
    await expect(
      adminPage.getByText(/유저 사이트에서/)
    ).toBeVisible({ timeout: 3_000 });
  });
});
