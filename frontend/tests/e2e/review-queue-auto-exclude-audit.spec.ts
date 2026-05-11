import { test, expect } from "./fixtures/auth";

/**
 * PR-3-lite Task 7 — 자동 제외 감사 페이지 E2E.
 *
 * 시드 데이터에 자동 제외 항목이 있다는 보장은 없으므로, 복구 버튼 흐름은 존재할 때만 실행한다.
 */
test.describe("Auto-Exclude Audit Page", () => {
  test("페이지 노출 + 기본 상태", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue/auto-exclude-audit");
    await expect(adminPage.getByRole("heading", { name: "자동 제외 감사" })).toBeVisible();

    // 요약 카드(지난 7일 자동 제외 N건) 문구 노출
    await expect(adminPage.getByText(/지난 7일 자동 제외/)).toBeVisible({ timeout: 10_000 });
  });

  test("기간 칩 변경 시 라벨이 갱신된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue/auto-exclude-audit");
    await expect(adminPage.getByRole("heading", { name: "자동 제외 감사" })).toBeVisible();

    // "30일" 칩 클릭 → 요약 카드 라벨이 "지난 30일 자동 제외" 로 바뀌어야 한다.
    await adminPage.getByRole("button", { name: "30일", exact: true }).click();
    await expect(adminPage.getByText(/지난 30일 자동 제외/)).toBeVisible({ timeout: 10_000 });
  });

  test("복구 버튼이 있으면 confirm → 성공 토스트", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue/auto-exclude-audit");
    await expect(adminPage.getByRole("heading", { name: "자동 제외 감사" })).toBeVisible();

    const restoreBtn = adminPage.getByRole("button", { name: /복구$/ }).first();
    if (await restoreBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await restoreBtn.click();
      // 확인 모달 내 "복구" 확정 버튼 클릭
      const confirmBtn = adminPage.getByRole("button", { name: /복구$/ }).last();
      await confirmBtn.click();
      // 성공 토스트 또는 REVIEW 복구 문구가 어딘가 노출되어야 한다.
      await expect(adminPage.getByText(/REVIEW.*복구|복구.*REVIEW|복구했어요/)).toBeVisible({ timeout: 5000 });
    } else {
      test.skip(true, "자동 제외된 시드 데이터가 없어서 복구 플로우는 생략");
    }
  });

  test("제목 클릭 시 드로어가 열리고 요약과 원문 링크가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue/auto-exclude-audit");
    await expect(adminPage.getByRole("heading", { name: "자동 제외 감사" })).toBeVisible();

    // 자동 제외 항목이 없으면 skip — 시드 보장 없음
    const firstTitle = adminPage.getByRole("button", { name: /상세 보기$/ }).first();
    if ((await firstTitle.count()) === 0) {
      test.skip(true, "자동 제외된 항목이 없어 skip");
      return;
    }

    await firstTitle.click();

    const drawer = adminPage.getByRole("dialog");
    await expect(drawer).toBeVisible();
    await expect(drawer.getByText("요약")).toBeVisible();

    // "원문 열기" 가 링크인지(링크면 target=_blank), disabled 버튼인지 둘 중 하나여야 한다.
    const openLink = drawer.getByRole("link", { name: /원문 열기/ });
    const openBtn = drawer.getByRole("button", { name: /원문 열기/ });
    if ((await openLink.count()) > 0) {
      await expect(openLink).toHaveAttribute("target", "_blank");
      await expect(openLink).toHaveAttribute("rel", /noopener/);
    } else {
      await expect(openBtn).toBeDisabled();
    }
  });

  test("드로어 내부의 'REVIEW 로 복구' 버튼으로도 REVIEW 로 복구할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue/auto-exclude-audit");
    await expect(adminPage.getByRole("heading", { name: "자동 제외 감사" })).toBeVisible();

    const firstTitle = adminPage.getByRole("button", { name: /상세 보기$/ }).first();
    if ((await firstTitle.count()) === 0) {
      test.skip(true, "자동 제외된 항목이 없어 skip");
      return;
    }
    await firstTitle.click();

    const drawer = adminPage.getByRole("dialog");
    await expect(drawer).toBeVisible();

    // 드로어 내부의 복구 버튼 클릭
    await drawer.getByRole("button", { name: /REVIEW 로 복구/ }).click();

    // ConfirmModal 확인 버튼 — 이름이 정확히 "복구"
    await adminPage.getByRole("button", { name: /^복구$/ }).click();

    // 성공 토스트 + drawer 자동 닫힘.
    // 페이지 헤더(`...필요 시 REVIEW 로 복구합니다`)와 drawer 버튼(`REVIEW 로 복구`)이
    // 같은 regex 에 매치돼 strict mode 위반하므로 Sonner 토스트 컨테이너로 한정한다.
    await expect(
      adminPage.locator("[data-sonner-toast]").getByText("REVIEW 로 복구했어요"),
    ).toBeVisible({ timeout: 5000 });
    await expect(drawer).not.toBeVisible();
  });
});
