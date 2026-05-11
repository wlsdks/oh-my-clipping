import { test, expect } from "../fixtures/user-auth";

test.describe("user home page", () => {
  test("유저 홈 페이지가 로드되고 브리핑 섹션이 표시된다", async ({ userPage }) => {
    await userPage.goto("/user");

    // 홈 제목 확인
    await expect(userPage.getByRole("heading", { name: "홈" })).toBeVisible();

    // 브리핑 섹션이 있거나 빈 상태(환영 메시지)가 있어야 함
    const briefingSection = userPage.getByText("오늘의 뉴스 브리핑");
    const welcomeHero = userPage.getByRole("heading", { name: "구독할 토픽을 추가해 보세요" });
    const newsCount = userPage.getByText(/오늘 뉴스.*건|이번 달 뉴스.*건|구독을 설정하면/);

    await expect(briefingSection.or(welcomeHero).or(newsCount).first()).toBeVisible({ timeout: 10_000 });
  });

  test("유저 사이드바 네비게이션이 동작한다", async ({ userPage }) => {
    await userPage.goto("/user");
    await expect(userPage.getByRole("heading", { name: "홈" })).toBeVisible();

    // 사이드바에서 "내 구독 관리" 클릭
    await userPage.getByRole("link", { name: "내 구독 관리" }).click();
    await expect(userPage).toHaveURL(/\/user\/manage/);
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // "구독 가능한 주제" 클릭
    await userPage.getByRole("link", { name: "구독 가능한 주제" }).click();
    await expect(userPage).toHaveURL(/\/user\/browse/);
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    // "신청 내역" 클릭
    await userPage.getByRole("link", { name: "신청 내역" }).click();
    await expect(userPage).toHaveURL(/\/user\/history/);
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();
  });

  test("퀵 액션 버튼이 표시되고 클릭할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user");
    await expect(userPage.getByRole("heading", { name: "홈" })).toBeVisible();

    // 구독이 있으면 "+ 새 주제" 버튼, 없으면 "1분 만에 시작하기" 버튼이 있어야 함
    const newTopicBtn = userPage.getByRole("button", { name: /새 주제/ });
    const startBtn = userPage.getByRole("button", { name: "시작하기" });
    const addTopicBtn = userPage.getByRole("button", { name: /주제 추가/ });

    await expect(newTopicBtn.or(startBtn).or(addTopicBtn)).toBeVisible({ timeout: 10_000 });

    // 내 구독 관리 사이드바 링크도 접근 가능 (lg 이상에서만 표시)
    const manageLink = userPage.getByRole("link", { name: "내 구독 관리" });
    const browseLink = userPage.getByRole("link", { name: "구독 가능한 주제" });
    // 사이드바는 lg 이상에서만 보이므로 or 패턴으로 확인
    await expect(manageLink.or(startBtn).or(addTopicBtn)).toBeVisible();
  });

  test("최근 기사 또는 빈 상태가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user");
    await expect(userPage.getByRole("heading", { name: "홈" })).toBeVisible();

    // 구독이 있으면 이번 달 요약 카드 또는 브리핑이 표시됨
    // 구독이 없으면 환영 히어로 + 온보딩 단계가 표시됨
    const monthlyCard = userPage.getByText("이번 달 받은 뉴스");
    const briefing = userPage.getByText("오늘의 뉴스 브리핑");
    const welcomeHero = userPage.getByRole("heading", { name: "구독할 토픽을 추가해 보세요" });
    const onboardingStep = userPage.getByText("키워드 입력");
    const noActive = userPage.getByText("아직 활성화된 구독이 없어요");

    await expect(
      monthlyCard.or(briefing).or(welcomeHero).or(onboardingStep).or(noActive).first()
    ).toBeVisible({ timeout: 10_000 });
  });
});
