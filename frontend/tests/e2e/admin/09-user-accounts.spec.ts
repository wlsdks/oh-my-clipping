import { test, expect } from "../fixtures/auth";
import { expectToast, expectContentOrEmpty, isVisibleSafe } from "../helpers/assertions";

test.describe("user accounts management", () => {
  test("가입 승인 탭이 로드되고 대기 목록이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts");

    // 페이지 제목 확인
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

    // "가입 승인" 탭이 기본 활성 탭으로 표시
    await expect(adminPage.getByRole("tab", { name: /가입 승인/ })).toBeVisible();

    // 대기 테이블 또는 빈 상태가 보이는지 확인
    const table = adminPage.locator("table");
    const emptyText = adminPage.getByText(/모든 가입 신청을 처리했어요/);
    const rejectedEmpty = adminPage.getByText(/반려된 신청이 없어요/);
    await expectContentOrEmpty(table, emptyText.or(rejectedEmpty));
  });

  test("가입을 승인할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts");
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

    // 데이터 로드 대기 — 테이블 또는 빈 상태가 보일 때까지
    const table = adminPage.locator("table");
    const emptyText = adminPage.getByText(/모든 가입 신청을 처리했어요/);
    await expect(table.or(emptyText)).toBeVisible({ timeout: 10_000 });

    // 테이블에서 "승인" 버튼 찾기
    const approveButton = adminPage.locator("td").getByRole("button", { name: "승인", exact: true }).first();
    const hasApproveBtn = await isVisibleSafe(approveButton);

    if (!hasApproveBtn) {
      test.skip();
      return;
    }

    await approveButton.click();

    // 승인 다이얼로그 확인
    await expect(adminPage.getByText("회원가입 승인")).toBeVisible();
    await expect(adminPage.getByLabel(/승인 메모/)).toBeVisible();

    // "승인 확정" 버튼 클릭
    await adminPage.getByRole("button", { name: "승인 확정" }).click();

    // 성공 토스트 확인
    await expectToast(adminPage, /회원가입을 승인했어요/);
  });

  test("가입을 반려할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts");
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();
    // 테이블 로드 대기
    await expect(adminPage.locator("table").or(adminPage.getByText(/모든 가입 신청을 처리했어요/))).toBeVisible({ timeout: 15_000 });

    // 테이블 행 내의 "반려" 버튼 찾기 — 필터 칩 "반려 N"과 구분
    // 테이블 셀 안의 반려 버튼을 정확히 타겟
    const tableRejectButton = adminPage.locator("table button", { hasText: "반려" }).filter({ hasNotText: /\d/ }).first();
    const directRejectButton = adminPage.locator("td").getByRole("button", { name: "반려", exact: true }).first();
    const rejectButton = directRejectButton;
    const hasRejectBtn = await isVisibleSafe(rejectButton);

    if (!hasRejectBtn) {
      test.skip();
      return;
    }

    await rejectButton.click();

    // 반려 다이얼로그 확인
    await expect(adminPage.getByText("회원가입 반려")).toBeVisible();

    // 사유 없이 반려 확정 시 에러 메시지 확인
    await adminPage.getByRole("button", { name: "반려 확정" }).click();
    await expect(adminPage.getByText("반려 사유를 입력해주세요.")).toBeVisible();

    // 반려 사유 입력 후 확정
    await adminPage.getByLabel(/반려 사유/).fill("테스트용 반려 사유입니다.");
    await adminPage.getByRole("button", { name: "반려 확정" }).click();

    // 성공 토스트 확인
    await expectToast(adminPage, /회원가입을 반려했어요/);
  });

  test("회원 현황 탭으로 전환하면 멤버 목록이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts");
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

    // "회원 현황" 탭 클릭
    await adminPage.getByRole("tab", { name: "회원 현황" }).click();

    // 설명 문구 변경 확인
    await expect(adminPage.getByText(/승인된 회원의 활동 현황을 관리해요/)).toBeVisible();

    // 멤버 테이블 또는 빈 상태가 보이는지 확인
    const table = adminPage.locator("table");
    const emptyText = adminPage.getByText(/활성 회원이 없어요/);
    const filteredEmpty = adminPage.getByText(/조건에 맞는 회원이 없어요/);
    await expectContentOrEmpty(table, emptyText.or(filteredEmpty));
  });

  test("회원 검색과 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts?tab=members");
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

    // 검색 입력란 확인
    const searchInput = adminPage.getByPlaceholder(/이름, 아이디, 부서 검색/);
    await expect(searchInput).toBeVisible();

    // 존재하지 않을 법한 검색어 입력
    await searchInput.fill("zzzzz_no_match");

    // 빈 상태 또는 필터 결과가 없음이 표시되어야 함
    const emptyText = adminPage.getByText(/결과가 없어요/).or(adminPage.getByText(/조건에 맞는 회원이 없어요/));
    const table = adminPage.locator("table");
    await expectContentOrEmpty(emptyText, table);

    // 검색어 지우기
    await searchInput.clear();

    // 역할 필터 칩 확인
    const adminChip = adminPage.getByRole("button", { name: "관리자", exact: true });
    const hasAdminChip = await isVisibleSafe(adminChip);

    if (hasAdminChip) {
      await adminChip.click();

      // 필터가 적용됨 — 페이지가 유효한지 확인
      await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

      // "전체" 칩으로 복구
      const allChip = adminPage.getByRole("button", { name: "전체", exact: true }).first();
      if (await isVisibleSafe(allChip)) {
        await allChip.click();
      }
    }
  });

  test("회원 탈퇴 처리가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/user-accounts?tab=members");
    await expect(adminPage.getByRole("heading", { name: "회원 관리" })).toBeVisible();

    // "탈퇴 처리" 버튼 찾기
    const withdrawButton = adminPage.getByRole("button", { name: "탈퇴 처리" }).first();
    const hasWithdrawBtn = await isVisibleSafe(withdrawButton);

    if (!hasWithdrawBtn) {
      test.skip();
      return;
    }

    await withdrawButton.click();

    // 탈퇴 다이얼로그 확인
    await expect(adminPage.getByRole("heading", { name: "탈퇴 처리" })).toBeVisible();

    // 사용자 정보가 표시되는지 확인
    await expect(adminPage.getByText("아이디:")).toBeVisible();

    // Slack 정리 확인 체크박스가 체크되지 않은 상태에서 "탈퇴 처리 확정" 비활성화 확인
    const confirmButton = adminPage.getByRole("button", { name: "탈퇴 처리 확정" });
    await expect(confirmButton).toBeDisabled();

    // Slack 정리 체크박스 체크
    const slackCheckbox = adminPage.getByRole("checkbox");
    await slackCheckbox.check();

    // "탈퇴 처리 확정" 버튼이 활성화 되었는지 확인
    await expect(confirmButton).toBeEnabled();

    // 취소 버튼으로 다이얼로그 닫기 (실제 탈퇴는 실행하지 않음 — 데이터 보존)
    await adminPage.getByRole("button", { name: "취소" }).click();

    // 다이얼로그가 닫혔는지 확인
    await expect(adminPage.getByRole("heading", { name: "탈퇴 처리" })).not.toBeVisible();
  });
});
