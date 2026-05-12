import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen, waitFor, cleanup } from "@testing-library/react";

import { TokenHealthBanner } from "@/components/shared/TokenHealthBanner";
import { authStore } from "@/store/authStore";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";

/**
 * tokenHealthService 는 네트워크를 타므로 항상 mock 한다. 각 테스트에서
 * `getStatus` 반환값을 바꿔 배너 렌더링 분기를 검증한다.
 */
vi.mock("@/services/tokenHealthService", () => ({
  tokenHealthService: {
    getStatus: vi.fn()
  }
}));

import { tokenHealthService } from "@/services/tokenHealthService";

type TokenHealthStatus = {
  slackBot: string;
  gemini: string;
  ok: boolean;
};

const mockGetStatus = vi.mocked(tokenHealthService.getStatus);

function renderBanner() {
  const qc = createTestQueryClient();
  return {
    ...render(<TokenHealthBanner />, { wrapper: createQueryClientWrapper(qc) }),
    qc
  };
}

function mockStatus(status: TokenHealthStatus) {
  mockGetStatus.mockResolvedValue(status);
}

async function advancePollingClock(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("TokenHealthBanner", () => {
  beforeEach(() => {
    // 로그인 상태에서만 폴링이 활성화되므로 매 테스트에서 로그인 시킨다.
    authStore.setState({
      isLoggedIn: true,
      user: { id: "u-1", email: "admin@test.local", name: "관리자", role: "ADMIN" } as never
    });
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
    authStore.setState({ isLoggedIn: false, user: null });
    vi.useRealTimers();
  });

  describe("정상 상태 (ok: true)", () => {
    it("모든 토큰이 ok이면 아무 것도 렌더링하지 않는다", async () => {
      mockStatus({ slackBot: "ok", gemini: "ok", ok: true });

      const { container } = renderBanner();

      // React Query 가 해결될 때까지 대기한 뒤에도 DOM 이 비어있어야 한다.
      await waitFor(() => {
        expect(mockGetStatus).toHaveBeenCalled();
      });
      expect(container).toBeEmptyDOMElement();
      // role=alert 인 요소가 하나도 없어야 한다.
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("로그인하지 않은 상태에서는 쿼리를 실행하지 않고 아무것도 렌더링하지 않는다", async () => {
      authStore.setState({ isLoggedIn: false, user: null });
      mockStatus({ slackBot: "expired", gemini: "ok", ok: false });

      const { container } = renderBanner();

      // 짧게 대기하지만 enabled=false 이므로 호출되면 안 된다.
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(mockGetStatus).not.toHaveBeenCalled();
      expect(container).toBeEmptyDOMElement();
    });
  });

  describe("Slack 장애 시 한국어 문구", () => {
    it("EXPIRED 상태에서 한국어 안내와 role=alert 를 렌더링한다", async () => {
      mockStatus({ slackBot: "expired", gemini: "ok", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent("토큰 상태를 점검해 주세요");
      expect(alert).toHaveTextContent(/Slack Bot 토큰이 만료되었거나 유효하지 않습니다/);
      expect(alert).toHaveTextContent(/관리자 설정에서 토큰을 다시 발급해 주세요/);
    });

    it("SCOPE_MISMATCH 상태에서 chat:write 권한 안내를 노출한다", async () => {
      mockStatus({ slackBot: "scope_mismatch", gemini: "ok", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Slack 앱 권한이 부족합니다/);
      expect(alert).toHaveTextContent(/chat:write/);
    });

    it("알 수 없는 Slack 코드는 code= 형식의 폴백 문구를 노출한다", async () => {
      mockStatus({ slackBot: "mystery_error", gemini: "ok", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Slack Bot 상태를 확인할 수 없습니다/);
      expect(alert).toHaveTextContent(/code=mystery_error/);
    });
  });

  describe("Gemini 장애 시 한국어 문구", () => {
    it("EXPIRED 상태에서 API 키 교체 안내를 노출한다", async () => {
      mockStatus({ slackBot: "ok", gemini: "expired", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Gemini API 키가 만료되었거나 유효하지 않습니다/);
      expect(alert).toHaveTextContent(/환경변수를 교체해 주세요/);
    });

    it("QUOTA_EXHAUSTED 상태에서 쿼터 소진 안내를 노출한다", async () => {
      mockStatus({ slackBot: "ok", gemini: "quota_exhausted", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Gemini API 일일 쿼터가 소진되었습니다/);
      expect(alert).toHaveTextContent(/쿼터 증액/);
    });
  });

  describe("복수 장애 메시지 연결", () => {
    it("Slack 과 Gemini 가 동시에 장애면 두 메시지 모두 노출한다", async () => {
      mockStatus({ slackBot: "expired", gemini: "quota_exhausted", ok: false });

      renderBanner();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Slack Bot 토큰이 만료되었거나 유효하지 않습니다/);
      expect(alert).toHaveTextContent(/Gemini API 일일 쿼터가 소진되었습니다/);
      // 두 이슈가 모두 별도 라인(`·` 불릿 prefix)으로 표시된다.
      const bullets = alert.querySelectorAll("span.leading-snug");
      expect(bullets.length).toBe(2);
    });
  });

  describe("폴링 주기", () => {
    it("refetchInterval 이 60초로 설정되어 초기 호출 이후 주기적으로 재페치된다", async () => {
      // 실시간 대기 없이 폴링 주기를 검증하기 위해 fake timer 를 사용한다.
      // getStatus 를 동기적으로 resolve 되도록 stub 해서 Promise flush 비용을 최소화한다.
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockGetStatus.mockImplementation(() =>
        Promise.resolve({ slackBot: "ok", gemini: "ok", ok: true })
      );

      renderBanner();

      // 초기 페치가 완료될 때까지 타이머/마이크로태스크를 진행시킨다.
      await advancePollingClock(10);
      expect(mockGetStatus).toHaveBeenCalledTimes(1);

      // 59초 시점에는 두 번째 호출이 발생하지 않아야 한다.
      await advancePollingClock(59_000);
      expect(mockGetStatus).toHaveBeenCalledTimes(1);

      // 60초 경계(총 약 60.01초)에서 두 번째 호출이 발생해야 한다.
      await advancePollingClock(1_000);
      expect(mockGetStatus).toHaveBeenCalledTimes(2);

      // 한 번 더 주기가 돌면 세 번째 호출까지 쌓여야 한다.
      await advancePollingClock(60_000);
      expect(mockGetStatus).toHaveBeenCalledTimes(3);
    });
  });
});
