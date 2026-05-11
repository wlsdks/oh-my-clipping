import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ReactElement } from "react";
import { render, screen } from "@testing-library/react";

// `@/lib/kyInstance`의 `api.post(...).json()` 체인을 가로채기 위해 모듈 자체를 모킹한다.
// 각 테스트에서 spy 함수를 재설정할 수 있도록 노출한다.
const postSpy = vi.fn();
const jsonSpy = vi.fn();

vi.mock("@/lib/kyInstance", () => ({
  api: {
    post: (...args: unknown[]) => {
      postSpy(...args);
      return { json: jsonSpy };
    }
  }
}));

import { ErrorBoundary } from "@/components/shared/ErrorBoundary";

/**
 * 렌더 시점에 예외를 던지는 자식 컴포넌트.
 * React는 componentDidCatch 중 에러를 콘솔에 찍으므로 에러 로그는 예상된 것이다.
 */
function Bomb({ message }: { message: string }): ReactElement {
  throw new Error(message);
}

describe("ErrorBoundary", () => {
  beforeEach(() => {
    postSpy.mockReset();
    // 기본적으로 fetch 호출이 성공한 것으로 간주한다 (resolve undefined).
    jsonSpy.mockReset();
    jsonSpy.mockReturnValue({
      catch: () => Promise.resolve(undefined)
    });
    // console.error는 기대되는 출력이므로 소음을 줄인다.
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("자식이 렌더 중 에러를 던지면 폴백 UI를 보여준다", () => {
    render(
      <ErrorBoundary>
        <Bomb message="boom-fallback" />
      </ErrorBoundary>
    );

    expect(screen.getByText("문제가 발생했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
  });

  it("에러 발생 시 /api/client-errors로 POST 보고를 전송한다", () => {
    render(
      <ErrorBoundary>
        <Bomb message="report-test" />
      </ErrorBoundary>
    );

    expect(postSpy).toHaveBeenCalledTimes(1);
    const [url, options] = postSpy.mock.calls[0];
    expect(url).toBe("client-errors");
    const payload = (options as { json: Record<string, unknown> }).json;
    expect(payload.message).toBe("report-test");
    expect(typeof payload.stack).toBe("string");
  });

  it("Minified React 에러 코드가 있으면 reactErrorCode 필드로 추출한다", () => {
    render(
      <ErrorBoundary>
        <Bomb message="Minified React error #185; visit reactjs.org/..." />
      </ErrorBoundary>
    );

    const payload = (postSpy.mock.calls[0][1] as { json: Record<string, unknown> })
      .json;
    expect(payload.reactErrorCode).toBe("185");
  });

  it("같은 에러 메시지가 5초 이내 재발하면 한 번만 보고한다", () => {
    const duplicateMessage = `dup-${Date.now()}`;

    const { unmount: unmount1 } = render(
      <ErrorBoundary>
        <Bomb message={duplicateMessage} />
      </ErrorBoundary>
    );
    unmount1();

    render(
      <ErrorBoundary>
        <Bomb message={duplicateMessage} />
      </ErrorBoundary>
    );

    // 두 번째 호출은 쿨다운 윈도우 안이므로 스킵되어야 한다.
    expect(postSpy).toHaveBeenCalledTimes(1);
  });

  it("보고 API가 실패해도 사용자에게 노출되지 않는다", () => {
    // json().catch()가 호출되어도 예외가 전파되지 않아야 한다.
    jsonSpy.mockReturnValue({
      catch: (handler: (err: unknown) => void) => {
        handler(new Error("network down"));
        return Promise.resolve(undefined);
      }
    });

    expect(() =>
      render(
        <ErrorBoundary>
          <Bomb message={`silent-${Date.now()}`} />
        </ErrorBoundary>
      )
    ).not.toThrow();
    expect(screen.getByText("문제가 발생했어요")).toBeInTheDocument();
  });
});
