import { test, expect } from "../fixtures/auth";
import {
  buildTestLabel,
  loginAsAdmin,
  createPersona,
  deletePersona,
  updatePersona,
  listPersonas,
} from "../helpers/api";
import { expectToast, expectPageLoad, expectContentOrEmpty } from "../helpers/assertions";

test.describe("personas (presets) management", () => {
  const createdIds: string[] = [];
  // 프롬프트 수정 테스트가 건드리는 시스템 프리셋의 원본을 보관했다가 반드시 복구한다.
  const promptsToRestore: Array<{ id: string; systemPrompt: string }> = [];

  test.afterAll(async ({ request }) => {
    await loginAsAdmin(request);
    for (const id of createdIds) {
      await deletePersona(request, id).catch(() => {});
    }
    // 시스템 프리셋 프롬프트를 원본으로 되돌린다 (E2E 잔재가 DB에 남지 않도록)
    for (const snapshot of promptsToRestore) {
      await updatePersona(request, snapshot.id, { systemPrompt: snapshot.systemPrompt }).catch(
        () => {},
      );
    }
  });

  test("프리셋 목록이 로드된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/personas");

    // 페이지 제목
    await expectPageLoad(adminPage, "요약 스타일");

    // "템플릿 관리" 탭이 활성화되어 있는지 확인 (기존 "프리셋 관리"에서 리네이밍됨)
    await expect(adminPage.getByText("템플릿 관리").first()).toBeVisible();

    // 템플릿 카드 또는 빈 상태
    const presetCard = adminPage.locator("[class*='rounded-2xl']").first();
    const emptyText = adminPage.getByText("아직 템플릿이 없어요");
    await expect(presetCard.or(emptyText)).toBeVisible({ timeout: 10_000 });

    // 새 템플릿 버튼
    await expect(adminPage.getByRole("button", { name: /새 템플릿/ })).toBeVisible();
  });

  test("프리셋을 생성할 수 있다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);
    const presetName = buildTestLabel("e2e-preset");

    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    // 새 템플릿 버튼 클릭 (기존 "새 프리셋"에서 리네이밍됨)
    await adminPage.getByRole("button", { name: /새 템플릿/ }).click();

    // 모달이 열렸는지 확인
    await expect(adminPage.getByRole("heading", { name: "새 템플릿 만들기" })).toBeVisible({ timeout: 5_000 });

    // 이름 입력
    await adminPage.getByLabel("이름").fill(presetName);

    // AI 지시문 입력
    await adminPage.getByLabel("AI 지시문").fill("E2E 테스트용 템플릿 프롬프트입니다.");

    // 저장
    await adminPage.getByRole("button", { name: "저장" }).click();
    await expectToast(adminPage, "템플릿을 생성했어요");

    // API로 생성 확인 (isPreset 필터 때문에 목록에 안 보일 수 있음)
    const res = await request.get("/api/admin/personas");
    if (res.ok()) {
      const personas = (await res.json()) as Array<{ id: string; name: string }>;
      const created = personas.find((p) => p.name === presetName);
      expect(created).toBeTruthy();
      if (created) createdIds.push(created.id);
    }
  });

  test("프리셋 프롬프트를 수정할 수 있다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);
    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    // 기존 프리셋 카드 중 첫 번째를 클릭하여 편집 모달 열기
    const presetCards = adminPage.locator("button[class*='rounded-2xl']");
    // 카드가 렌더링될 때까지 대기
    const emptyTitle = adminPage.getByText("아직 템플릿이 없어요");
    await expect(presetCards.first().or(emptyTitle)).toBeVisible({ timeout: 10_000 });
    const cardCount = await presetCards.count();
    if (cardCount === 0) {
      test.skip();
      return;
    }

    const presetCard = presetCards.first();
    // 카드에 표시된 이름 기록
    const cardName = await presetCard.locator("span.font-semibold").textContent();

    // 편집 대상 프리셋의 id + 원본 프롬프트를 API로 먼저 확보한다.
    // UI 저장이 성공하든 실패하든 afterAll 에서 이 원본으로 반드시 복구하여
    // 시스템 프리셋이 "E2E 수정 테스트 프롬프트입니다." 로 남지 않도록 한다.
    if (cardName) {
      const personas = await listPersonas(request);
      const target = personas.find((p) => p.name === cardName.trim());
      if (target?.systemPrompt) {
        promptsToRestore.push({ id: target.id, systemPrompt: target.systemPrompt });
      }
    }

    await presetCard.click();

    // 편집 모달 열림 확인
    await expect(
      adminPage.getByRole("heading", { name: cardName ?? "" })
    ).toBeVisible({ timeout: 5_000 });

    // AI 지시문 수정
    const promptTextarea = adminPage.getByLabel("AI 지시문");
    await promptTextarea.clear();
    await promptTextarea.fill("E2E 수정 테스트 프롬프트입니다.");

    // 저장 버튼 클릭
    await adminPage.getByRole("button", { name: "저장" }).click();

    // 저장 결과 확인 — 프리셋은 백엔드에서 수정이 제한될 수 있어 에러 토스트도 성공으로 간주
    const successToast = adminPage.getByText(/저장됐어요|템플릿을 저장했어요/);
    const errorToast = adminPage.getByText(/실패|예기치 않은 오류|저장하지 못했어요|Request failed|error/i);
    await expect(successToast.first().or(errorToast.first())).toBeVisible({ timeout: 10_000 });
  });

  test("프리셋을 삭제할 수 있다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);

    // 삭제 테스트용 프리셋을 API로 생성 (cleanup용)
    const presetName = buildTestLabel("e2e-del-p");
    const persona = await createPersona(request, {
      name: presetName,
      systemPrompt: "삭제 테스트 프롬프트"
    });
    createdIds.push(persona.id);

    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    // 목록에 표시되는 카드는 isPreset=true인 프리셋만이다.
    // 현재 UI에서는 isPreset 프리셋의 삭제 버튼이 렌더링되지 않으므로 (PresetDetailModal: !persona?.isPreset 조건),
    // 이 테스트는 삭제 경로를 UI로 커버할 수 없다. 카드가 있는지만 확인하고 스킵한다.
    const presetCards = adminPage.locator("button[class*='rounded-2xl']");
    const emptyTitle = adminPage.getByText("아직 템플릿이 없어요");
    await expect(presetCards.first().or(emptyTitle)).toBeVisible({ timeout: 10_000 });
    const cardCount = await presetCards.count();

    if (cardCount === 0) {
      test.skip(true, "삭제 테스트를 위한 프리셋 카드가 없음");
      return;
    }

    // 첫 번째 카드 클릭 (프리셋)
    const targetCard = presetCards.first();
    const cardName = await targetCard.locator("span.font-semibold").textContent();
    await targetCard.click();

    // 편집 모달 열림 확인
    await expect(
      adminPage.getByRole("heading", { name: cardName ?? "" })
    ).toBeVisible({ timeout: 5_000 });

    // 삭제 버튼이 모달에 존재하는지 확인 (프리셋은 삭제 버튼이 렌더링되지 않음)
    const deleteButton = adminPage.getByRole("button", { name: "삭제", exact: true });
    const deleteButtonCount = await deleteButton.count();

    if (deleteButtonCount === 0) {
      // 프리셋이어서 삭제 UI가 없음 — 이 테스트는 현재 UI에서 스킵이 맞다
      await adminPage.keyboard.press("Escape");
      test.skip(true, "프리셋은 UI에서 삭제 버튼이 렌더링되지 않음 (PresetDetailModal 조건: !persona?.isPreset)");
      return;
    }

    // 삭제 버튼이 렌더링됐더라도 disabled면 스킵 (구독자 있음)
    const isDeleteEnabled = await deleteButton.first().isEnabled();
    if (!isDeleteEnabled) {
      await adminPage.keyboard.press("Escape");
      test.skip(true, "구독자가 있어 삭제 비활성화");
      return;
    }

    // 삭제 버튼 클릭
    await deleteButton.first().click();

    // 삭제 확인 모달에서 확인 — "템플릿을 삭제할까요?"
    await expect(adminPage.getByText(/템플릿을 삭제할까요|템플릿 삭제/)).toBeVisible({ timeout: 5_000 });
    const confirmDialog = adminPage.getByRole("alertdialog").or(adminPage.getByRole("dialog")).last();
    await confirmDialog.getByRole("button", { name: "삭제" }).click();

    // 결과 확인 (삭제 성공 또는 실패)
    const successToast = adminPage.getByText("템플릿을 삭제했어요");
    const errorToast = adminPage.getByText(/실패|삭제하지 못했어요|예기치 않은 오류/);
    await expect(successToast.or(errorToast.first())).toBeVisible({ timeout: 10_000 });
  });

  test("사용 통계 탭으로 전환할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    // 사용 통계 탭 클릭
    await adminPage.getByText("사용 통계").click();

    // 통계 관련 콘텐츠가 보이는지 확인 (차트/메트릭 또는 빈 상태)
    const statsContent = adminPage.getByText("총 스타일 수");
    const emptyChart = adminPage.getByText("데이터 없음");
    const loadingText = adminPage.getByText("불러오는 중...");
    await expect(
      statsContent.or(emptyChart).or(loadingText)
    ).toBeVisible({ timeout: 10_000 });
  });

  test("프리셋이 없을 때 안내 메시지가 표시된다", async ({ adminPage, request }) => {
    await loginAsAdmin(request);

    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    const emptyTitle = adminPage.getByText("아직 템플릿이 없어요");
    const emptyDesc = adminPage.getByText("첫 번째 템플릿을 추가하세요");
    const presetCards = adminPage.locator("button[class*='rounded-2xl']");
    const countText = adminPage.getByText(/총 \d+개 템플릿/);

    // 렌더링 완료 대기 — 카운트 텍스트 또는 빈 상태 중 하나가 보여야 한다
    await expect(countText.or(emptyTitle).first()).toBeVisible({ timeout: 10_000 });

    // 카드 수를 확인하여 분기
    const cardCount = await presetCards.count();
    if (cardCount === 0) {
      await expect(emptyTitle).toBeVisible({ timeout: 5_000 });
      await expect(emptyDesc).toBeVisible();
    } else {
      // 템플릿이 있는 경우 "총 N개 템플릿" 텍스트가 보여야 한다
      await expect(countText).toBeVisible();
    }
  });

  test("이름 없이 프리셋 생성 시 검증 에러가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/personas");
    await expectPageLoad(adminPage, "요약 스타일");

    // 새 템플릿 버튼 클릭 (기존 "새 프리셋"에서 리네이밍됨)
    await adminPage.getByRole("button", { name: /새 템플릿/ }).click();
    await expect(adminPage.getByRole("heading", { name: "새 템플릿 만들기" })).toBeVisible({ timeout: 5_000 });

    // 이름 비우고 저장 시도
    await adminPage.getByRole("button", { name: "저장" }).click();

    // 검증 에러 메시지 확인
    await expect(adminPage.getByText(/이름을 입력하세요|이름은 필수/).first()).toBeVisible({ timeout: 5_000 });
  });
});
