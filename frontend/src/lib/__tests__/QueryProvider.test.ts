import { describe, it, expect } from "vitest";
import { HTTPError } from "ky";
import { queryClient } from "@/lib/QueryProvider";

/**
 * shouldThrowError는 모듈 내부 함수이므로 직접 import할 수 없다.
 * queryClient의 defaultOptions.queries.throwOnError에 바인딩되어 있으므로
 * 이를 통해 간접적으로 테스트한다.
 */
function getShouldThrowError(): (error: unknown) => boolean {
  const throwOnError = queryClient.getDefaultOptions().queries?.throwOnError;
  if (typeof throwOnError !== "function") {
    throw new Error("throwOnError가 함수여야 합니다");
  }
  // throwOnError는 (error, query) => boolean 시그니처이지만
  // shouldThrowError는 error만 사용하므로 두 번째 인자는 무시해도 된다
  return (error: unknown) => (throwOnError as (error: unknown) => boolean)(error);
}

function createHTTPError(status: number): HTTPError {
  const response = new Response(null, { status });
  const request = new Request("https://example.com/api/test");
  return new HTTPError(response, request, {} as never);
}

describe("shouldThrowError (QueryProvider)", () => {
  const shouldThrowError = getShouldThrowError();

  describe("DOMException AbortError", () => {
    it("AbortError는 throw하지 않아야 한다 (false)", () => {
      const abortError = new DOMException("The operation was aborted", "AbortError");
      expect(shouldThrowError(abortError)).toBe(false);
    });

    it("AbortError가 아닌 DOMException은 throw하지 않아야 한다 (false)", () => {
      const otherDomError = new DOMException("Not supported", "NotSupportedError");
      expect(shouldThrowError(otherDomError)).toBe(false);
    });
  });

  describe("HTTPError 상태 코드별 동작", () => {
    it("401 Unauthorized는 throw해야 한다 (true)", () => {
      expect(shouldThrowError(createHTTPError(401))).toBe(true);
    });

    it("403 Forbidden은 throw해야 한다 (true)", () => {
      expect(shouldThrowError(createHTTPError(403))).toBe(true);
    });

    it("404 Not Found는 throw해야 한다 (true)", () => {
      expect(shouldThrowError(createHTTPError(404))).toBe(true);
    });

    it("400 Bad Request는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(createHTTPError(400))).toBe(false);
    });

    it("500 Internal Server Error는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(createHTTPError(500))).toBe(false);
    });

    it("502 Bad Gateway는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(createHTTPError(502))).toBe(false);
    });

    it("422 Unprocessable Entity는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(createHTTPError(422))).toBe(false);
    });

    it("429 Too Many Requests는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(createHTTPError(429))).toBe(false);
    });
  });

  describe("기타 에러 타입", () => {
    it("일반 Error는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(new Error("Something went wrong"))).toBe(false);
    });

    it("TypeError는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(new TypeError("Cannot read property"))).toBe(false);
    });

    it("null은 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(null)).toBe(false);
    });

    it("undefined는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError(undefined)).toBe(false);
    });

    it("문자열 에러는 throw하지 않아야 한다 (false)", () => {
      expect(shouldThrowError("error string")).toBe(false);
    });
  });

  describe("queryClient 기본 옵션 검증", () => {
    it("queries의 throwOnError가 함수여야 한다", () => {
      expect(typeof queryClient.getDefaultOptions().queries?.throwOnError).toBe("function");
    });

    it("mutations의 throwOnError가 함수여야 한다", () => {
      expect(typeof queryClient.getDefaultOptions().mutations?.throwOnError).toBe("function");
    });

    it("queries의 staleTime이 30초(FREQUENT)여야 한다", () => {
      expect(queryClient.getDefaultOptions().queries?.staleTime).toBe(30_000);
    });

    it("queries의 gcTime이 10분이어야 한다", () => {
      expect(queryClient.getDefaultOptions().queries?.gcTime).toBe(1000 * 60 * 10);
    });

    it("queries의 retry가 1이어야 한다", () => {
      expect(queryClient.getDefaultOptions().queries?.retry).toBe(1);
    });

    it("mutations의 retry가 0이어야 한다", () => {
      expect(queryClient.getDefaultOptions().mutations?.retry).toBe(0);
    });

    it("queries의 refetchOnWindowFocus가 false여야 한다", () => {
      expect(queryClient.getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false);
    });
  });
});
