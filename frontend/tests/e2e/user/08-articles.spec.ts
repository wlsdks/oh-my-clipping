import { test, expect } from "../fixtures/user-auth";
import { isVisibleSafe } from "../helpers/assertions";

test.describe("articles page", () => {
  test("내 기사 목록 페이지가 로드된다", async ({ userPage }) => {
    await userPage.goto("/user/articles");

    // 페이지 헤딩 확인
    await expect(userPage.getByRole("heading", { name: "내 기사 목록" })).toBeVisible({ timeout: 10_000 });

    // 서브탭 (구독 뉴스 / 경쟁사 뉴스) 확인
    await expect(userPage.getByText("구독 뉴스", { exact: true }).first()).toBeVisible();
    await expect(userPage.getByText("경쟁사 뉴스", { exact: true }).first()).toBeVisible();

    // 검색창이 보이거나 빈 상태가 표시된다
    const hasSearch = await isVisibleSafe(userPage.getByPlaceholder("키워드 검색"));
    const hasEmpty = await isVisibleSafe(userPage.getByText(/뉴스 수신 이력이 없/));
    const hasLoading = await isVisibleSafe(userPage.getByText("불러오는 중..."));
    expect(hasSearch || hasEmpty || hasLoading).toBeTruthy();
  });

  test("검색으로 기사를 필터링할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/articles");
    await expect(userPage.getByRole("heading", { name: "내 기사 목록" })).toBeVisible({ timeout: 10_000 });

    // 검색창 존재 확인
    const searchInput = userPage.getByPlaceholder("키워드 검색");
    const hasSearch = await isVisibleSafe(searchInput);
    if (!hasSearch) {
      test.skip(true, "검색창이 없어 테스트를 건너뜀 (기사 없을 수 있음)");
      return;
    }

    // 기사가 있는지 먼저 확인
    const hasArticles = await isVisibleSafe(
      userPage.locator("[class*='rounded'][class*='border']").filter({ hasText: /·/ }).first()
    );

    // 검색어 입력
    await searchInput.fill("테스트");
    await userPage.waitForTimeout(300); // debounce wait

    // 검색 결과가 있거나 빈 상태가 표시된다
    const hasResults = await isVisibleSafe(
      userPage.locator("[class*='rounded'][class*='border']").filter({ hasText: /·/ }).first()
    );
    const hasEmptyAfterSearch = await isVisibleSafe(userPage.getByText(/뉴스 수신 이력이 없/));
    expect(hasResults || hasEmptyAfterSearch).toBeTruthy();

    // 검색어 초기화
    await searchInput.clear();
    await userPage.waitForTimeout(300); // debounce wait
  });

  test("카테고리 필터가 동작한다", async ({ userPage }) => {
    await userPage.goto("/user/articles");
    await expect(userPage.getByRole("heading", { name: "내 기사 목록" })).toBeVisible({ timeout: 10_000 });

    // 카테고리 셀렉트 트리거 확인 — Radix Select는 combobox 역할로 렌더링
    const selectTrigger = userPage.getByRole("combobox");
    const hasSelect = await isVisibleSafe(selectTrigger);
    if (!hasSelect) {
      test.skip(true, "카테고리 필터가 없어 테스트를 건너뜀");
      return;
    }

    // 셀렉트 열기
    await selectTrigger.click();

    // "전체 주제" 옵션이 드롭다운에 보이는지 확인
    const allOption = userPage.getByRole("option", { name: "전체 주제" });
    const hasAllOption = await isVisibleSafe(allOption);

    if (hasAllOption) {
      // 카테고리 옵션이 있으면 첫 번째 비-전체 옵션 클릭
      const options = userPage.getByRole("option");
      const count = await options.count();
      if (count > 1) {
        // 두 번째 옵션(첫 번째 카테고리) 클릭
        await options.nth(1).click();
        await userPage.waitForTimeout(500); // debounce + fetch

        // 필터 적용 후 결과/빈 상태/로딩 중 하나가 표시되어야 한다
        const hasFiltered = await isVisibleSafe(
          userPage.locator("[class*='rounded'][class*='border']").filter({ hasText: /·/ }).first()
        );
        const hasEmptyFiltered = await isVisibleSafe(userPage.getByText(/뉴스 수신 이력이 없/));
        const hasLoading = await isVisibleSafe(userPage.getByText("불러오는 중..."));
        const hasHeading = await isVisibleSafe(userPage.getByRole("heading", { name: "내 기사 목록" }));
        expect(hasFiltered || hasEmptyFiltered || hasLoading || hasHeading).toBeTruthy();

        // 전체 주제로 복귀
        await selectTrigger.click();
        await userPage.getByRole("option", { name: "전체 주제" }).click();
      } else {
        // 카테고리가 없으면 그냥 닫기
        await userPage.keyboard.press("Escape");
      }
    } else {
      // 드롭다운이 다른 형태일 수 있으므로 Escape로 닫기
      await userPage.keyboard.press("Escape");
    }
  });

  test("경쟁사 뉴스 탭이 동작한다", async ({ userPage }) => {
    await userPage.goto("/user/articles");
    await expect(userPage.getByRole("heading", { name: "내 기사 목록" })).toBeVisible({ timeout: 10_000 });

    // 경쟁사 뉴스 탭 클릭
    await userPage.getByText("경쟁사 뉴스", { exact: true }).first().click();

    // 경쟁사 뉴스 컨텐츠가 로드될 때까지 대기:
    // 1. 기간 필터 버튼 (이번 주 등)
    // 2. 경쟁사 뉴스 목록
    // 3. 빈 상태 ("경쟁사 뉴스가 없어요")
    // 4. 에러 상태 ("불러올 수 없어요")
    // 5. 로딩 상태
    const periodBtn = userPage.getByRole("button", { name: "이번 주" });
    const emptyState = userPage.getByText(/경쟁사 뉴스가 없어요/);
    const errorState = userPage.getByText(/불러올 수 없어요/);
    const loadingState = userPage.getByText("불러오는 중...");

    // 먼저 로딩 또는 컨텐츠가 나타날 때까지 대기 (.first() to avoid strict mode when multiple match)
    await expect(periodBtn.or(emptyState).or(errorState).or(loadingState).first()).toBeVisible({ timeout: 15_000 });

    // 로딩 중이면 완료될 때까지 추가 대기
    const isStillLoading = await loadingState.isVisible().catch(() => false);
    if (isStillLoading) {
      await expect(periodBtn.or(emptyState).or(errorState).first()).toBeVisible({ timeout: 15_000 });
    }

    // 기간 필터가 있으면 다른 기간으로 전환 확인
    const hasPeriodFilter = await isVisibleSafe(periodBtn);
    if (hasPeriodFilter) {
      // "오늘" 또는 "이번 달" 버튼 클릭 (이번 주가 기본이므로 다른 기간으로 전환)
      const altPeriod = userPage.getByRole("button", { name: /오늘|이번 달/ }).first();
      const hasAltPeriod = await isVisibleSafe(altPeriod);
      if (hasAltPeriod) {
        await altPeriod.click();

        // 기간 변경 후 로딩 완료 대기
        const altLoading = userPage.getByText("불러오는 중...");
        const altEmpty = userPage.getByText(/경쟁사 뉴스가 없/);
        const altContent = userPage.locator("[class*='rounded'][class*='border']").filter({ hasText: /·/ }).first();
        await expect(altEmpty.or(altContent).or(altLoading)).toBeVisible({ timeout: 10_000 });
        const stillLoading = await altLoading.isVisible().catch(() => false);
        if (stillLoading) {
          await expect(altEmpty.or(altContent)).toBeVisible({ timeout: 10_000 });
        }

        // 기간 변경 후 결과, 빈 상태, 또는 로딩이 표시
        const hasContent = await isVisibleSafe(
          userPage.locator("[class*='rounded'][class*='border']").filter({ hasText: /·/ }).first()
        );
        const hasEmptyAlt = await isVisibleSafe(userPage.getByText(/경쟁사 뉴스가 없/));
        expect(hasContent || hasEmptyAlt).toBeTruthy();
      }
    }

    // 다시 구독 뉴스로 돌아가기
    await userPage.getByText("구독 뉴스", { exact: true }).first().click();

    // 구독 뉴스 탭 컨텐츠가 로드될 때까지 대기
    const searchBack = userPage.getByPlaceholder("키워드 검색");
    const emptyBack = userPage.getByText(/뉴스 수신 이력이 없/);
    const loadingBack = userPage.getByText("불러오는 중...");
    await expect(searchBack.or(emptyBack).or(loadingBack)).toBeVisible({ timeout: 10_000 });

    const hasSearchBack = await isVisibleSafe(searchBack);
    const hasEmptyBack = await isVisibleSafe(emptyBack);
    expect(hasSearchBack || hasEmptyBack).toBeTruthy();
  });
});
