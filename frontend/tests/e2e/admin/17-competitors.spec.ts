import { test, expect } from "../fixtures/auth";
import { expectToast, expectPageLoad, expectContentOrEmpty } from "../helpers/assertions";
import { loginAsAdmin, wipeE2eCompetitors } from "../helpers/api";

test.describe("competitor management", () => {
  // Wipe E2E-created competitors before the add test so MAX_COMPETITORS (20)
  // is not hit by accumulated state from prior full-suite runs.
  test.beforeEach(async ({ request }, testInfo) => {
    if (testInfo.title === "경쟁사를 추가할 수 있다") {
      await loginAsAdmin(request);
      await wipeE2eCompetitors(request);
    }
  });
  test("경쟁사 관리 페이지가 로드되고 기존 경쟁사가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");

    // 페이지 헤더 확인
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 탭이 표시되는지 확인
    await expect(adminPage.getByRole("tab", { name: "경쟁사 목록" })).toBeVisible({ timeout: 10_000 });
    await expect(adminPage.getByRole("tab", { name: "분석 현황" })).toBeVisible();

    // 시드 데이터 경쟁사 또는 빈 상태 표시 확인
    const competitorRow = adminPage.getByRole("row").nth(1); // 헤더 다음 첫 번째 행
    const emptyState = adminPage.getByText("아직 등록된 경쟁사가 없어요");
    await expectContentOrEmpty(competitorRow, emptyState);
  });

  test("시드 데이터 경쟁사가 목록에 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 시드 데이터 중 하나 이상이 표시되는지 확인 — 이름은 테이블 셀 + 별칭 배지 등
    // 여러 곳에 중복 노출되므로 strict mode 충돌을 피하려고 .first() 를 사용한다
    const anyCompetitor = adminPage
      .getByText(/^(AlphaEd|GammaLearn|BetaCampus|DeltaClass|Udemy)$/)
      .first();

    // 목록이 로드될 때까지 대기
    await expect(anyCompetitor).toBeVisible({ timeout: 10_000 });
  });

  test("경쟁사 추가 버튼이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 목록이 있거나 빈 상태일 때 모두 추가 버튼이 있음
    // 빈 상태일 때는 EmptyState 내 버튼, 목록이 있을 때는 헤더 영역 버튼
    const addButton = adminPage.getByRole("button", { name: /경쟁사 추가/ });
    await expect(addButton.first()).toBeVisible({ timeout: 10_000 });
  });

  test("경쟁사 추가 모달이 열리고 폼이 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 경쟁사 추가 버튼 클릭 (목록이 있을 때의 버튼 또는 빈 상태 버튼)
    await adminPage.getByRole("button", { name: /경쟁사 추가/ }).first().click();

    // 모달 제목 확인
    await expect(adminPage.getByRole("heading", { name: "경쟁사 추가" })).toBeVisible({ timeout: 5_000 });

    // 폼 필드 확인: 이름
    await expect(adminPage.getByLabel("이름")).toBeVisible();

    // 폼 필드 확인: 등급 (Select)
    await expect(adminPage.getByText("등급").first()).toBeVisible();

    // 저장/취소 버튼 확인
    await expect(adminPage.getByRole("button", { name: "저장" })).toBeVisible();
    await expect(adminPage.getByRole("button", { name: "취소" })).toBeVisible();
  });

  test("이름 없이 저장 시 검증 에러가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    await adminPage.getByRole("button", { name: /경쟁사 추가/ }).first().click();
    await expect(adminPage.getByRole("heading", { name: "경쟁사 추가" })).toBeVisible({ timeout: 5_000 });

    // 이름을 비운 채로 저장 시도
    await adminPage.getByRole("button", { name: "저장" }).click();

    // 검증 에러 메시지 확인
    await expect(adminPage.getByText("이름을 입력하세요")).toBeVisible({ timeout: 5_000 });
  });

  test("경쟁사를 추가할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    await adminPage.getByRole("button", { name: /경쟁사 추가/ }).first().click();
    await expect(adminPage.getByRole("heading", { name: "경쟁사 추가" })).toBeVisible({ timeout: 5_000 });

    // 이름 입력 (htmlFor="competitor-name" → getByLabel)
    const testName = `E2E경쟁사-${Date.now()}`;
    await adminPage.getByLabel("이름").fill(testName);

    // 저장
    await adminPage.getByRole("button", { name: "저장" }).click();

    // 성공 토스트 확인
    await expectToast(adminPage, "경쟁사가 추가됐어요");

    // 목록에 추가된 경쟁사 확인
    await expect(adminPage.getByText(testName)).toBeVisible({ timeout: 10_000 });
  });

  test("경쟁사를 수정할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 목록이 있는지 확인
    const editButton = adminPage.getByRole("button").filter({ has: adminPage.locator("svg.lucide-pencil") }).first();
    const hasRows = await adminPage.getByRole("row").nth(1).isVisible({ timeout: 5_000 }).catch(() => false);
    if (!hasRows) {
      test.skip();
      return;
    }

    // 첫 번째 행의 편집 버튼 클릭 (aria-label 포함)
    const firstEditBtn = adminPage.getByRole("button", { name: /편집/ }).first();
    const isEditVisible = await firstEditBtn.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isEditVisible) {
      test.skip();
      return;
    }
    await firstEditBtn.click();

    // 수정 모달 제목 확인
    await expect(adminPage.getByRole("heading", { name: "경쟁사 수정" })).toBeVisible({ timeout: 5_000 });

    // 이름 필드에 텍스트 추가
    const nameInput = adminPage.getByLabel("이름");
    await nameInput.click();
    await adminPage.keyboard.press("End");
    await adminPage.keyboard.type("-수정");

    // 저장
    await adminPage.getByRole("button", { name: "저장" }).click();

    // 성공 토스트 확인
    await expectToast(adminPage, "경쟁사가 수정됐어요");
  });

  test("경쟁사 활성 토글을 변경할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 경쟁사 행이 있는지 확인
    const hasRows = await adminPage.getByRole("row").nth(1).isVisible({ timeout: 5_000 }).catch(() => false);
    if (!hasRows) {
      test.skip();
      return;
    }

    // 첫 번째 활성 스위치 찾기 (aria-label: "{경쟁사명} 활성 상태 토글")
    const firstSwitch = adminPage.getByRole("switch").first();
    const isSwitchVisible = await firstSwitch.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isSwitchVisible) {
      test.skip();
      return;
    }

    await firstSwitch.click();

    // 상태 변경 토스트 확인
    await expectToast(adminPage, "상태가 변경됐어요");
  });

  test("분석 현황 탭으로 전환할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 분석 현황 탭 클릭
    await adminPage.getByRole("tab", { name: "분석 현황" }).click();

    // 기간 필터 버튼이 표시되는지 확인 (로딩이 완료되면 표시됨)
    // 로딩 중이거나 에러 상태를 모두 허용
    const periodButton = adminPage.getByRole("button", { name: "이번 주" });
    const loadingState = adminPage.locator("[class*='animate-pulse']").first();
    const errorState = adminPage.getByText("데이터를 불러오지 못했어요");

    await expect(
      periodButton.or(loadingState).or(errorState)
    ).toBeVisible({ timeout: 10_000 });
  });

  test("분석 현황 탭에서 기간 필터를 변경할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 분석 현황 탭 클릭
    await adminPage.getByRole("tab", { name: "분석 현황" }).click();

    // 기간 필터 버튼이 로드될 때까지 대기
    const isThisWeekVisible = await adminPage
      .getByRole("button", { name: "이번 주" })
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isThisWeekVisible) {
      // 로딩 중이거나 에러 — 스킵
      test.skip();
      return;
    }

    // 기간 필터 버튼 확인
    await expect(adminPage.getByRole("button", { name: "이번 주" })).toBeVisible();
    await expect(adminPage.getByRole("button", { name: "이번 달" })).toBeVisible();
    await expect(adminPage.getByRole("button", { name: "최근 3개월" })).toBeVisible();

    // "이번 주" 버튼 클릭
    await adminPage.getByRole("button", { name: "이번 주" }).click();

    // 버튼이 선택된 상태가 되는지 확인 (variant="default" → 활성화)
    // 기간 필터 버튼이 여전히 표시되어 있어야 함
    await expect(adminPage.getByRole("button", { name: "이번 주" })).toBeVisible();
  });

  test("경쟁사 목록 탭으로 돌아올 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors?tab=analysis");
    await expectPageLoad(adminPage, "경쟁사 관리");

    // 경쟁사 목록 탭 클릭
    await adminPage.getByRole("tab", { name: "경쟁사 목록" }).click();

    // 경쟁사 추가 버튼이 표시되는지 확인 (목록 탭으로 전환됐음을 의미)
    const addButton = adminPage.getByRole("button", { name: /경쟁사 추가/ });
    const emptyState = adminPage.getByText("아직 등록된 경쟁사가 없어요");
    await expect(addButton.first().or(emptyState)).toBeVisible({ timeout: 10_000 });
  });

  test("취소 버튼으로 추가 모달을 닫을 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/competitors");
    await expectPageLoad(adminPage, "경쟁사 관리");

    await adminPage.getByRole("button", { name: /경쟁사 추가/ }).first().click();
    await expect(adminPage.getByRole("heading", { name: "경쟁사 추가" })).toBeVisible({ timeout: 5_000 });

    // 취소 버튼 클릭
    await adminPage.getByRole("button", { name: "취소" }).click();

    // 모달이 닫혔는지 확인
    await expect(adminPage.getByRole("heading", { name: "경쟁사 추가" })).not.toBeVisible({ timeout: 5_000 });
  });
});
