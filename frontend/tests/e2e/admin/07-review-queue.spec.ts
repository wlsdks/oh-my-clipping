import { test, expect } from "../fixtures/auth";
import { expectToast, expectContentOrEmpty, isVisibleSafe } from "../helpers/assertions";

test.describe("review queue page", () => {
  test("리뷰 큐 페이지가 로드된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");

    // 페이지 제목 확인
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 리뷰 카드 목록 또는 빈 상태가 표시되는지 확인
    const reviewCards = adminPage.locator(".space-y-2 .rounded-xl").first();
    const emptyText = adminPage.getByText(/검토할 항목이 없어요/);
    await expectContentOrEmpty(reviewCards, emptyText);
  });

  test("필터 버튼이 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 상태 필터 pill 버튼 확인 — 카운트가 포함되어 "확인 필요 3" 등의 형태
    // "확인 필요 설명" 인포 버튼과 구분하기 위해 숫자 suffix 를 요구한다
    const reviewBtn = adminPage.getByRole("button", { name: /^확인 필요\s+\d+$/ });
    await expect(reviewBtn).toBeVisible();

    // "전체" 필터 클릭 — 버튼 텍스트는 "전체 <count>" 형태이므로 regex 사용
    const allBtn = adminPage.getByRole("button", { name: /^전체\s+\d+$/ });
    if (await isVisibleSafe(allBtn)) {
      await allBtn.click();
      await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();
    }

    // "확인 필요" 필터로 복귀
    await reviewBtn.click();
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();
  });

  test("기사를 승인하면 상태가 변경되고 토스트가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 리뷰 카드에서 "보내기" 버튼 찾기
    const approveButton = adminPage.locator(".rounded-xl").first().getByRole("button", { name: "보내기" });
    const hasItems = await isVisibleSafe(approveButton);

    if (!hasItems) {
      test.skip();
      return;
    }

    await approveButton.click();
    await expectToast(adminPage, /보내기로 처리했어요/);
  });

  test("기사를 제외하면 상태가 변경되고 토스트가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 리뷰 카드에서 "건너뛰기" 버튼 찾기
    const excludeButton = adminPage.locator(".rounded-xl").first().getByRole("button", { name: "건너뛰기" });
    const hasItems = await isVisibleSafe(excludeButton);

    if (!hasItems) {
      test.skip();
      return;
    }

    await excludeButton.click();
    await expectToast(adminPage, /건너뛰기로 처리했어요/);
  });

  test("카테고리 필터가 동작한다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 카테고리 드롭다운 (주제 전체) 이 보이는지 확인
    const categoryTrigger = adminPage.getByText("주제 전체");
    await expect(categoryTrigger).toBeVisible();

    // 드롭다운을 클릭하여 옵션이 열리는지 확인
    await categoryTrigger.click();

    // SelectContent가 열렸는지 확인 — "주제 전체" 옵션이 드롭다운 내부에 표시됨
    const dropdownContent = adminPage.locator("[data-radix-select-content]");
    const hasDropdown = await isVisibleSafe(dropdownContent);

    if (hasDropdown) {
      // 첫 번째 카테고리 옵션이 있으면 선택
      const firstOption = dropdownContent.locator("[data-radix-select-item]").nth(1);
      const hasOption = await isVisibleSafe(firstOption);

      if (hasOption) {
        await firstOption.click();
        // 페이지가 유효한지 확인
        await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();
      } else {
        // 카테고리가 없으면 드롭다운 닫기
        await adminPage.keyboard.press("Escape");
      }
    }
  });

  test("검색으로 기사를 필터링할 수 있다", async ({ adminPage }) => {
    await adminPage.goto("/admin/review-queue");
    await expect(adminPage.getByRole("heading", { name: "뉴스 검토" })).toBeVisible();

    // 검색 입력란 찾기
    const searchInput = adminPage.getByPlaceholder("검색 (초성 가능)");
    await expect(searchInput).toBeVisible();

    // 존재하지 않을 법한 검색어 입력
    await searchInput.fill("zzzzz_no_match_here");

    // 검색 결과 없음 빈 상태가 표시되어야 함
    await expect(adminPage.getByText(/검색 결과가 없어요/)).toBeVisible();

    // 검색어 지우기
    await searchInput.clear();

    // 원래 목록이나 빈 상태로 복귀
    const reviewCards = adminPage.locator(".space-y-2 .rounded-xl").first();
    const emptyText = adminPage.getByText(/검토할 항목이 없어요/);
    await expectContentOrEmpty(reviewCards, emptyText);
  });
});
