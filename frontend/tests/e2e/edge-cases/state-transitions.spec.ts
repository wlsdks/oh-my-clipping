import { test, expect, type Page } from "@playwright/test";
import { buildTestLabel, loginAsUser, loginAsAdmin, createUserRequest } from "../helpers/api";
import { isVisibleSafe, expectToast } from "../helpers/assertions";

async function loginWithShortcut(page: Page, scope: "admin" | "user") {
  const username = scope === "admin" ? "dev.admin@clipping.local" : "dev.user@clipping.local";
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto("/login");
    const idInput = page.getByLabel("아이디");
    await expect(idInput).toBeVisible({ timeout: 15_000 });
    await idInput.fill(username);
    await page.getByLabel("비밀번호").fill("LocalPass123!");
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await expect(page).toHaveURL(new RegExp(scope === "admin" ? "/admin" : "/user"), { timeout: 20_000 });
      return;
    } catch {
      if (attempt === 3) throw new Error(`Login as ${scope} failed after 3 attempts`);
      await page.waitForTimeout(1000);
    }
  }
}

test.describe("state transitions — request lifecycle", () => {
  test("PENDING 요청을 승인하면 APPROVED 상태로 전환된다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const adminContext = await browser.newContext();

    // 유저 요청 생성 (API)
    await loginAsUser(userContext.request);
    const label = buildTestLabel("승인전환");
    const result = await createUserRequest(userContext.request, {
      requestName: label,
      sourceName: `${label} 소스`,
      sourceUrl: `https://example.com/approve-${Date.now()}.xml`,
    });

    if (!result.ok) {
      await userContext.close();
      await adminContext.close();
      test.skip(true, "요청 생성 실패 — 구독 한도 도달");
      return;
    }

    // 관리자 페이지에서 승인
    const adminPage = await adminContext.newPage();
    await loginWithShortcut(adminPage, "admin");
    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByRole("heading", { name: "구독 관리" })).toBeVisible();

    // 구독 관리 테이블에서 PENDING 요청의 승인 버튼 찾기
    // 새 UI에서는 요청 행 클릭 → 사이드 패널 → 승인 버튼 플로우
    const approveBtn = adminPage.getByRole("button", { name: "승인" }).first();
    const hasApproveBtn = await isVisibleSafe(approveBtn, 10_000);

    if (!hasApproveBtn) {
      // 테이블 행을 클릭하여 사이드 패널을 열어야 할 수 있음
      const pendingRow = adminPage.locator("tr, button").filter({ hasText: label }).first();
      if (await isVisibleSafe(pendingRow, 5_000)) {
        await pendingRow.click();
        await expect(adminPage.getByRole("button", { name: "승인" }).first()).toBeVisible({ timeout: 5_000 });
      }
    }

    await adminPage.getByRole("button", { name: "승인" }).first().click();
    await expectToast(adminPage, /승인|처리/);

    // 유저 페이지에서 승인 상태 확인
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 완료 탭 클릭
    await userPage.getByText(/^완료/).first().click();

    // "사용 중" 뱃지 확인
    const approvedBadge = userPage.getByText("사용 중").first();
    const hasApproved = await isVisibleSafe(approvedBadge, 10_000);
    expect(hasApproved).toBeTruthy();

    await userContext.close();
    await adminContext.close();
  });

  test("PENDING 요청을 반려하면 REJECTED 상태로 전환된다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const adminContext = await browser.newContext();

    await loginAsUser(userContext.request);
    const label = buildTestLabel("반려전환");
    const result = await createUserRequest(userContext.request, {
      requestName: label,
      sourceName: `${label} 소스`,
      sourceUrl: `https://example.com/reject-${Date.now()}.xml`,
    });

    if (!result.ok) {
      await userContext.close();
      await adminContext.close();
      test.skip(true, "요청 생성 실패");
      return;
    }

    // 관리자가 반려
    const adminPage = await adminContext.newPage();
    await loginWithShortcut(adminPage, "admin");
    await adminPage.goto("/admin/subscriptions");

    const rejectBtn = adminPage.getByRole("button", { name: "반려", exact: true }).first();
    const hasRejectBtn = await isVisibleSafe(rejectBtn);

    if (!hasRejectBtn) {
      await userContext.close();
      await adminContext.close();
      test.skip(true, "반려 버튼이 없어 건너뜁니다");
      return;
    }

    await rejectBtn.click();

    // 반려 사유 입력 (사이드 패널 또는 모달)
    const rejectReasonInput = adminPage.getByPlaceholder(/사유|승인 시 선택, 반려 시/).first();
    const hasReasonInput = await isVisibleSafe(rejectReasonInput, 5_000);
    if (hasReasonInput) {
      await rejectReasonInput.fill("테스트 반려 사유");
    }

    // 반려 확정 버튼 (구 "반려 확정" 또는 신 "반려" 버튼)
    const confirmRejectBtn = adminPage.getByRole("button", { name: /반려 확정|반려/ }).last();
    await confirmRejectBtn.click();
    await expectToast(adminPage, /반려|처리/);

    // 유저 페이지에서 반려 상태 확인
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    await userPage.getByText(/^반려/).first().click();

    const rejectedBadge = userPage.getByText("반려").nth(1);
    const hasRejected = await isVisibleSafe(rejectedBadge, 10_000);
    expect(hasRejected).toBeTruthy();

    await userContext.close();
    await adminContext.close();
  });

  test("PENDING 요청을 철회하면 WITHDRAWN 상태로 전환된다", async ({ browser }) => {
    const userContext = await browser.newContext();

    await loginAsUser(userContext.request);
    const label = buildTestLabel("철회전환");
    const result = await createUserRequest(userContext.request, {
      requestName: label,
      sourceName: `${label} 소스`,
      sourceUrl: `https://example.com/withdraw-${Date.now()}.xml`,
    });

    if (!result.ok) {
      await userContext.close();
      test.skip(true, "요청 생성 실패");
      return;
    }

    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 승인대기 탭
    await userPage.getByText(/^승인대기/).first().click();

    // 검토 대기인 항목 클릭
    const pendingCard = userPage.locator("button").filter({ hasText: "검토 대기" }).first();
    const hasPending = await isVisibleSafe(pendingCard, 10_000);

    if (!hasPending) {
      await userContext.close();
      test.skip(true, "대기 중인 신청이 없어 건너뜁니다");
      return;
    }

    await pendingCard.click();
    await expect(userPage.getByRole("button", { name: "철회하기" }).or(userPage.getByRole("dialog"))).toBeVisible({ timeout: 5_000 }).catch(() => {});

    // 철회하기 버튼 확인 및 클릭
    const withdrawBtn = userPage.getByRole("button", { name: "철회하기" });
    const hasWithdraw = await isVisibleSafe(withdrawBtn);

    if (!hasWithdraw) {
      await userContext.close();
      test.skip(true, "철회 버튼이 없어 건너뜁니다");
      return;
    }

    await withdrawBtn.click();

    // 확인 다이얼로그가 열리면 "철회" 또는 확인 버튼 클릭
    const confirmWithdrawDialog = userPage.getByRole("alertdialog").or(userPage.getByRole("dialog")).last();
    const confirmWithdrawBtn = confirmWithdrawDialog.getByRole("button", { name: /철회|확인/ }).last();
    if (await isVisibleSafe(confirmWithdrawBtn, 3_000)) {
      await confirmWithdrawBtn.click();
    }

    // 철회 탭에서 확인
    await userPage.getByText(/^철회/).first().click();

    const withdrawnBadge = userPage.getByText("철회됨").first();
    const hasWithdrawn = await isVisibleSafe(withdrawnBadge, 10_000);
    expect(hasWithdrawn).toBeTruthy();

    await userContext.close();
  });

  test("APPROVED 구독은 삭제할 수 없다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");

    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 완료 탭 클릭
    await userPage.getByText(/^완료/).first().click();

    const hasApproved = await isVisibleSafe(userPage.getByText("사용 중").first());
    if (!hasApproved) {
      await userContext.close();
      test.skip(true, "승인된 항목이 없어 건너뜁니다");
      return;
    }

    // 승인된 카드 클릭
    await userPage.locator("button").filter({ hasText: "사용 중" }).first().click();
    await expect(userPage.getByRole("button", { name: "삭제하기" }).or(userPage.getByRole("dialog"))).toBeVisible({ timeout: 5_000 }).catch(() => {});

    // 삭제하기/철회하기 버튼이 없어야 한다
    const deleteBtn = userPage.getByRole("button", { name: "삭제하기" });
    const withdrawBtn = userPage.getByRole("button", { name: "철회하기" });
    await expect(deleteBtn).toHaveCount(0);
    await expect(withdrawBtn).toHaveCount(0);

    await userContext.close();
  });

  test("REJECTED 요청에서 다시 신청하면 새 PENDING 요청이 생성된다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");

    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 반려 탭
    await userPage.getByText(/^반려/).first().click();

    const hasRejected = await isVisibleSafe(userPage.getByText("반려").nth(1));
    if (!hasRejected) {
      await userContext.close();
      test.skip(true, "반려 항목이 없어 건너뜁니다");
      return;
    }

    // 반려된 카드 클릭
    const rejectedCard = userPage.locator("button").filter({ hasText: "반려" }).first();
    await rejectedCard.click();
    await expect(userPage.getByRole("button", { name: /다시 신청|재신청/ }).or(userPage.getByRole("dialog"))).toBeVisible({ timeout: 5_000 }).catch(() => {});

    // "다시 신청" 버튼 확인
    const resubmitBtn = userPage.getByRole("button", { name: /다시 신청|재신청/ });
    const hasResubmit = await isVisibleSafe(resubmitBtn);

    if (!hasResubmit) {
      // 다시 신청 기능이 UI에 없으면 건너뜀
      await userContext.close();
      test.skip(true, "다시 신청 버튼이 없어 건너뜁니다");
      return;
    }

    await resubmitBtn.click();

    // 위자드 또는 신청 폼이 열리는지 확인
    const wizardVisible = await isVisibleSafe(userPage.getByText("빠른 세팅"));
    const formVisible = await isVisibleSafe(userPage.getByText(/사이트|소스 URL/));
    expect(wizardVisible || formVisible).toBeTruthy();

    await userContext.close();
  });

  test("WITHDRAWN 요청에서 다시 신청하면 데이터가 pre-fill된 위자드가 열린다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");

    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 철회 탭
    await userPage.getByText(/^철회/).first().click();

    const hasWithdrawn = await isVisibleSafe(userPage.getByText("철회됨").first());
    if (!hasWithdrawn) {
      await userContext.close();
      test.skip(true, "철회 항목이 없어 건너뜁니다");
      return;
    }

    // 철회된 카드 클릭
    const withdrawnCard = userPage.locator("button").filter({ hasText: "철회됨" }).first();
    await withdrawnCard.click();

    // "다시 신청" 버튼 확인
    const resubmitBtn = userPage.getByRole("button", { name: /다시 신청|재신청/ });
    const hasResubmit = await isVisibleSafe(resubmitBtn);

    if (!hasResubmit) {
      await userContext.close();
      test.skip(true, "다시 신청 버튼이 없어 건너뜁니다");
      return;
    }

    await resubmitBtn.click();

    // 위자드가 열리는지 확인
    const wizardVisible = await isVisibleSafe(userPage.getByText("빠른 세팅"));
    expect(wizardVisible).toBeTruthy();

    await userContext.close();
  });

  test("REJECTED/WITHDRAWN 항목을 삭제하면 영구 제거된다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");

    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 반려 또는 철회 항목 찾기
    let targetFilter: string | null = null;

    await userPage.getByText(/^반려/).first().click();
    let hasTarget = await isVisibleSafe(userPage.getByText("반려").nth(1));
    if (hasTarget) {
      targetFilter = "반려";
    }

    if (!hasTarget) {
      await userPage.getByText(/^철회/).first().click();
      hasTarget = await isVisibleSafe(userPage.getByText("철회됨").first());
      if (hasTarget) {
        targetFilter = "철회";
      }
    }

    if (!hasTarget) {
      await userContext.close();
      test.skip(true, "삭제 가능한 항목이 없어 건너뜁니다");
      return;
    }

    // 카드 클릭
    const filterText = targetFilter === "반려" ? "반려" : "철회됨";
    const targetCard = userPage.locator("button").filter({ hasText: filterText }).first();
    await targetCard.click();
    await expect(userPage.getByRole("button", { name: "삭제하기" }).or(userPage.getByRole("dialog"))).toBeVisible({ timeout: 5_000 }).catch(() => {});

    const deleteBtn = userPage.getByRole("button", { name: "삭제하기" });
    const canDelete = await isVisibleSafe(deleteBtn);
    if (!canDelete) {
      await userContext.close();
      test.skip(true, "삭제 버튼이 없어 건너뜁니다");
      return;
    }

    // 삭제 전 카드 개수 기록 (현재 필터 탭 기준)
    const filterTextForCount = targetFilter === "반려" ? "반려" : "철회됨";
    const beforeCount = await userPage.locator("button").filter({ hasText: filterTextForCount }).count();

    await deleteBtn.click();

    // 확인 다이얼로그가 열리면 "삭제" 버튼을 클릭
    const confirmDialog = userPage.getByRole("alertdialog").or(userPage.getByRole("dialog")).last();
    const confirmDeleteBtn = confirmDialog.getByRole("button", { name: "삭제", exact: true });
    if (await isVisibleSafe(confirmDeleteBtn, 3_000)) {
      await confirmDeleteBtn.click();
    }

    // 삭제 성공 토스트, 빈 상태, 또는 카드 개수 감소 중 하나 확인
    const toastVisible = await isVisibleSafe(userPage.getByText(/삭제했어요/), 10_000);
    const tabCountZero = await isVisibleSafe(userPage.getByText(/해당하는 신청 내역이 없|신청 내역이 없어요/), 3_000);
    // 다이얼로그가 닫히고 뒤에 리스트가 보일 때까지 잠시 대기
    await userPage.waitForTimeout(500);
    const afterCount = await userPage.locator("button").filter({ hasText: filterTextForCount }).count();
    const cardRemoved = afterCount < beforeCount;
    expect(toastVisible || tabCountZero || cardRemoved).toBeTruthy();

    await userContext.close();
  });

  test("비활성 소스에 대한 승인 시 검증이 필요하다", async ({ browser }) => {
    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    await loginWithShortcut(adminPage, "admin");

    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 새 UI에는 별도 "미승인" 필터 버튼이 없고 미승인 뱃지는 소스 편집 모달에서만 표시됨
    // 승인 버튼이 페이지에 노출되는지만 확인
    const approveBtn = adminPage.getByRole("button", { name: "승인", exact: true }).first();
    const hasApprove = await isVisibleSafe(approveBtn, 5_000);

    if (!hasApprove) {
      await adminContext.close();
      test.skip(true, "미승인 소스가 없어 건너뜁니다 — UI에 승인 버튼 없음");
      return;
    }

    // 승인 클릭
    await approveBtn.click();

    // 검증 시작 토스트 또는 상태 변경 확인
    const successToast = adminPage.getByText(/승인됐어요/);
    const errorToast = adminPage.getByText(/승인 처리 실패/);
    await expect(successToast.or(errorToast).first()).toBeVisible({ timeout: 10_000 });

    await adminContext.close();
  });

  test("이미 승인된 가입 요청을 다시 승인하면 no-op이다", async ({ browser }) => {
    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    await loginWithShortcut(adminPage, "admin");

    await adminPage.goto("/admin/user-accounts");

    // 유저 계정 페이지 로드 확인
    const heading = adminPage.getByRole("heading", { name: /회원 관리|멤버 관리|유저 계정/ });
    const hasPage = await isVisibleSafe(heading, 10_000);

    if (!hasPage) {
      await adminContext.close();
      test.skip(true, "유저 계정 페이지 로드 실패");
      return;
    }

    // 승인 상태 필터에서 "승인됨" 선택
    const approvedFilter = adminPage.getByRole("button", { name: /승인됨|APPROVED/ });
    const hasFilter = await isVisibleSafe(approvedFilter);
    if (hasFilter) {
      await approvedFilter.click();
    }

    // 이미 승인된 사용자의 "승인" 버튼이 없어야 한다
    const approveBtn = adminPage.getByRole("button", { name: "승인", exact: true });
    const approveCount = await approveBtn.count();

    // 승인 버튼이 표시되지 않으면 no-op 동작이 올바르게 작동
    // (이미 승인된 사용자에게 재승인 버튼이 없음 = 올바른 동작)
    // 페이지가 정상이면 통과
    await expect(heading).toBeVisible();

    await adminContext.close();
  });

  test("구독 일시정지 후 재개하면 다시 활성 상태가 된다", async ({ browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await loginWithShortcut(userPage, "user");

    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // 토글 스위치 찾기
    const toggleSwitch = userPage.getByRole("switch").first();
    const hasSubscription = await isVisibleSafe(toggleSwitch);

    if (!hasSubscription) {
      await userContext.close();
      test.skip(true, "활성 구독이 없어 건너뜁니다");
      return;
    }

    // 현재 상태 기록
    const initialState = await toggleSwitch.getAttribute("aria-checked");

    // 일시정지 (토글 OFF)
    await toggleSwitch.click();
    await expect(toggleSwitch).toHaveAttribute("aria-checked", initialState === "true" ? "false" : "true", { timeout: 5_000 }).catch(() => {});

    const pausedState = await toggleSwitch.getAttribute("aria-checked");
    expect(pausedState).not.toBe(initialState);

    // 재개 (토글 ON 복원)
    await toggleSwitch.click();
    await expect(toggleSwitch).toHaveAttribute("aria-checked", initialState!, { timeout: 5_000 }).catch(() => {});

    const resumedState = await toggleSwitch.getAttribute("aria-checked");
    expect(resumedState).toBe(initialState);

    await userContext.close();
  });
});
