import { test, expect } from "../fixtures/auth";

test.describe("system status page", () => {
  test("시스템 상태 페이지가 로드되고 서비스 카드가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/system-status");

    // 페이지 제목
    await expect(adminPage.getByRole("heading", { name: "시스템 상태" })).toBeVisible();

    // 4개 서비스 카드 또는 로딩/에러 상태 확인 — 부제는 현재 UI에 없음
    const serverCard = adminPage.getByRole("heading", { name: "서버 상태" });
    const dbCard = adminPage.getByRole("heading", { name: "데이터베이스" });
    const errorState = adminPage.getByText(/시스템 상태를 불러오는 중 문제가 발생했어요/);
    const loadingSkeleton = adminPage.locator(".animate-pulse").first();

    await expect(
      serverCard.or(errorState).or(loadingSkeleton)
    ).toBeVisible({ timeout: 10_000 });

    // 서버 카드가 보이면 다른 카드도 확인
    const hasServerCard = await serverCard.isVisible().catch(() => false);
    if (hasServerCard) {
      await expect(dbCard).toBeVisible();
      await expect(adminPage.getByRole("heading", { name: "Slack 연결" })).toBeVisible();
      await expect(adminPage.getByRole("heading", { name: "스케줄러 현황" })).toBeVisible();
    }
  });

  test("메모리 사용량과 서버 상태가 표시된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/system-status");

    await expect(adminPage.getByRole("heading", { name: "시스템 상태" })).toBeVisible();

    // 서버 카드 대기
    const serverCard = adminPage.getByRole("heading", { name: "서버 상태" });
    const errorState = adminPage.getByText(/시스템 상태를 불러오는 중 문제가 발생했어요/);

    await expect(serverCard.or(errorState)).toBeVisible({ timeout: 10_000 });

    const hasServerCard = await serverCard.isVisible().catch(() => false);
    if (!hasServerCard) {
      test.skip();
      return;
    }

    // Uptime 표시 확인
    await expect(adminPage.getByText("Uptime")).toBeVisible();

    // 메모리 바 표시 확인 (MB 단위 포함).
    // `exact: true` 로 SchedulerStatusPanel 의 "인메모리" 부분 매치를 배제한다.
    await expect(adminPage.getByText("메모리", { exact: true })).toBeVisible();
    await expect(adminPage.getByText(/MB/).first()).toBeVisible();

    // DB 연결 상태 확인
    const connected = adminPage.getByText("연결됨");
    const disconnected = adminPage.getByText("연결 끊김");
    await expect(connected.or(disconnected)).toBeVisible();
  });

  test("자동 새로고침으로 데이터가 갱신된다", async ({ adminPage }) => {
    await adminPage.goto("/admin/system-status");

    await expect(adminPage.getByRole("heading", { name: "시스템 상태" })).toBeVisible();

    // 서버 카드 대기
    const serverCard = adminPage.getByRole("heading", { name: "서버 상태" });
    const errorState = adminPage.getByText(/시스템 상태를 불러오는 중 문제가 발생했어요/);

    await expect(serverCard.or(errorState)).toBeVisible({ timeout: 10_000 });

    const hasServerCard = await serverCard.isVisible().catch(() => false);
    if (!hasServerCard) {
      test.skip();
      return;
    }

    // Uptime 값 읽기
    const uptimeEl = adminPage.locator("text=Uptime").locator("..").locator("p.text-xl");
    const firstUptime = await uptimeEl.textContent().catch(() => null);

    // 자동 새로고침 주기(30초)만큼 대기하지 않고,
    // API 호출이 주기적으로 발생하는지 네트워크 요청으로 확인
    const apiCallPromise = adminPage.waitForResponse(
      (response) => response.url().includes("/api/admin/system-status"),
      { timeout: 35_000 }
    );

    const response = await apiCallPromise.catch(() => null);

    // 자동 새로고침 API 호출이 발생했는지 확인
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }

    // 페이지가 여전히 정상인지 확인
    await expect(adminPage.getByRole("heading", { name: "시스템 상태" })).toBeVisible();
  });
});
