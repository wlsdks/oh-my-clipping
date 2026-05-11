import { test, expect } from "../fixtures/user-auth";

test.describe("manage subscriptions page", () => {
  test("활성 구독 목록이 로드된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");

    // 페이지 제목 확인
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // "구독 가능한 주제" 버튼 또는 링크 확인
    const browseBtnOrLink = userPage.getByRole("button", { name: "구독 가능한 주제" })
      .or(userPage.getByRole("link", { name: "구독 가능한 주제" }));
    await expect(browseBtnOrLink.first()).toBeVisible();

    // "새 주제" 또는 "구독 한도 도달" 버튼이 보여야 함
    const newTopicBtn = userPage.getByRole("button", { name: /새 주제|구독 한도/ });
    await expect(newTopicBtn).toBeVisible();

    // 구독 카드("변경" 버튼 포함)가 있거나 빈 상태
    const changeBtn = userPage.getByRole("button", { name: "변경", exact: true }).first();
    const emptyState = userPage.getByText(/활성화된 구독이 없/);
    await expect(changeBtn.or(emptyState)).toBeVisible({ timeout: 10_000 });
  });

  test("구독 편집 모달에서 키워드를 변경할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

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

    // 제외 키워드 입력 필드 확인
    const keywordInput = userPage.getByPlaceholder(/광고.*후원|Enter로 추가/);
    await expect(keywordInput).toBeVisible();

    // 키워드 추가
    await keywordInput.fill("테스트키워드");
    await userPage.getByRole("button", { name: "추가" }).click();

    // 키워드 칩이 추가되었는지 확인
    await expect(userPage.getByText("테스트키워드")).toBeVisible();

    // 키워드 삭제 (제거 버튼 클릭) — "테스트키워드 제거" aria-label
    await userPage.getByRole("button", { name: "테스트키워드 제거" }).click();

    // 키워드가 제거되었는지 확인
    await expect(userPage.getByText("테스트키워드")).toBeHidden();

    // 취소로 닫기
    await userPage.getByRole("button", { name: "취소" }).click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeHidden();
  });

  test("구독 편집에서 발송 스케줄을 변경할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

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

    // "매일" 프리셋 클릭
    await userPage.getByRole("button", { name: "매일" }).click();

    // 발송 시간 슬롯 확인 (8, 12, 18시)
    await expect(userPage.getByRole("button", { name: /오전 8시/ })).toBeVisible();

    // 다른 시간 선택
    await userPage.getByRole("button", { name: /오후 12시/ }).click();

    // "직접 선택" 클릭 시 요일 버튼들이 나타나는지 확인
    await userPage.getByRole("button", { name: "직접 선택" }).click();
    await expect(userPage.getByRole("button", { name: "월", exact: true })).toBeVisible();
    await expect(userPage.getByRole("button", { name: "일", exact: true })).toBeVisible();

    // 취소로 닫기 (변경 사항 버리기)
    await userPage.getByRole("button", { name: "취소" }).click();
    await expect(userPage.getByRole("heading", { name: "구독 설정 변경" })).toBeHidden();
  });

  test("구독을 일시정지할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // 활성 구독의 토글 스위치 찾기
    const toggleSwitch = userPage.getByRole("switch").first();
    const hasSubscription = await toggleSwitch.isVisible().catch(() => false);

    if (!hasSubscription) {
      test.skip(true, "활성 구독이 없어 테스트를 건너뜁니다");
      return;
    }

    // 현재 토글 상태 확인
    const isActive = await toggleSwitch.getAttribute("aria-checked");

    // 토글 클릭
    await toggleSwitch.click();
    await expect(toggleSwitch).toHaveAttribute("aria-checked", isActive === "true" ? "false" : "true", { timeout: 5_000 }).catch(() => {});

    // 상태가 변경되었는지 확인
    const newState = await toggleSwitch.getAttribute("aria-checked");
    expect(newState).not.toBe(isActive);

    // 다시 원래 상태로 복원
    await toggleSwitch.click();
    await expect(toggleSwitch).toHaveAttribute("aria-checked", isActive!, { timeout: 5_000 }).catch(() => {});

    const restoredState = await toggleSwitch.getAttribute("aria-checked");
    expect(restoredState).toBe(isActive);
  });

  test("진행 상태 페이지에서 대기 중 신청을 확인하고 철회할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/history");

    // 진행 상태 페이지 확인
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 필터 칩 확인
    await expect(userPage.getByText(/^전체/).first()).toBeVisible();
    await expect(userPage.getByText(/^승인대기/).first()).toBeVisible();

    // 승인대기 필터 클릭
    await userPage.getByText(/^승인대기/).first().click();

    // 대기 중인 항목 찾기
    const pendingItem = userPage.getByText("검토 대기").first();
    const emptyState = userPage.getByText(/신청 내역이 없|해당하는 신청 내역이 없/);
    const hasPending = await pendingItem.isVisible().catch(() => false);

    if (!hasPending) {
      // 대기 중인 항목이 없으면 빈 상태 확인 후 건너뜀
      await expect(emptyState).toBeVisible();
      test.skip(true, "대기 중인 신청이 없어 철회 테스트를 건너뜁니다");
      return;
    }

    // 대기 중인 항목 클릭하여 상세 다이얼로그 열기
    await userPage.locator("button").filter({ hasText: "검토 대기" }).first().click();

    // 상세 다이얼로그에서 "철회하기" 버튼 확인
    const withdrawBtn = userPage.getByRole("button", { name: "철회하기" });
    await expect(withdrawBtn).toBeVisible({ timeout: 5_000 });
  });

  test("구독이 없을 때 안내 메시지가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // 구독이 있는지 확인
    const changeBtn = userPage.getByRole("button", { name: "변경", exact: true }).first();
    const hasSubscription = await changeBtn.isVisible().catch(() => false);

    if (hasSubscription) {
      // 구독이 있으면 "구독 가능한 주제" CTA 버튼이 있는지 확인
      await expect(userPage.getByRole("button", { name: "구독 가능한 주제" })).toBeVisible();
      // 구독 카운터가 표시되는지 확인
      await expect(userPage.getByText(/\/5개 구독/)).toBeVisible();
    } else {
      // 빈 상태 메시지 확인
      await expect(userPage.getByText(/활성화된 구독이 없/)).toBeVisible();
      // CTA 버튼 확인
      const ctaBtn = userPage.getByRole("button", { name: "시작하기" });
      const browseBtn = userPage.getByRole("button", { name: "구독 가능한 주제" });
      await expect(ctaBtn.or(browseBtn)).toBeVisible();
    }
  });
});
