import { test, expect } from "../fixtures/user-auth";

test.describe("category browse page", () => {
  test("카테고리 목록이 로드되고 검색이 동작한다", async ({ userPage }) => {
    await userPage.goto("/user/browse");

    // 페이지 제목 확인
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    // 검색 입력 필드 확인
    const searchInput = userPage.getByPlaceholder(/주제 검색|초성 검색/);
    await expect(searchInput).toBeVisible();

    // 카테고리 카드 또는 빈 상태 확인
    const categoryCard = userPage.getByText(/명$/).first();
    const emptyState = userPage.getByText(/등록된 주제가 없/);
    await expect(categoryCard.or(emptyState)).toBeVisible({ timeout: 10_000 });

    // 카테고리가 있으면 검색 테스트
    const hasCards = await categoryCard.isVisible().catch(() => false);
    if (hasCards) {
      // 존재하지 않는 키워드로 검색
      await searchInput.fill("zzzznonexistent999");
      await userPage.waitForTimeout(300); // debounce wait

      // 검색 결과 없음 표시 확인
      await expect(userPage.getByText(/검색 결과가 없/)).toBeVisible();

      // 검색 초기화
      await searchInput.clear();
      await userPage.waitForTimeout(300); // debounce wait

      // 카드가 다시 보이는지 확인
      await expect(userPage.getByText(/명$/).first()).toBeVisible();
    }
  });

  test("구독 버튼 클릭 시 Slack ID 미설정이면 연결 모달이 뜬다", async ({ userPage }) => {
    await userPage.goto("/user/browse");
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    const subscribeBtn = userPage.getByRole("button", { name: "DM으로 받기" }).first();
    const hasSubscribable = await subscribeBtn.isVisible().catch(() => false);

    if (!hasSubscribable) {
      test.skip(true, "구독 가능한 카테고리가 없어 테스트를 건너뜁니다");
      return;
    }

    await subscribeBtn.click();

    // Slack 연결 모달이 뜨면: 멤버 ID 입력 가이드 확인 후 취소
    const slackModal = userPage.getByText("Slack DM을 받으려면 멤버 ID가 필요해요");
    const isModalVisible = await slackModal.isVisible().catch(() => false);

    if (isModalVisible) {
      await expect(userPage.getByPlaceholder("U01AB2CD3EF")).toBeVisible();
      await expect(userPage.getByText(/멤버 ID 찾는 법/)).toBeVisible();
      await userPage.getByRole("button", { name: "취소" }).click();
      await expect(slackModal).not.toBeVisible();
    } else {
      // 이미 Slack ID 설정됨 — 바로 구독 성공
      const successToast = userPage.getByText(/DM 구독.*설정/);
      const subscribedBadge = userPage.getByText("DM 수신 중").first();
      await expect(successToast.or(subscribedBadge)).toBeVisible({ timeout: 10_000 });
    }
  });

  test("이미 구독 중인 카테고리는 구독 상태가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/browse");
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    // "DM 수신 중" 뱃지가 있는 카테고리 찾기
    const subscribedBadge = userPage.getByText("DM 수신 중").first();
    const hasSubscribed = await subscribedBadge.isVisible().catch(() => false);

    if (!hasSubscribed) {
      test.skip(true, "구독 중인 카테고리가 없어 테스트를 건너뜁니다");
      return;
    }

    // "DM 수신 중" 뱃지가 표시되어야 함
    await expect(subscribedBadge).toBeVisible();

    // 구독 중인 카테고리에는 "DM으로 받기" 버튼이 없고 "DM 수신 중" 뱃지가 있어야 함
    // (카드 컴포넌트에서 isSubscribed일 때 버튼 대신 뱃지 표시)
    const subscribedCard = subscribedBadge.locator("xpath=ancestor::div[contains(@class,'rounded-xl')]");
    const dmButton = subscribedCard.getByRole("button", { name: "DM으로 받기" });
    await expect(dmButton).toHaveCount(0);
  });

  test("카테고리가 없을 때 안내 메시지가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/browse");
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    // 카테고리가 있는지 확인
    const categoryCard = userPage.getByText(/명$/).first();
    const hasCards = await categoryCard.isVisible().catch(() => false);

    if (hasCards) {
      // 카테고리가 있으면 이용 가이드가 보이는지 확인
      await expect(userPage.getByText("이렇게 이용하세요")).toBeVisible();
      await expect(userPage.getByText(/DM으로 받기/).first()).toBeVisible();
    } else {
      // 빈 상태 안내 메시지 확인
      await expect(userPage.getByText(/등록된 주제가 없/)).toBeVisible();
      await expect(userPage.getByText(/관리자에게 문의|주제를 만들어/)).toBeVisible();
    }
  });
});
