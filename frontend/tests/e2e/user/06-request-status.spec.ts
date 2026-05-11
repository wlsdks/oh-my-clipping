import { test, expect } from "../fixtures/user-auth";
import { isVisibleSafe } from "../helpers/assertions";

test.describe("request status", () => {
  test("전체 신청 내역이 상태 뱃지와 함께 로드된다", async ({ userPage }) => {
    await userPage.goto("/user/history");

    // "진행 상태" 페이지 헤딩 확인
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // 필터 칩이 로드되었는지 확인 (전체, 승인대기 등)
    await expect(userPage.getByText(/^전체/).first()).toBeVisible();

    // 신청 내역 카드가 있거나 빈 상태 메시지가 표시된다
    // UserStatusPage의 PENDING 상태 레이블은 "검토 대기" (HistoryTab과 동일)
    const hasCards = await isVisibleSafe(userPage.locator("[class*='rounded']").filter({ hasText: /검토 대기|사용 중|반려|철회됨/ }).first());
    const hasEmpty = await isVisibleSafe(userPage.getByText(/신청 내역이 없/));
    expect(hasCards || hasEmpty).toBeTruthy();

    // 카드가 있을 때 상태 뱃지 중 하나 이상이 보이는지 확인
    if (hasCards) {
      const statusBadges = userPage.getByText(/검토 대기|사용 중|반려|철회됨/);
      await expect(statusBadges.first()).toBeVisible();
    }
  });

  test("필터 칩이 동작한다 (전체/승인대기/완료)", async ({ userPage }) => {
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // 전체 필터 칩 확인
    const allChip = userPage.getByText(/^전체/).first();
    await expect(allChip).toBeVisible();

    // 승인대기 칩 클릭
    const pendingChip = userPage.getByText(/^승인대기/).first();
    await expect(pendingChip).toBeVisible();
    await pendingChip.click();

    // 필터 적용 후 대기 항목 또는 빈 상태가 표시된다
    // PENDING 상태 배지 레이블: "검토 대기" (UserStatusPage.statusBadge 기준)
    const hasPending = await isVisibleSafe(userPage.getByText("검토 대기").first());
    const hasEmptyPending = await isVisibleSafe(userPage.getByText(/해당하는 신청 내역이 없/));
    expect(hasPending || hasEmptyPending).toBeTruthy();

    // 완료 칩 클릭
    const completedChip = userPage.getByText(/^완료/).first();
    await completedChip.click();

    // 완료 필터 적용 후 승인 항목, 빈 상태, 또는 안내 문구가 표시된다
    const hasApproved = await isVisibleSafe(userPage.getByText("사용 중").first());
    const hasEmptyCompleted = await isVisibleSafe(userPage.getByText(/해당하는 신청 내역이 없/));
    const hasCompletedHint = await isVisibleSafe(userPage.getByText(/승인 완료된 구독은/));
    expect(hasApproved || hasEmptyCompleted || hasCompletedHint).toBeTruthy();

    // 전체 칩으로 복귀
    await allChip.click();
  });

  test("반려된 항목에서 상세 확인 시 반려 사유가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // 반려 칩 클릭
    const rejectedChip = userPage.getByText(/^반려/).first();
    await rejectedChip.click();

    // 반려된 항목이 있는지 확인
    const hasRejected = await isVisibleSafe(userPage.getByText("반려").nth(1)); // 첫 번째는 필터 칩
    if (!hasRejected) {
      test.skip(true, "반려된 신청 항목이 없어 테스트를 건너뜀");
      return;
    }

    // 반려 카드 클릭하여 상세 다이얼로그 열기
    const rejectedCard = userPage.locator("button").filter({ hasText: "반려" }).first();
    await rejectedCard.click();

    // 상세 다이얼로그에서 반려 사유 또는 삭제하기 버튼이 표시된다
    const hasRejectReason = await isVisibleSafe(userPage.getByText(/반려 사유|반려:/));
    const hasDeleteBtn = await isVisibleSafe(userPage.getByRole("button", { name: "삭제하기" }));
    expect(hasRejectReason || hasDeleteBtn).toBeTruthy();
  });

  test("반려/철회 항목을 삭제할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // 반려 또는 철회 항목 찾기
    const rejectedChip = userPage.getByText(/^반려/).first();
    await rejectedChip.click();

    let hasTarget = await isVisibleSafe(userPage.getByText("반려").nth(1));

    if (!hasTarget) {
      // 철회 탭 확인
      const withdrawnChip = userPage.getByText(/^철회/).first();
      await withdrawnChip.click();
      hasTarget = await isVisibleSafe(userPage.getByText("철회됨").first());
    }

    if (!hasTarget) {
      test.skip(true, "반려/철회 항목이 없어 삭제 테스트를 건너뜀");
      return;
    }

    // 삭제 대상 카드 클릭
    const targetCard = userPage.locator("button").filter({ hasText: /반려|철회됨/ }).first();
    await targetCard.click();

    // 삭제하기 버튼 확인
    const deleteBtn = userPage.getByRole("button", { name: "삭제하기" });
    const canDelete = await isVisibleSafe(deleteBtn);
    if (!canDelete) {
      test.skip(true, "삭제 버튼이 없어 테스트를 건너뜀");
      return;
    }

    // 다이얼로그 confirm을 자동 수락
    userPage.once("dialog", (dialog) => dialog.accept());
    await deleteBtn.click();

    // 삭제 성공 토스트 또는 항목 사라짐 확인
    const toastVisible = await isVisibleSafe(userPage.getByText(/삭제했어요/), 5_000);
    expect(toastVisible).toBeTruthy();
  });

  test("승인된 항목은 삭제할 수 없다", async ({ userPage }) => {
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // 완료 칩 클릭하여 승인 항목 보기
    const completedChip = userPage.getByText(/^완료/).first();
    await completedChip.click();

    const hasApproved = await isVisibleSafe(userPage.getByText("사용 중").first());
    if (!hasApproved) {
      test.skip(true, "승인된 항목이 없어 테스트를 건너뜀");
      return;
    }

    // 승인된 카드 클릭하여 상세 열기
    const approvedCard = userPage.locator("button").filter({ hasText: "사용 중" }).first();
    await approvedCard.click();

    // 삭제하기 버튼이 없어야 한다 (철회하기도 없어야 한다)
    const deleteBtn = userPage.getByRole("button", { name: "삭제하기" });
    const withdrawBtn = userPage.getByRole("button", { name: "철회하기" });
    await expect(deleteBtn).toHaveCount(0);
    await expect(withdrawBtn).toHaveCount(0);
  });
});
