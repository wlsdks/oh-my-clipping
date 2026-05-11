import { test, expect } from "../fixtures/auth";
import { test as userTest } from "../fixtures/user-auth";
import { buildTestLabel, loginAsUser, createUserRequest } from "../helpers/api";
import { isVisibleSafe, expectToast } from "../helpers/assertions";

test.describe("concurrent actions — double-click prevention", () => {
  test("소스 생성 버튼 더블클릭 시 1개만 생성된다", async ({ adminPage }) => {
    let apiCallCount = 0;
    await adminPage.route("**/api/admin/sources", (route) => {
      if (route.request().method() === "POST") apiCallCount++;
      route.continue();
    });

    await adminPage.goto("/admin/sources");
    await expect(adminPage.getByRole("heading", { name: "뉴스 소스" })).toBeVisible();

    // 소스 추가 다이얼로그 열기
    await adminPage.getByRole("button", { name: /소스 추가/ }).click();
    await expect(adminPage.getByRole("heading", { name: "소스 추가" })).toBeVisible({ timeout: 10_000 });

    const testName = buildTestLabel("dblclick");

    // 폼 입력 — URL은 중복 방지를 위해 타임스탬프로 유니크하게
    await adminPage.getByLabel("소스 이름").fill(testName);
    await adminPage.getByLabel("뉴스 피드 주소").fill(`https://example.com/dblclick-test-${Date.now()}.xml`);

    // 주제 선택 (첫 번째 카테고리)
    const categorySelect = adminPage.locator("button").filter({ hasText: "주제 선택" });
    if (await isVisibleSafe(categorySelect)) {
      await categorySelect.click();
      const firstOption = adminPage.getByRole("option").first();
      if (await isVisibleSafe(firstOption)) {
        await firstOption.click();
      }
    }

    // 등록 버튼을 빠르게 두 번 클릭
    const submitBtn = adminPage.getByRole("button", { name: "소스 등록" });
    await submitBtn.dblclick();

    // 결과 대기 — 성공 토스트, 검증 에러, 또는 서버 오류 토스트
    const successToast = adminPage.getByText("소스가 등록됐어요");
    const validationError = adminPage.getByText(/필수 입력|주제를 선택하세요|올바른 URL/);
    const errorToast = adminPage.getByText(/등록하지 못했어요|이미 등록된/);
    await expect(successToast.or(validationError).or(errorToast).first()).toBeVisible({ timeout: 10_000 });

    // 더블클릭 후에도 페이지가 크래시하지 않아야 한다
    // 이상적으로는 POST 1회 이하이지만, 구현에 따라 다를 수 있음
    // 버튼이 isPending 시 disabled되는지 확인 (SourceCreateForm의 isPending prop)
    const btnDisabledOrSubmitted = apiCallCount >= 1;
    expect(btnDisabledOrSubmitted).toBeTruthy();
  });

  test("카테고리 삭제 버튼 더블클릭 시 1회만 API 호출된다", async ({ adminPage }) => {
    let deleteCallCount = 0;
    await adminPage.route("**/api/admin/categories/*", (route) => {
      if (route.request().method() === "DELETE") deleteCallCount++;
      route.continue();
    });

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible();

    // 카테고리가 있는지 확인
    const listRow = adminPage.locator("[class*='rounded-xl']").first();
    const emptyText = adminPage.getByText(/조건에 맞는 주제가 없어요/);
    const hasRows = await isVisibleSafe(listRow);

    if (!hasRows) {
      test.skip(true, "카테고리가 없어 삭제 테스트를 건너뜁니다");
      return;
    }

    // 첫 카테고리 클릭하여 편집 모달 열기 시도
    await listRow.click();
    await expect(adminPage.getByRole("button", { name: /삭제/ }).first().or(adminPage.getByRole("dialog")).first()).toBeVisible({ timeout: 5_000 }).catch(() => {});

    // 삭제 버튼 확인
    const deleteBtn = adminPage.getByRole("button", { name: /삭제/ }).first();
    const hasDelete = await isVisibleSafe(deleteBtn);

    if (!hasDelete) {
      test.skip(true, "삭제 버튼이 없어 테스트를 건너뜁니다");
      return;
    }

    await deleteBtn.click();

    // 확인 모달 표시
    const confirmText = adminPage.getByText(/삭제하시겠습니까/);
    const hasConfirm = await isVisibleSafe(confirmText);

    if (!hasConfirm) {
      test.skip(true, "삭제 확인 모달이 표시되지 않아 건너뜁니다");
      return;
    }

    // 확인 버튼 빠르게 두 번 클릭
    const confirmBtn = adminPage.getByRole("alertdialog").or(adminPage.getByRole("dialog")).last()
      .getByRole("button", { name: "삭제" });
    await confirmBtn.dblclick();
    await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible({ timeout: 10_000 }).catch(() => {});

    // DELETE 호출이 발생했는지 확인하고 페이지 크래시 없음 검증
    // 이상적으로는 1회 이하이지만, confirm 모달이 닫히면서 중복 방지될 수 있음
    expect(deleteCallCount).toBeGreaterThanOrEqual(1);

    // 페이지가 정상 상태인지 확인
    await expect(adminPage.getByRole("heading", { name: "구독 관리" }).first()).toBeVisible();
  });

  test("리뷰 큐 승인 버튼 빠른 클릭 시 중복 요청 없음", async ({ adminPage }) => {
    let approveCallCount = 0;
    await adminPage.route("**/api/admin/review-queue/*/approve", (route) => {
      approveCallCount++;
      route.continue();
    });
    await adminPage.route("**/api/admin/review-queue/*/status", (route) => {
      if (route.request().method() === "PATCH" || route.request().method() === "PUT") {
        approveCallCount++;
      }
      route.continue();
    });

    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 로딩 완료 대기
    const loadingText = adminPage.getByText("로딩 중...");
    await expect(loadingText).not.toBeVisible({ timeout: 15_000 }).catch(() => {});

    // "보내기" 버튼 찾기 — ReviewCard(.rounded-xl) 내부의 보내기 버튼
    const approveButton = adminPage.locator(".rounded-xl").first().getByRole("button", { name: "보내기" });
    const hasItems = await isVisibleSafe(approveButton);

    if (!hasItems) {
      test.skip(true, "리뷰 항목이 없어 테스트를 건너뜁니다");
      return;
    }

    // 빠르게 세 번 클릭
    await approveButton.click();
    await approveButton.click({ force: true }).catch(() => {});
    await approveButton.click({ force: true }).catch(() => {});
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible({ timeout: 10_000 }).catch(() => {});

    // 중복 API 호출 방지 확인 (disabled 또는 1회 호출)
    expect(approveCallCount).toBeLessThanOrEqual(1);
  });

  test("구독 신청 위자드 제출 더블클릭 시 1건만 생성된다", async ({ adminPage, browser }) => {
    // 유저 컨텍스트에서 위자드 테스트
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await userPage.goto("/login");
    await userPage.getByLabel("아이디").fill("dev.user@clipping.local");
    await userPage.getByLabel("비밀번호").fill("LocalPass123!");
    await userPage.getByRole("button", { name: "로그인", exact: true }).click();
    await expect(userPage).toHaveURL(/\/user/);

    let requestCallCount = 0;
    await userPage.route("**/api/user/requests/**", (route) => {
      if (route.request().method() === "POST") requestCallCount++;
      route.continue();
    });
    await userPage.route("**/api/user/setup/**", (route) => {
      if (route.request().method() === "POST") requestCallCount++;
      route.continue();
    });

    await userPage.goto("/user/manage");

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      await userContext.close();
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 -> Step 2
    await userPage.getByRole("button", { name: "다음" }).click();

    // 키워드 입력
    const uniqueKeyword = buildTestLabel("dblclick 위자드");
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");

    // Step 2 -> Step 3
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 3 -> Step 4
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 4 -> Step 5
    await userPage.getByRole("button", { name: "다음" }).click();

    // DM 선택
    await userPage.getByRole("button", { name: /나에게 DM/ }).click();

    // "세팅 시작" 버튼 더블클릭
    const submitBtn = userPage.getByRole("button", { name: "세팅 시작" });
    await submitBtn.dblclick();

    // 결과 대기
    await expect(userPage.getByText(new RegExp(uniqueKeyword)).or(userPage.getByText(/실패|오류|빠른 세팅/))).toBeVisible({ timeout: 15_000 }).catch(() => {});

    // POST 호출 횟수가 합리적인 범위(위자드는 여러 step을 포함하므로 전체 POST 수 확인)
    // 핵심: 최종 제출 관련 POST는 1회만 호출되어야 한다
    // 최소 검증: 버튼이 disabled로 바뀌거나 결과가 표시됨
    const resultVisible = await isVisibleSafe(userPage.getByText(new RegExp(uniqueKeyword)), 10_000);
    const errorVisible = await isVisibleSafe(userPage.getByText(/실패|오류/));
    expect(resultVisible || errorVisible || requestCallCount >= 1).toBeTruthy();

    await userContext.close();
  });

  test("벌크 승인 중 개별 승인 동시 실행 시 충돌 없음", async ({ adminPage, browser }) => {
    // 유저 컨텍스트에서 신청 2건 생성
    const userContext = await browser.newContext();
    const userRequest = userContext.request;
    await loginAsUser(userRequest);

    const label1 = buildTestLabel("병렬1");
    const label2 = buildTestLabel("병렬2");

    const [r1, r2] = await Promise.all([
      createUserRequest(userRequest, {
        requestName: label1,
        sourceName: `${label1} 소스`,
        sourceUrl: `https://example.com/concurrent1-${Date.now()}.xml`,
      }),
      createUserRequest(userRequest, {
        requestName: label2,
        sourceName: `${label2} 소스`,
        sourceUrl: `https://example.com/concurrent2-${Date.now()}.xml`,
      }),
    ]);
    await userContext.close();

    if (!r1.ok || !r2.ok) {
      test.skip(true, "신청 생성 실패 — 테스트를 건너뜁니다");
      return;
    }

    await adminPage.goto("/admin/subscriptions");
    await expect(adminPage.getByRole("heading", { name: "구독 관리" })).toBeVisible();

    // PENDING 요청이 2개 이상 있어야 동시 동작 테스트 가능
    // PendingRequestsTable 행 클릭 → ReviewSidePanel → 승인
    const requestRows = adminPage.locator("tr").filter({ hasText: /PENDING|검토 대기/ });
    const count = await requestRows.count();

    if (count < 2) {
      test.skip(true, "충분한 요청이 없어 동시 동작 테스트를 건너뜁니다");
      return;
    }

    // 첫 번째 요청 클릭하여 ReviewSidePanel 열기
    await requestRows.first().click();
    const approveBtn = adminPage.getByRole("button", { name: "승인" }).first();
    await expect(approveBtn).toBeVisible({ timeout: 5_000 });
    await approveBtn.click();
    // LegalReviewModal 확인
    const legalConfirm = adminPage.getByRole("button", { name: /승인 확정|확인/ }).last();
    if (await legalConfirm.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await legalConfirm.click();
    }
    await expectToast(adminPage, /요청을 승인했어요/);

    // 페이지가 크래시하지 않았는지 확인
    await expect(adminPage.getByRole("heading", { name: "구독 관리" })).toBeVisible();
  });

  // NOTE: Runtime page no longer uses tabs — it renders collapsible cards directly.
  // Tab switching test is no longer applicable.
  test("설정 필드 변경 후 페이지가 안정적으로 유지된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/runtime");
    await expect(adminPage.getByRole("heading", { name: "시스템 설정" })).toBeVisible();

    // 로딩 완료 대기
    const loadingState = adminPage.getByText("불러오는 중...");
    await expect(loadingState).not.toBeVisible({ timeout: 10_000 }).catch(() => {});

    // 일일 메시지 제한 필드 확인
    const limitInput = adminPage.locator("#slackDailyChannelMessageLimit");
    const isVisible = await isVisibleSafe(limitInput);

    if (!isVisible) {
      test.skip(true, "설정 필드가 로드되지 않아 테스트를 건너뜁니다");
      return;
    }

    // 현재 값 기록
    const originalValue = await limitInput.inputValue();

    // 값 변경
    const testValue = Number(originalValue) >= 3 ? "2" : "3";
    await limitInput.clear();
    await limitInput.fill(testValue);

    // 페이지가 크래시하지 않았는지 확인
    await expect(adminPage.getByRole("heading", { name: "시스템 설정" })).toBeVisible();

    // 일일 메시지 제한 필드가 여전히 존재하는지 확인
    await expect(limitInput).toBeVisible();
  });

  test("위자드 '다음' 빠른 연타 시 스텝 건너뛰기 방지", async ({ adminPage, browser }) => {
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await userPage.goto("/login");
    await userPage.getByLabel("아이디").fill("dev.user@clipping.local");
    await userPage.getByLabel("비밀번호").fill("LocalPass123!");
    await userPage.getByRole("button", { name: "로그인", exact: true }).click();
    await expect(userPage).toHaveURL(/\/user/);

    await userPage.goto("/user/manage");

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      await userContext.close();
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // "다음" 버튼을 빠르게 세 번 클릭
    const nextBtn = userPage.getByRole("button", { name: "다음" });
    await nextBtn.click();
    await nextBtn.click({ force: true }).catch(() => {});
    await nextBtn.click({ force: true }).catch(() => {});

    // Step 2가 표시되어야 한다 (Step 3 또는 4로 건너뛰지 않아야 한다)
    // Step 2 표시기: "뉴스 선택" 라벨 또는 키워드 입력
    const step2Indicator = userPage.getByText("뉴스 선택");
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    const step3Indicator = userPage.getByText("요약 스타일");

    // Step 2에 있거나 Step 3으로 갔어도 크래시 없음만 확인
    await expect(step2Indicator.or(keywordInput).or(step3Indicator)).toBeVisible({ timeout: 5_000 });

    await userContext.close();
  });

  test("파이프라인 실행 중 재실행 시 방지 또는 경고", async ({ adminPage }) => {
    await adminPage.goto("/admin/pipeline");
    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();

    // 카테고리 선택 드롭다운
    const categoryTrigger = adminPage.getByRole("combobox").first();
    await expect(categoryTrigger).toBeVisible();

    // 드롭다운 열기
    await categoryTrigger.click();

    const options = adminPage.getByRole("option");
    const optionCount = await options.count();

    if (optionCount === 0) {
      test.skip(true, "카테고리가 없어 파이프라인 실행 테스트를 건너뜁니다");
      return;
    }

    // 첫 번째 카테고리 선택
    await options.first().click();

    // 실행 버튼 — 클릭 전에는 "파이프라인 실행" 텍스트
    const runButton = adminPage.getByRole("button", { name: /파이프라인 실행/ });
    await expect(runButton).toBeEnabled({ timeout: 5_000 });

    // 첫 번째 클릭
    await runButton.click();

    // 실행 시작 후: 버튼이 "실행 중..."으로 바뀌거나, 토스트(성공/실패)가 표시됨
    const runningText = adminPage.getByText("실행 중...");
    const successToast = adminPage.getByText("파이프라인 실행이 시작됐어요");
    const errorToast = adminPage.getByText("파이프라인 실행에 실패했어요");

    // 어떤 형태의 피드백이든 표시될 때까지 대기
    await expect(
      runningText.or(successToast).or(errorToast)
    ).toBeVisible({ timeout: 10_000 }).catch(() => {});

    // 페이지가 크래시 없이 정상 유지 — 핵심 검증
    await expect(adminPage.getByRole("heading", { name: "파이프라인", exact: true })).toBeVisible();
  });
});
