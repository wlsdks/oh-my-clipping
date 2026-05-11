import { test, expect } from "../fixtures/auth";

test.describe("sidebar badge tooltip", () => {
  test("배지가 존재하면 hover 시 툴팁 텍스트 노출", async ({ adminPage }) => {
    await adminPage.goto("/admin");
    await expect(adminPage.getByRole("heading", { name: /^(홈|안녕하세요)/ })).toBeVisible();

    const badges = adminPage.locator("[data-testid='sidebar-badge']");
    const badgeCount = await badges.count();

    if (badgeCount === 0) {
      test.skip(true, "시드/DB 상태에 사이드바 배지가 없어 툴팁 동작 확인 불가");
      return;
    }

    await badges.first().hover();

    // 툴팁 텍스트 5종 중 어느 하나라도 노출되면 PASS
    const tooltipPatterns = [
      /현재 승인 대기 \d+건/,
      /현재 판단 대기 뉴스 \d+건/,
      /현재 구독 요청 대기 \d+건/,
      /최근 24h 발송 실패 \d+건/,
      /최근 24h 파이프라인 실패 \d+건/,
    ];

    // 하나라도 뜨기를 기다림 (Radix Tooltip 이 portal 로 document.body 끝에 렌더됨)
    const anyTooltip = adminPage.getByText(
      new RegExp(tooltipPatterns.map((p) => p.source).join("|"))
    );
    await expect(anyTooltip.first()).toBeVisible({ timeout: 3_000 });
  });

  // PR #423 이후 모바일(lg 미만) 에서는 사이드바가 DOM 에는 있으나 `hidden lg:block`
  // 으로 화면에서 안 보이고, 대신 별도의 `<BottomNavigation>` 을 노출한다. BottomNavigation
  // 에는 sidebar-badge 가 없어 "모바일 배지 tap → 툴팁 비노출 + 링크 이동" 시나리오 자체가
  // 더 이상 적용되지 않는다. 테스트의 전제가 무효해진 상황이므로 skip 으로 명시한다.
  test.skip(
    "모바일(hover:none) 환경에서는 툴팁 비활성 — 탭이 네비게이션으로 동작",
    () => {
      /*
       * Obsolete as of PR #423: mobile UI uses BottomNavigation, not a
       * collapsible sidebar drawer. There are no sidebar-badge elements on
       * mobile viewport anymore. If a future redesign adds badges to
       * BottomNavigation, rewrite this around that component instead.
       */
    }
  );
});
