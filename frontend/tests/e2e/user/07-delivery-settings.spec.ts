import { test, expect } from "../fixtures/user-auth";
import { isVisibleSafe } from "../helpers/assertions";

// TODO: Delivery settings were part of UserClippingPage (tabbed view) which is no longer routed.
// The delivery schedule is now set per-subscription via SubscriptionEditModal
// or via QuickSetupWizard. These tests need to be rewritten when the standalone
// delivery settings page is re-introduced.

test.describe("delivery settings", () => {
  test("구독 편집 모달에서 발송 프리셋이 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    // 활성 구독의 "변경" 버튼 찾기
    const changeBtn = userPage.getByRole("button", { name: "변경", exact: true }).first();
    const hasSubscription = await changeBtn.isVisible().catch(() => false);

    if (!hasSubscription) {
      test.skip(true, "활성 구독이 없어 테스트를 건너뜁니다");
      return;
    }

    // 편집 모달 열기
    await changeBtn.click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeVisible({ timeout: 5_000 });

    // 발송 요일 프리셋 확인
    await expect(userPage.getByRole("button", { name: "평일만" })).toBeVisible();
    await expect(userPage.getByRole("button", { name: "매일" })).toBeVisible();
    await expect(userPage.getByRole("button", { name: "직접 선택" })).toBeVisible();

    // 발송 시간 슬롯 확인 (8, 12, 18시)
    await expect(userPage.getByRole("button", { name: /오전 8시/ })).toBeVisible();

    // 취소로 닫기
    await userPage.getByRole("button", { name: "취소" }).click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeHidden();
  });

  test("구독 편집 모달에서 직접 선택 시 요일 버튼이 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    const changeBtn = userPage.getByRole("button", { name: "변경", exact: true }).first();
    const hasSubscription = await changeBtn.isVisible().catch(() => false);

    if (!hasSubscription) {
      test.skip(true, "활성 구독이 없어 테스트를 건너뜁니다");
      return;
    }

    await changeBtn.click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeVisible({ timeout: 5_000 });

    // "직접 선택" 클릭 시 요일 버튼들이 나타나는지 확인
    await userPage.getByRole("button", { name: "직접 선택" }).click();
    await expect(userPage.getByRole("button", { name: "월", exact: true })).toBeVisible();
    await expect(userPage.getByRole("button", { name: "일", exact: true })).toBeVisible();

    // 취소로 닫기
    await userPage.getByRole("button", { name: "취소" }).click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeHidden();
  });
});
