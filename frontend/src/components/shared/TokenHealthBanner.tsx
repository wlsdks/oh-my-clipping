import { AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { tokenHealthService, type TokenHealthStatus } from "@/services/tokenHealthService";
import { useAuthStore } from "@/store/authStore";

const tokenHealthKeys = {
  status: ["token-health", "status"] as const,
};

/**
 * 토큰 헬스 상태별 사용자 안내 문구.
 *
 * 백엔드 wire 값은 코드 용어(`expired`, `quota_exhausted` 등)이므로 반드시
 * 한국어 운영 용어로 변환해서 노출한다 (AGENTS.md 8.3 에러 메시지 한국어화 규칙).
 */
const SLACK_MESSAGES: Record<string, string> = {
  expired: "Slack Bot 토큰이 만료되었거나 유효하지 않습니다. 관리자 설정에서 토큰을 다시 발급해 주세요.",
  scope_mismatch: "Slack 앱 권한이 부족합니다. Slack 앱 설정에서 chat:write 등 필수 스코프를 추가해 주세요.",
};

const GEMINI_MESSAGES: Record<string, string> = {
  expired: "Gemini API 키가 만료되었거나 유효하지 않습니다. 환경변수를 교체해 주세요.",
  quota_exhausted: "Gemini API 일일 쿼터가 소진되었습니다. 쿼터 증액 또는 다음 윈도우까지 대기해 주세요.",
};

function describeIssues(status: TokenHealthStatus): string[] {
  const issues: string[] = [];
  if (status.slackBot && status.slackBot !== "ok") {
    issues.push(SLACK_MESSAGES[status.slackBot] ?? `Slack Bot 상태를 확인할 수 없습니다 (code=${status.slackBot})`);
  }
  if (status.gemini && status.gemini !== "ok") {
    issues.push(GEMINI_MESSAGES[status.gemini] ?? `Gemini API 상태를 확인할 수 없습니다 (code=${status.gemini})`);
  }
  return issues;
}

/**
 * 토큰(Slack Bot / Gemini) 장애 배너 (F8).
 *
 * - 로그인 상태에서만 폴링하고, 상태가 모두 `ok`이면 렌더링하지 않는다.
 * - `refetchInterval: 60초` — 복구 시 최대 1분 내 배너가 내려간다.
 * - 관리자 전용 엔드포인트이므로 비관리자에게는 404/403이 내려와 에러 toast를 띄우지 않도록 `retry: false`.
 */
export function TokenHealthBanner() {
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn);

  const { data } = useQuery({
    queryKey: tokenHealthKeys.status,
    queryFn: () => tokenHealthService.getStatus(),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    staleTime: 30_000,
    enabled: isLoggedIn,
    retry: false,
  });

  if (!data || data.ok) return null;

  const issues = describeIssues(data);
  if (issues.length === 0) return null;

  return (
    <div
      role="alert"
      className="flex items-start gap-3 bg-[var(--status-danger-bg)] text-[var(--status-danger-text)] text-sm py-2.5 px-4 shrink-0 border-b border-border"
    >
      <AlertTriangle size={18} className="shrink-0 mt-0.5" aria-hidden="true" />
      <div className="flex flex-col gap-0.5">
        <span className="font-semibold">토큰 상태를 점검해 주세요</span>
        {issues.map((msg) => (
          <span key={msg} className="leading-snug">
            · {msg}
          </span>
        ))}
      </div>
    </div>
  );
}
