import { describe, expect, it } from "vitest";
import { ApiError } from "../../api/httpClient";
import {
  isHttpStatus,
  isConflictError,
  isForbiddenError,
  isNotFoundError,
  userFriendlyMessage,
  translateRawMessage,
  extractStaleEditInfo,
} from "../httpError";

describe("isHttpStatus", () => {
  it("ApiError의 상태 코드가 일치하면 true", () => {
    const err = new ApiError(404, "not found");
    expect(isHttpStatus(err, 404)).toBe(true);
  });

  it("ApiError의 상태 코드가 다르면 false", () => {
    const err = new ApiError(500, "server error");
    expect(isHttpStatus(err, 404)).toBe(false);
  });

  it("일반 Error이면 false", () => {
    expect(isHttpStatus(new Error("fail"), 500)).toBe(false);
  });

  it("null이면 false", () => {
    expect(isHttpStatus(null, 500)).toBe(false);
  });
});

describe("isConflictError", () => {
  it("409 ApiError에 true", () => {
    expect(isConflictError(new ApiError(409, "conflict"))).toBe(true);
  });

  it("다른 상태 코드에 false", () => {
    expect(isConflictError(new ApiError(400, "bad"))).toBe(false);
  });
});

describe("isForbiddenError", () => {
  it("403 ApiError에 true", () => {
    expect(isForbiddenError(new ApiError(403, "forbidden"))).toBe(true);
  });

  it("401 ApiError에 false", () => {
    expect(isForbiddenError(new ApiError(401, "unauthorized"))).toBe(false);
  });

  it("일반 Error에 false", () => {
    expect(isForbiddenError(new Error("forbidden"))).toBe(false);
  });
});

describe("isNotFoundError", () => {
  it("404 ApiError에 true", () => {
    expect(isNotFoundError(new ApiError(404, "not found"))).toBe(true);
  });

  it("500 ApiError에 false", () => {
    expect(isNotFoundError(new ApiError(500, "server error"))).toBe(false);
  });
});

describe("userFriendlyMessage", () => {
  describe("context 없이 호출", () => {
    it("401 -> 로그인 필요 메시지", () => {
      const err = new ApiError(401, "Unauthorized");
      expect(userFriendlyMessage(err)).toBe("로그인이 필요해요.");
    });

    it("403 -> 권한 없음 메시지", () => {
      const err = new ApiError(403, "Forbidden");
      expect(userFriendlyMessage(err)).toBe(
        "권한이 없어요. 관리자에게 문의해 주세요.",
      );
    });

    it("404 -> 찾을 수 없음 메시지", () => {
      const err = new ApiError(404, "Not Found");
      expect(userFriendlyMessage(err)).toBe(
        "요청한 항목을 찾을 수 없어요. 삭제되었거나 접근할 수 없는 항목이에요.",
      );
    });

    it("409 STALE_EDIT 코드이면 낙관적 잠금 충돌 메시지를 노출한다", () => {
      const err = new ApiError(409, "stale", {
        code: "STALE_EDIT",
        message: "stale",
      });
      expect(userFriendlyMessage(err)).toBe(
        "다른 곳에서 변경이 있었어요. 새로고침 후 다시 시도해 주세요.",
      );
    });

    it("409 에 한국어 사유가 담겨있으면 서버 메시지를 그대로 노출한다", () => {
      const err = new ApiError(409, "하위 팀 1개가 남아 있어 부서를 삭제할 수 없습니다.");
      expect(userFriendlyMessage(err)).toBe(
        "하위 팀 1개가 남아 있어 부서를 삭제할 수 없습니다.",
      );
    });

    it("409 payload 에 code 가 없고 영어 기술 메시지만 있으면 제네릭 fallback 을 반환한다", () => {
      const err = new ApiError(409, "Conflict");
      expect(userFriendlyMessage(err)).toBe(
        "요청이 현재 상태와 맞지 않아 처리하지 못했어요.",
      );
    });

    it("409 username_exists 코드는 회원가입 안내 메시지를 반환한다", () => {
      const err = new ApiError(409, "Username already exists", {
        code: "username_exists",
        message: "Username already exists",
      });
      expect(userFriendlyMessage(err, "가입에 실패했어요")).toBe(
        "가입에 실패했어요. 이미 가입된 계정이에요. 로그인해 주세요.",
      );
    });

    it("409 + 번역 맵에 있는 영어 메시지(코드 없음)는 한국어로 번역된다", () => {
      const err = new ApiError(409, "Username already exists");
      expect(userFriendlyMessage(err)).toBe("이미 사용 중인 아이디예요");
    });

    it("409 + 카테고리 이름 중복 영어 메시지는 한국어로 번역된다", () => {
      const err = new ApiError(409, "Category name already exists");
      expect(userFriendlyMessage(err)).toBe(
        "이미 같은 이름의 카테고리가 있어요",
      );
    });

    it("422 INVALID_STATE -> 상태 불가 메시지", () => {
      const err = new ApiError(422, "Invalid state", {
        code: "INVALID_STATE",
        message: "Invalid state",
      });
      expect(userFriendlyMessage(err)).toBe(
        "현재 상태에서는 이 작업을 할 수 없어요.",
      );
    });

    it("422 기타 -> 입력값 확인 메시지", () => {
      const err = new ApiError(422, "Unprocessable", {
        code: "OTHER",
        message: "Unprocessable",
      });
      expect(userFriendlyMessage(err)).toBe("입력값을 확인해 주세요.");
    });

    it("429 -> 요청 제한 메시지", () => {
      const err = new ApiError(429, "Too Many Requests");
      expect(userFriendlyMessage(err)).toBe(
        "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("502 DEPENDENCY_FAILURE -> 외부 서비스 메시지", () => {
      const err = new ApiError(502, "Bad Gateway", {
        code: "DEPENDENCY_FAILURE",
        message: "Bad Gateway",
      });
      expect(userFriendlyMessage(err)).toBe(
        "외부 서비스 연결에 문제가 있어요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("500 -> 일시적 오류 메시지", () => {
      const err = new ApiError(500, "Internal Server Error");
      expect(userFriendlyMessage(err)).toBe(
        "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("503 -> 일시적 오류 메시지", () => {
      const err = new ApiError(503, "Service Unavailable");
      expect(userFriendlyMessage(err)).toBe(
        "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    });
  });

  describe("context 포함 호출", () => {
    it("403 + context -> 접두어 포함", () => {
      const err = new ApiError(403, "Forbidden");
      expect(userFriendlyMessage(err, "저장하지 못했어요")).toBe(
        "저장하지 못했어요. 권한이 없어요. 관리자에게 문의해 주세요.",
      );
    });

    it("404 + context -> 접두어 포함", () => {
      const err = new ApiError(404, "Not Found");
      expect(
        userFriendlyMessage(err, "데이터를 불러오지 못했어요"),
      ).toBe(
        "데이터를 불러오지 못했어요. 요청한 항목을 찾을 수 없어요. 삭제되었거나 접근할 수 없는 항목이에요.",
      );
    });

    it("429 + context -> 접두어 포함", () => {
      const err = new ApiError(429, "Too Many Requests");
      expect(userFriendlyMessage(err, "저장하지 못했어요")).toBe(
        "저장하지 못했어요. 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.",
      );
    });
  });

  describe("400 에러 메시지 처리", () => {
    it("한글 메시지는 그대로 전달", () => {
      const err = new ApiError(400, "주제 이름을 입력해 주세요.", {
        code: "INVALID_INPUT",
        message: "주제 이름을 입력해 주세요.",
      });
      expect(userFriendlyMessage(err)).toBe("주제 이름을 입력해 주세요.");
    });

    it("번역 맵에 있는 영어 메시지는 한국어로 번역", () => {
      const err = new ApiError(400, "requestName is required", {
        code: "VALIDATION_ERROR",
        message: "requestName is required",
      });
      expect(userFriendlyMessage(err)).toBe("요청 이름을 입력해 주세요");
    });

    it("번역 맵에 없는 camelCase 메시지는 제네릭으로 대체", () => {
      const err = new ApiError(400, "unknownFieldName is invalid", {
        code: "VALIDATION_ERROR",
        message: "unknownFieldName is invalid",
      });
      expect(userFriendlyMessage(err)).toBe("입력값을 확인해 주세요.");
    });

    it("SQL 키워드 포함 메시지는 제네릭으로 대체", () => {
      const err = new ApiError(400, "SQL syntax error near SELECT", {
        code: "INVALID_INPUT",
        message: "SQL syntax error",
      });
      expect(userFriendlyMessage(err)).toBe("입력값을 확인해 주세요.");
    });

    it("Exception 포함 메시지는 제네릭으로 대체", () => {
      const err = new ApiError(
        400,
        "NullPointerException at service",
        {
          code: "INVALID_INPUT",
          message: "NullPointerException",
        },
      );
      expect(userFriendlyMessage(err)).toBe("입력값을 확인해 주세요.");
    });

    it("Slack 관련 에러 메시지 번역", () => {
      const err = new ApiError(
        400,
        "conversations.info failed: invalid_arguments",
        {
          code: "VALIDATION_ERROR",
          message: "conversations.info failed: invalid_arguments",
        },
      );
      expect(userFriendlyMessage(err)).toBe(
        "Slack 채널 정보를 불러오지 못했어요. 채널 설정을 확인해 주세요",
      );
    });
  });

  describe("추적 ID 제거", () => {
    it("추적 ID가 메시지에서 제거된다", () => {
      const err = new ApiError(
        400,
        "잘못된 입력입니다. (추적 ID: abc-123-def)",
        {
          code: "INVALID_INPUT",
          message: "잘못된 입력입니다.",
        },
      );
      expect(userFriendlyMessage(err)).toBe("잘못된 입력입니다.");
    });
  });

  describe("네트워크 에러", () => {
    it("TypeError -> 네트워크 연결 확인 메시지", () => {
      const err = new TypeError("Failed to fetch");
      expect(userFriendlyMessage(err)).toBe(
        "네트워크 연결을 확인해 주세요.",
      );
    });

    it("TypeError + context -> 접두어 포함", () => {
      const err = new TypeError("Failed to fetch");
      expect(userFriendlyMessage(err, "저장하지 못했어요")).toBe(
        "저장하지 못했어요. 네트워크 연결을 확인해 주세요.",
      );
    });
  });

  describe("알 수 없는 에러", () => {
    it("문자열 에러 -> 일시적 오류 메시지", () => {
      expect(userFriendlyMessage("something")).toBe(
        "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("undefined -> 일시적 오류 메시지", () => {
      expect(userFriendlyMessage(undefined)).toBe(
        "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("null -> 일시적 오류 메시지", () => {
      expect(userFriendlyMessage(null)).toBe(
        "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    });
  });

  describe("기타 4xx", () => {
    it("한국어 메시지가 있는 4xx는 메시지 그대로", () => {
      const err = new ApiError(418, "요청이 너무 많아요.");
      expect(userFriendlyMessage(err)).toBe("요청이 너무 많아요.");
    });

    it("기술 메시지가 포함된 4xx는 제네릭", () => {
      const err = new ApiError(418, "at RateLimiter.checkLimit");
      expect(userFriendlyMessage(err)).toBe(
        "요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.",
      );
    });

    it("영어 전용 메시지가 포함된 4xx는 제네릭 폴백", () => {
      const err = new ApiError(418, "I'm a teapot");
      expect(userFriendlyMessage(err)).toBe(
        "요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.",
      );
    });
  });
});

describe("translateRawMessage", () => {
  it("null 입력 -> 기본 폴백 메시지", () => {
    expect(translateRawMessage(null)).toBe("알 수 없는 오류가 발생했어요");
  });

  it("undefined 입력 -> 기본 폴백 메시지", () => {
    expect(translateRawMessage(undefined)).toBe(
      "알 수 없는 오류가 발생했어요",
    );
  });

  it("null + 커스텀 폴백 -> 커스텀 폴백 메시지", () => {
    expect(translateRawMessage(null, "크롤링 오류가 발생했어요")).toBe(
      "크롤링 오류가 발생했어요",
    );
  });

  it("번역 맵에 있는 영어 메시지 -> 한국어", () => {
    expect(translateRawMessage("Feed parsing failed")).toBe(
      "RSS 피드를 읽을 수 없어요. 주소를 다시 확인해 주세요",
    );
  });

  it("번역 맵에 부분 일치하는 메시지 -> 한국어", () => {
    expect(translateRawMessage("Connection timed out after 30s")).toBe(
      "연결 시간이 초과됐어요. 잠시 후 다시 시도해 주세요",
    );
  });

  it("한국어 메시지 -> 그대로 반환", () => {
    expect(translateRawMessage("이미 등록된 소스입니다")).toBe(
      "이미 등록된 소스입니다",
    );
  });

  it("기술적 영어 메시지 -> 제네릭 폴백", () => {
    expect(
      translateRawMessage("NullPointerException at CrawlService.run"),
    ).toBe("오류가 발생했어요. 잠시 후 다시 시도해 주세요");
  });

  it("기술적 영어 메시지 + 커스텀 폴백 -> 커스텀 폴백", () => {
    expect(
      translateRawMessage(
        "ConstraintViolation in DB",
        "크롤링 오류가 발생했어요",
      ),
    ).toBe("크롤링 오류가 발생했어요");
  });

  it("일반 영어 메시지 -> 제네릭 폴백", () => {
    expect(translateRawMessage("Some unknown english error")).toBe(
      "오류가 발생했어요. 잠시 후 다시 시도해 주세요",
    );
  });

  it("추적 ID가 제거된다", () => {
    expect(
      translateRawMessage("잘못된 입력입니다. (추적 ID: abc-123-def)"),
    ).toBe("잘못된 입력입니다.");
  });

  it("Slack 에러 부분 일치 -> 번역", () => {
    expect(
      translateRawMessage(
        "conversations.info failed: invalid_arguments",
      ),
    ).toBe(
      "Slack 채널 정보를 불러오지 못했어요. 채널 설정을 확인해 주세요",
    );
  });
});

describe("extractStaleEditInfo", () => {
  it("409 + STALE_EDIT payload이면 info를 반환한다", () => {
    const err = new ApiError(409, "stale", {
      code: "CONFLICT",
      message: "stale",
      staleEditInfo: {
        code: "STALE_EDIT",
        latestUpdatedAt: "2026-04-17T10:00:00Z",
        latestEditorName: "김관리",
        changedFieldNames: ["name"],
      },
    });
    expect(extractStaleEditInfo(err)).toEqual({
      code: "STALE_EDIT",
      latestUpdatedAt: "2026-04-17T10:00:00Z",
      latestEditorName: "김관리",
      changedFieldNames: ["name"],
    });
  });

  it("409이지만 staleEditInfo가 없으면 null을 반환한다", () => {
    const err = new ApiError(409, "duplicate name", { code: "CONFLICT", message: "duplicate name" });
    expect(extractStaleEditInfo(err)).toBeNull();
  });

  it("staleEditInfo.code가 STALE_EDIT이 아니면 null을 반환한다", () => {
    const err = new ApiError(409, "other", {
      code: "CONFLICT",
      staleEditInfo: {
        code: "OTHER_CONFLICT",
        latestUpdatedAt: "2026-04-17T10:00:00Z",
        latestEditorName: "a",
        changedFieldNames: [],
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any,
    });
    expect(extractStaleEditInfo(err)).toBeNull();
  });

  it("409가 아닌 에러에는 null을 반환한다", () => {
    const err = new ApiError(400, "bad", { code: "INVALID_INPUT" });
    expect(extractStaleEditInfo(err)).toBeNull();
  });

  it("일반 Error 객체는 null을 반환한다", () => {
    expect(extractStaleEditInfo(new Error("nope"))).toBeNull();
  });

  it("null / undefined는 null을 반환한다", () => {
    expect(extractStaleEditInfo(null)).toBeNull();
    expect(extractStaleEditInfo(undefined)).toBeNull();
  });
});
