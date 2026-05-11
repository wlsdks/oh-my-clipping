import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/kyInstance";
import { reportBoundaryError } from "@/lib/sentry";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/**
 * 같은 에러 메시지에 대한 중복 보고를 눌러 주는 쿨다운 윈도우.
 * 하나의 페이지에서 같은 컴포넌트가 연속으로 터질 때 서버 로그가 폭주하지 않도록 한다.
 */
const DUPLICATE_REPORT_COOLDOWN_MS = 5_000;
const recentReportedAt = new Map<string, number>();

/**
 * React minified 에러 메시지에서 숫자 코드를 추출한다.
 * 예: "Minified React error #185;" → "185".
 */
function extractReactErrorCode(message: string): string | null {
  const minified = message.match(/Minified React error #(\d+)/);
  if (minified?.[1]) return minified[1];
  const generic = message.match(/error #(\d+)/);
  return generic?.[1] ?? null;
}

/**
 * 클라이언트 에러 보고 페이로드 형태.
 * 백엔드 `/api/client-errors` 엔드포인트의 `ClientErrorReport`와 필드를 맞춘다.
 */
interface ClientErrorReportPayload {
  message: string;
  stack?: string;
  componentStack?: string;
  url?: string;
  userAgent?: string;
  reactErrorCode?: string;
}

/**
 * 에러 보고를 fire-and-forget으로 서버에 전송한다.
 * 보고 자체가 실패해도 사용자 경험에 영향을 주지 않도록 조용히 삼킨다.
 */
function reportClientError(payload: ClientErrorReportPayload): void {
  void api
    .post("client-errors", { json: payload })
    .json()
    .catch(() => {
      // 보고 실패는 의도적으로 무시한다 — 이중 에러로 루프가 생기면 안 됨.
    });
}

/**
 * 앱 전역 에러 경계 — 하위 컴포넌트에서 렌더링 에러가 발생하면
 * 흰 화면 대신 사용자 친화적인 폴백 UI를 보여주고,
 * `/api/client-errors`로 진단 정보를 전송한다 (fire-and-forget).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // 개발 보조용 콘솔 출력은 유지한다.
    console.error("[ErrorBoundary] Uncaught error:", error, errorInfo);

    // 동일 메시지가 5초 이내 재발하면 서버 보고를 생략한다 — 로그 폭주 방지.
    const now = Date.now();
    const lastReportedAt = recentReportedAt.get(error.message) ?? 0;
    if (now - lastReportedAt < DUPLICATE_REPORT_COOLDOWN_MS) {
      return;
    }
    recentReportedAt.set(error.message, now);

    // 브라우저 환경에서만 페이지 컨텍스트를 수집한다.
    const url = typeof window !== "undefined" ? `${window.location.pathname}${window.location.search}` : undefined;
    const userAgent = typeof navigator !== "undefined" ? navigator.userAgent : undefined;
    const reactErrorCode = extractReactErrorCode(error.message) ?? undefined;

    reportClientError({
      message: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack ?? undefined,
      url,
      userAgent,
      reactErrorCode
    });

    reportBoundaryError(error, {
      componentStack: errorInfo.componentStack ?? undefined,
      url,
      reactErrorCode
    });
  }

  private handleReload = (): void => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background px-4 text-center">
          <h1 className="text-xl font-semibold text-foreground">문제가 발생했어요</h1>
          <p className="max-w-sm text-sm text-muted-foreground">
            일시적인 오류가 발생했어요. 페이지를 새로고침해 주세요.
          </p>
          <Button variant="outline" onClick={this.handleReload}>
            새로고침
          </Button>
        </div>
      );
    }

    return this.props.children;
  }
}
