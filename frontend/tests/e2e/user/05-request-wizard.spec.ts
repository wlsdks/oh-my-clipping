import { test, expect } from "../fixtures/user-auth";
import { buildTestLabel } from "../helpers/api";
import { isVisibleSafe } from "../helpers/assertions";

test.describe("request wizard", () => {
  test("위자드가 열리고 Step 1 (사이트 필터)이 표시된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    // 구독 한도 도달 시 "새 주제" 버튼이 disabled로 표시됨
    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      await expect(userPage.getByRole("button", { name: /구독 한도/ })).toBeDisabled();
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();

    // Step 1: "빠른 세팅" 타이틀과 사이트 필터 UI가 표시된다
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 heading 확인
    await expect(userPage.getByText("어디서 찾을까요?")).toBeVisible();

    // "다음" 버튼이 존재하는지 확인
    await expect(userPage.getByRole("button", { name: "다음" })).toBeVisible();
  });

  test("Step 1에서 Step 2 (키워드 입력)로 진행할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 → Step 2: "다음" 클릭
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 2: 키워드 입력 영역이 표시된다
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    await expect(keywordInput).toBeVisible({ timeout: 5_000 });

    // Step 2 heading 확인
    await expect(userPage.getByText("어떤 뉴스를 받고 싶으세요?")).toBeVisible();
  });

  test("Step 2에서 Step 3 (페르소나/요약 스타일 선택)로 진행할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 → Step 2
    await userPage.getByRole("button", { name: "다음" }).click();

    // 키워드 입력
    const uniqueKeyword = buildTestLabel("E2E 위자드");
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");

    // Step 2 → Step 3
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 3: 요약 스타일 heading 확인
    await expect(userPage.getByText("어떤 스타일로 요약할까요?")).toBeVisible({ timeout: 5_000 });
  });

  test("Step 4에서 Step 5 (Slack 채널 연결)로 진행할 수 있다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 → Step 2
    await userPage.getByRole("button", { name: "다음" }).click();

    // 키워드 입력
    const uniqueKeyword = buildTestLabel("E2E 슬랙");
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");

    // Step 2 → Step 3
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 3 → Step 4 (수신 옵션)
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 4 → Step 5 (Slack)
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 5: Slack 관련 옵션 확인 — "나에게 DM" 과 "Slack 채널" 버튼이 모두 표시된다
    await expect(userPage.getByRole("button", { name: /나에게 DM/ })).toBeVisible({ timeout: 5_000 });
    await expect(userPage.getByRole("button", { name: /Slack 채널/ })).toBeVisible();

    // Step 5 heading 확인
    await expect(userPage.getByText("뉴스를 어디로 받을까요?")).toBeVisible();
  });

  test("위자드를 완료하면 구독 신청이 생성된다", async ({ userPage }) => {
    await userPage.goto("/user/manage");
    await expect(userPage.getByRole("heading", { name: "내 구독 관리" })).toBeVisible({ timeout: 10_000 });

    const newTopicBtn = userPage.getByRole("button", { name: /\+ 새 주제|새 주제 추가|새 주제/ }).first();
    const canCreate = await isVisibleSafe(newTopicBtn);
    if (!canCreate) {
      test.skip(true, "구독 한도 도달 — 위자드 열기 불가");
      return;
    }

    const uniqueKeyword = buildTestLabel("E2E 완료 확인");

    await newTopicBtn.click();
    await expect(userPage.getByText("빠른 세팅")).toBeVisible({ timeout: 5_000 });

    // Step 1 → Step 2
    await userPage.getByRole("button", { name: "다음" }).click();

    // 키워드 입력
    const keywordInput = userPage.getByPlaceholder(/키워드를 입력하고 Enter/);
    await keywordInput.fill(uniqueKeyword);
    await keywordInput.press("Enter");

    // Step 2 → Step 3
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 3 → Step 4 (수신 옵션)
    await userPage.getByRole("button", { name: "다음" }).click();

    // Step 4 → Step 5 (Slack)
    await userPage.getByRole("button", { name: "다음" }).click();

    // DM 옵션 선택 (Step 5에서 "나에게 DM" 클릭하면 자동으로 확정됨)
    await userPage.getByRole("button", { name: /나에게 DM/ }).click();

    // 세팅 시작
    await userPage.getByRole("button", { name: "세팅 시작" }).click();

    // 위자드 완료 후 생성된 구독이 표시되는지 확인
    await expect(userPage.getByText(new RegExp(uniqueKeyword))).toBeVisible({ timeout: 15_000 });
  });
});
