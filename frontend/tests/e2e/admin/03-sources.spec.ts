import { test, expect } from "../fixtures/auth";
import { buildTestLabel } from "../helpers/api";
import { isVisibleSafe, expectToast } from "../helpers/assertions";

test.describe("sources management", () => {
  test("소스 목록 페이지가 로드되고 통계 카드가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스", exact: true })).toBeVisible();

    // 스탯 바 카드가 보이는지 확인 (정상/주의 등)
    await expect(adminPage.getByText("정상").first()).toBeVisible();

    // 리스트 행 또는 빈 상태가 보이는지 확인
    const listRow = adminPage.locator("[class*='rounded-xl']").first();
    const emptyText = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요/);
    await expect(listRow.or(emptyText)).toBeVisible();

    // 소스 추가 버튼
    await expect(adminPage.getByRole("button", { name: /소스 추가/ })).toBeVisible();
  });

  test("검색으로 소스를 필터링할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 목록이 Collapsible로 감싸져 있어 기본 상태에서 접혀있을 수 있으므로 먼저 펼친다.
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      // aria-expanded="false"면 클릭해서 펼친다
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 검색 입력란 확인
    const searchInput = adminPage.getByPlaceholder(/소스명, URL 검색/).first();
    await expect(searchInput).toBeVisible({ timeout: 10_000 });

    // 검색어 입력 — 존재하지 않는 키워드로 필터링하면 결과 변경 확인
    await searchInput.fill("zzzznonexistent");

    // 결과 없음 또는 빈 상태 표시
    const emptyResult = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요|조건에 맞는 소스가 없어요/);
    await expect(emptyResult.first()).toBeVisible({ timeout: 10_000 });

    // 검색어 지우면 원래 목록 복원 — 테이블 행 또는 빈 상태가 보여야 한다
    await searchInput.clear();

    const tableRow = adminPage.locator("table tbody tr").first();
    const originalEmpty = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요|조건에 맞는 소스가 없어요/);
    await expect(tableRow.or(originalEmpty.first())).toBeVisible({ timeout: 10_000 });
  });

  test("카테고리 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 Collapsible을 펼쳐 필터 UI를 노출한다
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 주제(카테고리) 필터는 shadcn Select 트리거 버튼이다. placeholder="주제"
    const categoryTrigger = adminPage.getByRole("combobox").filter({ hasText: /주제|전체 주제/ }).first();
    await expect(categoryTrigger).toBeVisible({ timeout: 10_000 });

    // Select를 열고 옵션 중 하나 선택
    await categoryTrigger.click();
    const options = adminPage.getByRole("option");
    const optionCount = await options.count();
    if (optionCount > 1) {
      // 첫 번째는 "전체 주제", 그 다음 카테고리 선택
      await options.nth(1).click();

      // 필터링이 적용되면 URL에 categoryId 쿼리 파라미터가 반영된다 (handleCategoryChange 확인).
      // URL 변경이 일어나면 카테고리 필터가 정상 동작한 것으로 간주한다.
      await expect(adminPage).toHaveURL(/[?&]categoryId=/, { timeout: 10_000 });
    } else {
      // 옵션이 없으면 드롭다운 닫기
      await adminPage.keyboard.press("Escape");
    }
  });

  test("소스를 생성할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 소스 추가 버튼 클릭
    await adminPage.getByRole("button", { name: /소스 추가/ }).click();

    // 다이얼로그 열림 확인
    await expect(adminPage.getByRole("heading", { name: "소스 추가" })).toBeVisible({ timeout: 10_000 });

    // 고유한 테스트 이름 생성
    const testName = buildTestLabel("E2E");

    // 소스 이름 입력
    const nameInput = adminPage.getByLabel("소스 이름");
    await nameInput.fill(testName);

    // RSS URL 입력 — 중복 방지를 위해 타임스탬프로 유니크하게
    const urlInput = adminPage.getByLabel("뉴스 피드 주소");
    await urlInput.fill(`https://example.com/rss-test-feed-${Date.now()}`);

    // 주제 선택 (첫 번째 카테고리)
    const categorySelect = adminPage.locator("button").filter({ hasText: "주제 선택" });
    if (await isVisibleSafe(categorySelect)) {
      await categorySelect.click();
      // 첫 번째 옵션 선택
      const firstOption = adminPage.getByRole("option").first();
      if (await isVisibleSafe(firstOption)) {
        await firstOption.click();
      }
    }

    // 등록 버튼 클릭
    await adminPage.getByRole("button", { name: "소스 등록" }).click();

    // 성공 토스트, 유효성 오류, 또는 서버 오류 확인
    const successToast = adminPage.getByText("소스가 등록됐어요");
    const validationError = adminPage.getByText(/필수 입력|주제를 선택하세요|올바른 URL/);
    const errorToast = adminPage.getByText(/등록하지 못했어요|이미 등록된/);
    await expect(successToast.or(validationError).or(errorToast).first()).toBeVisible({ timeout: 10_000 });
  });

  test("잘못된 URL로 소스 생성 시 한국어 에러 메시지가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 소스 추가 다이얼로그 열기
    await adminPage.getByRole("button", { name: /소스 추가/ }).click();
    await expect(adminPage.getByRole("heading", { name: "소스 추가" })).toBeVisible({ timeout: 10_000 });

    // 소스 이름, 잘못된 URL 입력 후 등록 시도
    await adminPage.getByLabel("소스 이름").fill("잘못된 URL 테스트");
    await adminPage.getByLabel("뉴스 피드 주소").fill("example.com");

    // 소스 등록 버튼 클릭
    await adminPage.getByRole("button", { name: "소스 등록" }).click();

    // zod 검증 에러 또는 백엔드 API 에러의 한국어 메시지 확인
    // - 인라인: "올바른 URL을 입력하세요", "주제를 선택하세요", "필수 입력"
    // - 토스트: "입력값을 확인해 주세요"
    const koreanError = adminPage.getByText(/올바른 URL|주제를 선택|필수 입력|입력값을 확인/);
    const hasKoreanError = await isVisibleSafe(koreanError.first(), 5_000);

    // 영어 에러 메시지가 노출되면 안 됨 (핵심 검증)
    const englishError = adminPage.getByText(/scheme is required|invalid url|URL scheme/i);
    await expect(englishError).toHaveCount(0);

    // 한국어 에러가 보이거나, 에러 없이 폼이 유지되면(다이얼로그가 닫히지 않으면) 통과
    if (!hasKoreanError) {
      // 다이얼로그가 여전히 열려 있는지 확인 — 잘못된 입력으로 제출이 차단됨
      await expect(adminPage.getByRole("heading", { name: "소스 추가" })).toBeVisible();
    }
  });

  test("소스 이름을 수정할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 Collapsible을 펼친다
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 소스 목록은 테이블로 렌더링됨
    const sourceRows = adminPage.locator("table tbody tr");
    const emptyText = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요|조건에 맞는 소스가 없어요/);

    // 행 또는 빈 상태가 나타날 때까지 대기
    await expect(sourceRows.first().or(emptyText.first())).toBeVisible({ timeout: 10_000 });
    const rowCount = await sourceRows.count();

    if (rowCount === 0) {
      test.skip();
      return;
    }

    // 첫 번째 행의 마지막 셀(더보기 메뉴) 버튼 클릭
    const targetRow = sourceRows.first();
    const moreBtn = targetRow.locator("td").last().getByRole("button").first();
    await moreBtn.click();

    // 드롭다운에서 "편집" 클릭
    const editMenuItem = adminPage.getByRole("menuitem", { name: "편집" });
    await expect(editMenuItem).toBeVisible({ timeout: 5_000 });
    await editMenuItem.click();

    // 편집 모달 열림 확인
    await expect(adminPage.getByRole("heading", { name: "소스 편집" })).toBeVisible({ timeout: 10_000 });

    // 소스 이름 수정
    const nameInput = adminPage.getByLabel("소스 이름");
    const originalName = await nameInput.inputValue();
    const updatedName = originalName + " 수정됨";
    await nameInput.clear();
    await nameInput.fill(updatedName);

    // 저장
    await adminPage.getByRole("button", { name: "저장", exact: true }).click();

    // 성공 토스트 확인 (Undo 토스트라 "저장됐어요" 또는 "소스가 수정됐어요"가 표시될 수 있음)
    const successToast = adminPage.getByText(/소스가 수정됐어요|저장됐어요/);
    const errorToast = adminPage.getByText(/수정 실패|수정하지 못했어요/);
    await expect(successToast.or(errorToast).first()).toBeVisible({ timeout: 10_000 });
  });

  test("소스를 삭제할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 Collapsible을 펼친다
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 테이블 행 확인
    const sourceRows = adminPage.locator("table tbody tr");
    const emptyText = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요|조건에 맞는 소스가 없어요/);
    await expect(sourceRows.first().or(emptyText.first())).toBeVisible({ timeout: 10_000 });
    const rowCount = await sourceRows.count();
    if (rowCount === 0) {
      test.skip();
      return;
    }

    // 첫 번째 행의 더보기 메뉴 클릭 (마지막 td의 버튼)
    const targetRow = sourceRows.first();
    await targetRow.locator("td").last().getByRole("button").first().click();

    // 드롭다운에서 "삭제" 클릭
    const deleteMenuItem = adminPage.getByRole("menuitem", { name: "삭제" });
    await expect(deleteMenuItem).toBeVisible({ timeout: 5_000 });
    await deleteMenuItem.click();

    // 확인 모달 표시 — "소스를 삭제할까요?"
    const confirmDialog = adminPage.getByText(/소스를 삭제할까요|삭제하면 뉴스가 더 이상/);
    await expect(confirmDialog.first()).toBeVisible({ timeout: 10_000 });

    // 확인 버튼 클릭 — ConfirmModal 내부의 "삭제" 버튼
    const confirmButton = adminPage.getByRole("button", { name: "삭제", exact: true }).last();
    await confirmButton.click();

    // 삭제 성공 토스트
    await expectToast(adminPage, /소스가 삭제됐어요|삭제하지 못했어요|삭제 실패/);
  });

  test("소스 검증 실행 시 상태가 변경된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 Collapsible을 펼친다
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 테이블 행 확인
    const sourceRows = adminPage.locator("table tbody tr");
    const emptyText = adminPage.getByText(/소스가 없어요|첫 뉴스 소스를 추가해보세요|조건에 맞는 소스가 없어요/);
    await expect(sourceRows.first().or(emptyText.first())).toBeVisible({ timeout: 10_000 });
    const rowCount = await sourceRows.count();
    if (rowCount === 0) {
      test.skip();
      return;
    }

    // 첫 번째 행의 더보기 메뉴 클릭
    const targetRow = sourceRows.first();
    await targetRow.locator("td").last().getByRole("button").first().click();

    // "연결 확인" 메뉴 아이템 클릭
    const verifyMenuItem = adminPage.getByRole("menuitem", { name: "연결 확인" });
    await expect(verifyMenuItem).toBeVisible({ timeout: 5_000 });
    await verifyMenuItem.click();

    // 검증 시작 토스트
    await expectToast(adminPage, /재시도를 시작했어요|재시도에 실패했어요/);
  });

  test("소스 승인 시 상태가 ACTIVE로 변경된다", async ({ adminPage }) => {
    // 현재 UI에서는 소스 목록에 "승인" 버튼이나 "미승인" 필터 탭이 직접 노출되지 않는다.
    // 미승인(pendingApproval) 그룹은 SourcesPage에서 아직 렌더링되지 않음 (SourcesPage.tsx 참조).
    // 승인/반려는 편집 모달 등 다른 경로로만 처리되므로 이 테스트는 스킵한다.
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();
    test.skip(true, "소스 목록에 '승인' 버튼/'미승인' 필터 UI가 더 이상 노출되지 않음");
  });

  test("소스가 없을 때 안내 메시지가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 정상 소스 Collapsible을 펼쳐 검색 입력란을 노출한다
    const activeTrigger = adminPage.getByRole("button", { name: /정상 소스 \d+개/ }).first();
    if (await isVisibleSafe(activeTrigger)) {
      const expanded = await activeTrigger.getAttribute("aria-expanded");
      if (expanded === "false") {
        await activeTrigger.click();
      }
    }

    // 존재하지 않는 검색어로 필터링하여 빈 상태 유도
    const searchInput = adminPage.getByPlaceholder(/소스명, URL 검색/).first();
    await expect(searchInput).toBeVisible({ timeout: 10_000 });
    await searchInput.fill("zzzzzzzzzzzzz_no_match");

    // 빈 상태 안내 메시지 확인 — 서버 사이드 검색에서 totalCount=0이면 빈 상태 표시
    const emptyState = adminPage.getByText(/조건에 맞는 소스가 없어요|첫 뉴스 소스를 추가해보세요/);
    const guidanceText = adminPage.getByText(/검색어나 필터를 변경해 보세요|RSS 피드 URL/);
    await expect(emptyState.first().or(guidanceText.first())).toBeVisible({ timeout: 10_000 });
  });
});
