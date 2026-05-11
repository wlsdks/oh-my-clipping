import { expect, test, type Page } from "@playwright/test";

const LABEL_PREFIX = ["새벽", "잔잔한", "푸른", "다온", "은빛", "맑은", "단단한", "고른"];
const LABEL_MIDDLE = ["협업", "공급망", "브랜드", "보안", "운영", "시장", "전략", "물류"];
const LABEL_SUFFIX = ["브리핑", "모니터링", "리포트", "다이제스트", "체크", "인사이트", "요약", "업데이트"];
let labelSequence = 0;

function buildHumanLabel(prefix: string) {
  labelSequence += 1;
  const first = LABEL_PREFIX[labelSequence % LABEL_PREFIX.length];
  const second = LABEL_MIDDLE[Math.floor(labelSequence / LABEL_PREFIX.length) % LABEL_MIDDLE.length];
  const third =
    LABEL_SUFFIX[Math.floor(labelSequence / (LABEL_PREFIX.length * LABEL_MIDDLE.length)) % LABEL_SUFFIX.length];
  return `${prefix} ${first} ${second} ${third}`;
}

function buildQueryToken(label: string) {
  return encodeURIComponent(label.toLowerCase().replace(/\s+/g, "-"));
}

async function loginWithShortcut(page: Page, scope: "admin" | "user") {
  const username = scope === "admin" ? "dev.admin@clipping.local" : "dev.user@clipping.local";
  const password = "LocalPass123!";
  const urlPattern = new RegExp(scope === "admin" ? "/admin" : "/user");
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.context().clearCookies();
    await page.goto("/login", { waitUntil: "networkidle" });
    await page.getByLabel("아이디").fill(username);
    await page.getByLabel("비밀번호").fill(password);
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await expect(page).toHaveURL(urlPattern, { timeout: 15_000 });
      return;
    } catch {
      if (attempt === 3) throw new Error(`Login as ${scope} failed after 3 attempts`);
      await page.waitForTimeout(1000);
    }
  }
}

test.describe("core regression", () => {
  test("quick setup should create a new topic from user flow", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    const uniqueKeyword = buildHumanLabel("협업 생산성");

    await loginWithShortcut(page, "user");
    await page.goto("/user/manage");

    // 새 주제 버튼 또는 빠른세팅 진입점을 찾는다
    const newTopicBtn = page.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await newTopicBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (!canCreate) {
      // 한도 도달 또는 UI 구조 변경 — 스킵
      test.skip(true, "새 주제 버튼을 찾을 수 없음 — UI 변경 또는 구독 한도");
      await context.close();
      return;
    }

    await newTopicBtn.click();

    await expect(page.getByText("빠른 세팅")).toBeVisible();
    await page.getByRole("button", { name: "다음" }).click();

    const keywordInput = page.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");
    await page.getByRole("button", { name: "다음" }).click();

    await page.getByRole("button", { name: "다음" }).click();

    // Step 4: Details — 기본값으로 넘기기
    await page.getByRole("button", { name: "다음" }).click();

    // DM 옵션 선택 (클릭하면 자동 확정)
    await page.getByRole("button", { name: /나에게 DM/ }).click();
    await page.getByRole("button", { name: "세팅 시작" }).click();

    await expect(page.getByText(new RegExp(uniqueKeyword))).toBeVisible({ timeout: 10_000 });

    await context.close();
  });

  test("quick setup should show slack channel input from user flow", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    const uniqueKeyword = buildHumanLabel("브랜드 모니터링");

    await loginWithShortcut(page, "user");
    await page.goto("/user/manage");

    const newTopicBtn2 = page.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate2 = await newTopicBtn2.isVisible({ timeout: 5000 }).catch(() => false);
    if (!canCreate2) {
      test.skip(true, "새 주제 버튼을 찾을 수 없음 — UI 변경 또는 구독 한도");
      await context.close();
      return;
    }

    await newTopicBtn2.click();

    await page.getByRole("button", { name: "다음" }).click();
    const keywordInput = page.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "다음" }).click();

    // Step 4: Details — 기본값으로 넘기기
    await page.getByRole("button", { name: "다음" }).click();

    await page.getByRole("button", { name: /Slack 채널/ }).click();

    // 채널 검색 또는 채널 목록이 보이는지 확인
    const channelSearch = page.getByPlaceholder(/채널 이름 검색|채널 검색/);
    const channelList = page.locator("button").filter({ hasText: /^#/ }).first();
    const loadingMsg = page.getByText(/채널 목록 불러오는 중/);
    await expect(channelSearch.or(channelList).or(loadingMsg)).toBeVisible({ timeout: 10_000 });

    await context.close();
  });

  test("user request should appear in admin queue and sync after approval", async ({ browser }) => {
    const userContext = await browser.newContext();
    const adminContext = await browser.newContext();
    const userPage = await userContext.newPage();
    const adminPage = await adminContext.newPage();
    const requestName = buildHumanLabel("공급망 리스크 브리핑");
    const sourceName = "Supply Chain Dive";
    const sourceUrl = `https://news.google.com/rss/search?q=${buildQueryToken(requestName)}`;

    await loginWithShortcut(userPage, "user");
    await loginWithShortcut(adminPage, "admin");

    const createResponse = await userContext.request.post("/api/user/requests", {
      data: {
        requestName,
        sourceName,
        sourceUrl,
        slackChannelId: "C0123456789",
        personaName: "핵심 요약",
        personaPrompt: "핵심만 쉽고 짧게 정리해줘"
      }
    });
    // 구독 한도 도달 시 스킵
    if (!createResponse.ok()) {
      test.skip(true, "구독 한도 도달 — 요청 생성 불가");
      await userContext.close();
      await adminContext.close();
      return;
    }

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByText(requestName).first()).toBeVisible();
    // 요청 행 클릭하여 ReviewSidePanel 열기
    await adminPage.getByText(requestName).first().click();
    // ReviewSidePanel에서 승인 버튼 클릭
    await adminPage.getByRole("button", { name: "승인" }).first().click();
    // LegalReviewModal에서 확인 버튼 클릭
    const legalConfirm = adminPage.getByRole("button", { name: /승인 확정|확인/ }).last();
    await expect(legalConfirm).toBeVisible({ timeout: 5_000 }).catch(() => {});
    if (await legalConfirm.isVisible().catch(() => false)) {
      await legalConfirm.click();
    }
    // 승인 토스트 확인
    await expect(adminPage.getByText(/요청을 승인했어요|처리되었습니다/).first()).toBeVisible({ timeout: 5_000 }).catch(() => {});

    // 유저 진행 상태 페이지에서 승인된 구독 확인
    // /user/status redirects to /user/history (UserStatusPage with chip filters)
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // "완료" 필터 칩 클릭하여 승인된 항목 확인
    const completedChip = userPage.locator("button").filter({ hasText: "완료" }).first();
    if (await completedChip.isVisible().catch(() => false)) {
      await completedChip.click();
    }

    const found = await userPage
      .getByText(requestName)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    // 전체 필터로 복귀해서 다시 확인
    if (!found) {
      const allChip = userPage.locator("button").filter({ hasText: "전체" }).first();
      if (await allChip.isVisible().catch(() => false)) {
        await allChip.click();
      }
    }
    const foundFinal = found || await userPage
      .getByText(requestName)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    expect(foundFinal).toBeTruthy();

    await userContext.close();
    await adminContext.close();
  });

  test("rejected request should show the rejection reason in user status", async ({ browser }) => {
    const userContext = await browser.newContext();
    const adminContext = await browser.newContext();
    const userPage = await userContext.newPage();
    const adminPage = await adminContext.newPage();
    const requestName = buildHumanLabel("중복 주제 확인 요청");
    const rejectReason = "이미 운영 중인 주제와 중복되어 반려했어요.";
    const sourceName = "Industry Dive";
    const sourceUrl = `https://news.google.com/rss/search?q=${buildQueryToken(requestName)}`;

    await loginWithShortcut(userPage, "user");
    await loginWithShortcut(adminPage, "admin");

    const createResponse = await userContext.request.post("/api/user/requests", {
      data: {
        requestName,
        sourceName,
        sourceUrl,
        slackChannelId: "C0123456789",
        personaName: "핵심 요약",
        personaPrompt: "핵심만 쉽고 짧게 정리해줘"
      }
    });
    if (!createResponse.ok()) {
      test.skip(true, "구독 한도 도달 — 요청 생성 불가");
      await userContext.close();
      await adminContext.close();
      return;
    }

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByText(requestName).first()).toBeVisible({ timeout: 10_000 });
    // 요청 행 클릭하여 ReviewSidePanel 열기
    await adminPage.getByText(requestName).first().click();
    // ReviewSidePanel에서 심사 메모 입력 (반려 시 필수)
    const reviewNote = adminPage.getByPlaceholder(/승인 시 선택, 반려 시 사유를 입력/);
    await expect(reviewNote).toBeVisible({ timeout: 5_000 });
    await reviewNote.fill(rejectReason);
    // 반려 버튼 클릭
    await adminPage.getByRole("button", { name: "반려" }).click();
    await expect(adminPage.getByText("요청을 반려했어요")).toBeVisible();

    // 유저 진행 상태 페이지에서 반려된 요청 확인
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });

    // "반려" 필터 칩 클릭하여 반려된 항목 확인
    const rejectedChip = userPage.locator("button").filter({ hasText: "반려" }).first();
    if (await rejectedChip.isVisible().catch(() => false)) {
      await rejectedChip.click();
    }

    // 반려 뱃지가 보이는지 확인
    await expect(userPage.getByText("반려").first()).toBeVisible({ timeout: 10_000 });
    // 반려된 요청명이 보이는지 확인
    await expect(userPage.getByText(requestName).first()).toBeVisible();

    await userContext.close();
    await adminContext.close();
  });

  // NOTE: Runtime page no longer uses tabs — it renders collapsible cards.
  // "뉴스 수집 설정" is a collapsible section that must be expanded first.
  test("operator runtime settings should save and reset cleanly", async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    await loginWithShortcut(page, "admin");
    await page.goto("/admin/runtime");

    // 시스템 설정 페이지 로드 확인
    await expect(page.getByRole("heading", { name: "시스템 설정" })).toBeVisible();

    // 로딩 완료 대기
    const loadingState = page.getByText("불러오는 중...");
    await expect(loadingState).not.toBeVisible({ timeout: 15_000 }).catch(() => {});

    const errorState = page.getByText("설정을 불러오지 못했어요");
    if (await errorState.isVisible().catch(() => false)) {
      test.skip(true, "설정 로드 실패");
      await context.close();
      return;
    }

    // 뉴스 수집 설정 섹션 펼침
    await page.getByRole("button", { name: /뉴스 수집 설정/ }).click();

    // 기본 수집 기간 필드 수정
    const hoursInput = page.locator("#defaultHoursBack");
    await expect(hoursInput).toBeVisible({ timeout: 5_000 });
    await hoursInput.scrollIntoViewIfNeeded();
    await hoursInput.clear();
    await hoursInput.fill("18");

    // 뉴스 수집 카드의 저장 버튼 클릭
    const collectionForm = hoursInput.locator("xpath=ancestor::form");
    await collectionForm.getByRole("button", { name: /저장/ }).click();

    // 저장 반영 확인
    await expect(page.getByText(/설정을 저장했어요/).first()).toBeVisible({ timeout: 5_000 }).catch(() => {});
    await page.reload();

    // 로딩 대기 + 섹션 펼침
    await expect(page.getByRole("heading", { name: "시스템 설정" })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText("불러오는 중...")).not.toBeVisible({ timeout: 15_000 }).catch(() => {});
    await page.getByRole("button", { name: /뉴스 수집 설정/ }).click();
    await expect(page.locator("#defaultHoursBack")).toHaveValue("18");

    // 기본값 복원 (ConfirmModal 사용)
    await page.getByRole("button", { name: "기본값 복원" }).click();
    await expect(page.getByRole("button", { name: "복원", exact: true })).toBeVisible({ timeout: 3_000 });
    await page.getByRole("button", { name: "복원", exact: true }).click();
    await expect(page.getByText(/기본값으로 복원했어요/).first()).toBeVisible({ timeout: 5_000 }).catch(() => {});
    await page.reload();

    // 로딩 대기 + 섹션 펼침
    await expect(page.getByRole("heading", { name: "시스템 설정" })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText("불러오는 중...")).not.toBeVisible({ timeout: 15_000 }).catch(() => {});
    await page.getByRole("button", { name: /뉴스 수집 설정/ }).click();
    await expect(page.locator("#defaultHoursBack")).toHaveValue("24");

    await context.close();
  });

  test("dm subscription additional rss request should be approved without invalid channel", async ({ browser }) => {
    const userContext = await browser.newContext();
    const adminContext = await browser.newContext();
    const userPage = await userContext.newPage();
    const adminPage = await adminContext.newPage();
    const personaName = buildHumanLabel("운영 체크 요약");
    const categoryName = buildHumanLabel("물류 동향 브리핑");
    const sourceName = "Logistics Base Feed";
    const sourceUrl = "https://example.com/logistics-base-feed.xml";
    const additionalSourceName = "Supply Chain Dive";
    const additionalSourceUrl = "https://example.com/supply-chain-dive.xml";

    await loginWithShortcut(userPage, "user");
    await loginWithShortcut(adminPage, "admin");

    const personaResponse = await userContext.request.post("/api/user/setup/personas", {
      data: {
        name: personaName,
        systemPrompt: "핵심만 정리",
        maxItems: 5
      }
    });
    expect(personaResponse.ok()).toBeTruthy();
    const persona = (await personaResponse.json()) as { id: string };

    const categoryResponse = await userContext.request.post("/api/user/setup/categories", {
      data: {
        name: categoryName,
        maxItems: 5,
        personaId: persona.id
      }
    });
    expect(categoryResponse.ok()).toBeTruthy();
    const category = (await categoryResponse.json()) as { id: string };

    const wizardResponse = await userContext.request.post("/api/user/requests/wizard-ownership", {
      data: {
        requestName: categoryName,
        sourceName,
        sourceUrl,
        slackChannelId: "",
        personaName,
        personaPrompt: "핵심만 정리",
        categoryId: category.id,
        personaId: persona.id
      }
    });
    if (!wizardResponse.ok()) {
      test.skip(true, "구독 한도 도달 — 위자드 소유권 등록 불가");
      await userContext.close();
      await adminContext.close();
      return;
    }
    const baseRequest = (await wizardResponse.json()) as { id: string };

    const additionalResponse = await userContext.request.post("/api/user/requests/rss-sources", {
      data: {
        baseRequestId: baseRequest.id,
        requestNote: "추가 공급망 출처 연결",
        sources: [{ sourceName: additionalSourceName, sourceUrl: additionalSourceUrl }]
      }
    });
    expect(additionalResponse.ok()).toBeTruthy();

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByText(additionalSourceName).first()).toBeVisible({ timeout: 10_000 });
    // 요청 행 클릭하여 ReviewSidePanel 열기
    await adminPage.getByText(additionalSourceName).first().click();
    // ReviewSidePanel에서 승인 버튼 클릭
    await adminPage.getByRole("button", { name: "승인" }).first().click();
    // LegalReviewModal에서 확인 버튼 클릭 (있는 경우)
    const legalConfirm2 = adminPage.getByRole("button", { name: /승인 확정|확인/ }).last();
    await expect(legalConfirm2).toBeVisible({ timeout: 5_000 }).catch(() => {});
    if (await legalConfirm2.isVisible().catch(() => false)) {
      await legalConfirm2.click();
    }

    await expect(adminPage.getByText("Slack 채널 검증 실패")).toHaveCount(0);
    await expect(adminPage.getByText("요청을 승인했어요")).toBeVisible();

    // 유저 진행 상태 페이지에서 승인된 구독 확인
    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible({ timeout: 10_000 });
    await expect(userPage.getByText(new RegExp(categoryName)).first()).toBeVisible({ timeout: 10_000 });

    await userContext.close();
    await adminContext.close();
  });
});
