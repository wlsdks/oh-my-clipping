import { test, expect } from "../fixtures/auth";
import { test as userTest } from "../fixtures/user-auth";
import { buildTestLabel, loginAsAdmin, createCategory, deleteCategory, createSource, deleteSource } from "../helpers/api";
import { isVisibleSafe, expectToast } from "../helpers/assertions";

test.describe("empty states — route interception", () => {
  test("소스 0건일 때 빈 상태와 CTA가 표시된다", async ({ adminPage }) => {
    // API 인터셉트: 소스 목록을 빈 페이지로 반환 (paginated)
    await adminPage.route("**/api/admin/sources*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
        });
      } else {
        route.continue();
      }
    });

    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 빈 상태 메시지 또는 안내 문구 확인
    const emptyText = adminPage.getByText(/조건에 맞는 소스가 없어요|첫 뉴스 소스를 추가해보세요/);
    await expect(emptyText).toBeVisible({ timeout: 10_000 });

    // "소스 추가" CTA 버튼이 여전히 활성화되어야 한다
    const addBtn = adminPage.getByRole("button", { name: /소스 추가/ });
    await expect(addBtn).toBeVisible();
    await expect(addBtn).toBeEnabled();
  });

  test("카테고리 0건일 때 빈 상태와 CTA가 표시된다", async ({ adminPage }) => {
    // API 인터셉트: 카테고리는 페이지 shape, 유저 요청/규칙 통계는 배열 shape
    await adminPage.route("**/api/admin/categories*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
        });
      } else {
        route.continue();
      }
    });
    await adminPage.route("**/api/admin/user-requests*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        });
      } else {
        route.continue();
      }
    });

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible({ timeout: 10_000 });

    // "운영중" 필터를 클릭하여 카테고리 리스트(ActiveSubscriptionsTable)의 빈 상태 확인
    const activeChip = adminPage.getByRole("button", { name: /운영중/ });
    if (await isVisibleSafe(activeChip)) {
      await activeChip.click();
    }

    // 빈 상태 메시지 확인 — 활성/대기/반려 탭별로 다른 메시지 가능
    const emptyText = adminPage.getByText(/조건에 맞는 주제가 없어요|구독이 없어요|등록된 주제가 없|해당하는 구독이 없어요|대기 중인 요청이 없어요|반려된 요청이 없어요|철회된 요청이 없어요|총 0개 구독/);
    await expect(emptyText.first()).toBeVisible({ timeout: 10_000 });
  });

  test("리뷰 큐 0건일 때 빈 메시지가 표시된다", async ({ adminPage }) => {
    // API 인터셉트: 리뷰 큐를 빈 배열로 반환
    await adminPage.route("**/api/admin/review-items*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        });
      } else {
        route.continue();
      }
    });

    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 빈 상태 메시지 확인
    const emptyText = adminPage.getByText(/검토할 항목이 없어요|검토할 뉴스가 없/);
    await expect(emptyText).toBeVisible({ timeout: 10_000 });
  });
});

userTest.describe("empty states — user pages", () => {
  userTest("유저 구독 0건일 때 '구독 가능한 주제' CTA가 표시된다", async ({ userPage }) => {
    // API 인터셉트: 유저 구독 목록을 빈 배열로 반환
    await userPage.route("**/api/user/requests*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        });
      } else {
        route.continue();
      }
    });

    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // 빈 상태 메시지 확인
    const emptyState = userPage.getByText(/활성화된 구독이 없/);
    await expect(emptyState).toBeVisible({ timeout: 10_000 });

    // CTA 버튼 확인 (구독 가능한 주제 또는 시작하기)
    const ctaBtn = userPage.getByRole("button", { name: /구독 가능한 주제|1분 만에 시작하기/ }).first();
    await expect(ctaBtn).toBeVisible();
    await expect(ctaBtn).toBeEnabled();
  });

  userTest("유저 신청 내역 0건일 때 빈 상태가 표시된다", async ({ userPage }) => {
    // API 인터셉트: 유저 신청 내역을 빈 배열로 반환
    await userPage.route("**/api/user/requests*", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        });
      } else {
        route.continue();
      }
    });

    await userPage.goto("/user/history");
    await expect(userPage.getByRole("heading", { name: "진행 상태" })).toBeVisible();

    // 빈 상태 메시지 확인
    const emptyText = userPage.getByText(/신청 내역이 없/);
    await expect(emptyText).toBeVisible({ timeout: 10_000 });
  });

  userTest("검색 결과 0건일 때 안내 메시지가 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/browse");
    await expect(userPage.getByRole("heading", { name: "구독 가능한 주제" })).toBeVisible();

    const searchInput = userPage.getByPlaceholder(/주제 검색|초성 검색/);
    await expect(searchInput).toBeVisible();

    // 존재하지 않는 검색어 입력
    await searchInput.fill("zzzzz존재하지않는주제12345");
    await userPage.waitForTimeout(300); // debounce wait

    // 검색 결과 없음 메시지 확인
    const noResults = userPage.getByText(/검색 결과가 없/);
    await expect(noResults).toBeVisible({ timeout: 10_000 });
  });
});

userTest.describe("boundary conditions — subscription limit", () => {
  userTest("구독 한도 도달 시 '새 주제' 버튼이 비활성화된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();

    // 구독 한도 도달 버튼 확인
    const limitBtn = userPage.getByRole("button", { name: /구독 한도/ });
    const hasLimit = await isVisibleSafe(limitBtn);

    if (hasLimit) {
      // 한도 도달 시 버튼이 disabled
      await expect(limitBtn).toBeDisabled();
    } else {
      // 한도 미도달 시 "새 주제" 버튼이 활성화
      const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가/ }).first();
      const hasNewTopic = await isVisibleSafe(newTopicBtn);
      if (hasNewTopic) {
        await expect(newTopicBtn).toBeEnabled();
      }
    }

    // 어떤 경우든 페이지가 정상 렌더링되어야 함
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible();
  });
});

test.describe("boundary conditions — long text", () => {
  test("200자 카테고리 이름이 UI에서 오버플로 없이 표시된다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);

    // 200자 이름 생성
    const longName = "E2E-긴이름-" + "가나다라마바사아자차카타파하".repeat(13);
    const trimmedName = longName.substring(0, 200);

    // 카테고리 생성 시도
    let categoryId: string | null = null;
    try {
      const cat = await createCategory(request, { name: trimmedName });
      categoryId = cat.id;
    } catch {
      // API가 긴 이름을 거부할 수 있음 — 거부하면 그것 자체가 올바른 동작
      test.skip(true, "API가 200자 카테고리 이름을 허용하지 않습니다");
      return;
    }

    try {
      await adminPage.goto("/admin/subscriptions");
      await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible();

      // 카테고리 목록으로 전환 (기본 "대기" 탭은 요청만 보여줌) — "주의" 칩 클릭 (소스 없는 카테고리)
      const warningChip = adminPage.getByRole("button", { name: /^주의/ }).first();
      if (await isVisibleSafe(warningChip, 3_000)) {
        await warningChip.click();
        await adminPage.waitForTimeout(500);
      }

      // 이름으로 검색하여 생성된 카테고리 찾기
      const searchInput = adminPage.getByPlaceholder(/이름으로 검색/);
      if (await isVisibleSafe(searchInput, 2_000)) {
        await searchInput.fill(trimmedName.substring(0, 20));
        await adminPage.waitForTimeout(500);
      }

      // 긴 이름이 페이지에 표시되는지 확인 (일부 또는 전체)
      const shortSnippet = trimmedName.substring(0, 20);
      const nameElement = adminPage.getByText(shortSnippet).first();
      await expect(nameElement).toBeVisible({ timeout: 10_000 });

      // 오버플로 검증: 이름 요소의 가로 크기가 뷰포트를 넘지 않는지 확인
      const box = await nameElement.boundingBox();
      if (box) {
        const viewportSize = adminPage.viewportSize();
        if (viewportSize) {
          // 요소가 뷰포트 밖으로 넘치면 안 된다
          expect(box.x + box.width).toBeLessThanOrEqual(viewportSize.width + 10);
        }
      }

      // 페이지가 크래시하지 않았는지 확인
      await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible();
    } finally {
      // 정리
      if (categoryId) {
        await deleteCategory(request, categoryId).catch(() => {});
      }
    }
  });

  test("매우 긴 소스 URL이 UI에서 오버플로 없이 표시된다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);

    // 200자 URL 생성
    const longPath = "a".repeat(150);
    const longUrl = `https://example.com/rss/${longPath}/feed.xml`;

    // 소스 생성을 위해 카테고리가 필요
    const catRes = await request.get("/api/admin/categories");
    const catData = (await catRes.json()) as { content: Array<{ id: string; name: string }> };
    const categories = catData.content ?? [];

    if (categories.length === 0) {
      test.skip(true, "카테고리가 없어 소스 생성 불가");
      return;
    }

    let sourceId: string | null = null;
    try {
      const source = await createSource(request, {
        name: buildTestLabel("긴URL"),
        url: longUrl,
        categoryId: categories[0].id,
      });
      sourceId = source.id;
    } catch {
      test.skip(true, "긴 URL 소스 생성 실패");
      return;
    }

    try {
      await adminPage.goto("/admin/sources");
      await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

      // 페이지가 정상 렌더링되는지 확인
      await expect(adminPage.locator("main")).toBeVisible();

      // 수평 스크롤바가 생기지 않는지 확인
      const hasHorizontalScroll = await adminPage.evaluate(() => {
        return document.documentElement.scrollWidth > document.documentElement.clientWidth;
      });

      // 수평 오버플로가 없어야 한다
      expect(hasHorizontalScroll).toBe(false);

      // 페이지가 크래시하지 않았는지 확인
      await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();
    } finally {
      if (sourceId) {
        await deleteSource(request, sourceId).catch(() => {});
      }
    }
  });
});
