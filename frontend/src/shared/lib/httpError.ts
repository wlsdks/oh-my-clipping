import { ApiError } from "../api/httpClient";
import type { StaleEditInfo } from "../types/common";

export function isHttpStatus(error: unknown, status: number): error is ApiError {
  return error instanceof ApiError && error.status === status;
}

export function isConflictError(error: unknown): error is ApiError {
  return isHttpStatus(error, 409);
}

/**
 * 409 응답 payload에서 {@link StaleEditInfo}를 추출한다.
 *
 * 서버 `AdminErrorDtos.ErrorResponse.staleEditInfo`가 채워진 경우에만 값을 반환한다.
 * 일반 409(중복/상태 충돌)에는 null이다.
 */
export function extractStaleEditInfo(error: unknown): StaleEditInfo | null {
  if (!isConflictError(error)) return null;
  const info = error.payload?.staleEditInfo;
  if (!info) return null;
  // code 필드가 STALE_EDIT인 경우에만 모달을 띄운다 (다른 409와 구분).
  if (info.code !== "STALE_EDIT") return null;
  return info;
}

export function isForbiddenError(error: unknown): error is ApiError {
  return isHttpStatus(error, 403);
}

export function isNotFoundError(error: unknown): error is ApiError {
  return isHttpStatus(error, 404);
}

/** 영어 백엔드 메시지 → 한국어 사용자 메시지 번역 */
const ERROR_TRANSLATIONS: Record<string, string> = {
  // URL/소스 관련
  "URL scheme is required": "http:// 또는 https://로 시작하는 주소를 입력해 주세요",
  "Invalid URL format": "올바르지 않은 URL 형식이에요",
  "Invalid URL": "올바르지 않은 주소예요. 다시 확인해 주세요",
  "URL host is required": "URL에 호스트가 필요해요",
  "URL host could not be resolved":
    "URL 호스트를 확인할 수 없어요. 주소를 다시 확인해 주세요",
  "Only HTTP(S) URLs are allowed":
    "http:// 또는 https://로 시작하는 주소만 허용돼요",
  "Localhost is not allowed": "localhost 주소는 허용되지 않아요",
  "Private or local network addresses are not allowed":
    "내부 네트워크 주소는 허용되지 않아요",
  "URL is blocked": "보안 정책으로 차단된 주소예요",
  "Feed parsing failed":
    "RSS 피드를 읽을 수 없어요. 주소를 다시 확인해 주세요",
  "Not a valid RSS":
    "유효한 RSS 피드가 아니에요. 주소를 다시 확인해 주세요",
  "Connection timed out":
    "연결 시간이 초과됐어요. 잠시 후 다시 시도해 주세요",
  "Connection refused":
    "서버에 연결할 수 없어요. 주소를 다시 확인해 주세요",
  "Failed to collect RSS":
    "RSS 수집에 실패했어요. 주소가 유효한지, 서버가 응답하는지 확인해 주세요",
  "after 3 attempts":
    "여러 번 시도했지만 응답이 없어요. 주소를 다시 확인해 주세요",
  "unknown host":
    "호스트를 찾을 수 없어요. 주소를 다시 확인해 주세요",
  UnknownHostException:
    "호스트를 찾을 수 없어요. 주소를 다시 확인해 주세요",
  SocketTimeoutException:
    "응답 시간이 초과됐어요. 잠시 후 다시 시도해 주세요",
  SSLHandshakeException:
    "SSL 인증서 오류가 발생했어요. 주소를 다시 확인해 주세요",
  "status code: 404":
    "피드 주소를 찾을 수 없어요 (404). 경로가 바뀌었을 수 있어요",
  "status code: 403":
    "피드 접근이 차단됐어요 (403). 권한을 확인해 주세요",
  "status code: 500":
    "대상 서버에서 오류가 발생했어요 (500). 잠시 후 다시 시도해 주세요",
  // Slack 관련
  "conversations.info failed":
    "Slack 채널 정보를 불러오지 못했어요. 채널 설정을 확인해 주세요",
  invalid_arguments: "잘못된 요청이에요. 입력값을 다시 확인해 주세요",
  channel_not_found:
    "Slack 채널을 찾을 수 없어요. 채널 설정을 확인해 주세요",
  not_in_channel:
    "봇이 해당 Slack 채널에 참여하지 않았어요. 채널에 봇을 초대해 주세요",
  missing_scope: "Slack 봇 권한이 부족해요. 관리자에게 문의해 주세요",
  token_revoked: "Slack 연동이 해제됐어요. 다시 연동해 주세요",
  // 입력 검증
  "Name is required": "이름을 입력해 주세요",
  "requestName is required": "요청 이름을 입력해 주세요",
  "sourceName is required": "소스 이름을 입력해 주세요",
  "sourceUrl is required": "소스 URL을 입력해 주세요",
  "personaName is required": "페르소나 이름을 입력해 주세요",
  "personaPrompt is required": "페르소나 프롬프트를 입력해 주세요",
  "categoryId is required": "카테고리를 선택해 주세요",
  "baseRequestId is required": "기준 요청을 선택해 주세요",
  "slackChannelId is required": "Slack 채널을 설정해 주세요",
  // 한도
  "Category name already exists": "이미 같은 이름의 카테고리가 있어요",
  "Username already exists": "이미 사용 중인 아이디예요",
  "Maximum batch size is 50": "한 번에 최대 50건까지 처리할 수 있어요",
  "Max items must be between 1 and 50":
    "최대 항목 수는 1~50 범위여야 해요",
  "maxItems must be between 1 and 50":
    "최대 항목 수는 1~50 범위여야 해요",
  "includeThreshold must be between 0 and 1":
    "포함 임계치는 0~1 범위여야 해요",
  "reviewThreshold must be between 0 and 1":
    "검토 임계치는 0~1 범위여야 해요",
  "days must be between 1 and 90": "조회 기간은 1~90일 범위여야 해요",
  // Slack
  "Slack bot token is not configured": "Slack 봇 토큰이 설정되지 않았어요",
  "Slack API error": "Slack 연동에 문제가 있어요. 설정을 확인해 주세요",
  // 인증
  "Signup is disabled": "현재 회원가입이 비활성화되어 있어요",
  "Only ADMIN can": "관리자만 수행할 수 있는 작업이에요",
  // 상태
  "Request failed with status code": "요청 처리 중 오류가 발생했어요",
  "Internal Server Error":
    "서버 오류가 발생했어요. 잠시 후 다시 시도해 주세요",
};

/** 한국어 문자가 하나도 없는지 확인 */
const HAS_KOREAN_RE = /[\uAC00-\uD7AF\u1100-\u11FF\u3130-\u318F]/;

/** 알려진 번역이 있으면 반환, 없으면 undefined */
export function translateErrorMessage(msg: string): string | undefined {
  // 정확히 일치
  if (ERROR_TRANSLATIONS[msg]) return ERROR_TRANSLATIONS[msg];
  // 부분 일치 (백엔드가 추가 컨텍스트를 붙인 경우)
  for (const [key, value] of Object.entries(ERROR_TRANSLATIONS)) {
    if (msg.includes(key)) return value;
  }
  return undefined;
}

/** 기술 메시지 패턴 -- 사용자에게 노출하면 안 되는 내용 */
const TECHNICAL_PATTERNS = [
  /\bat\s+\w+\.\w+/,
  /Exception/i,
  /\bSQL\b/i,
  /\bJDBC\b/i,
  /NullPointer/i,
  /\bstacktrace\b/i,
  /org\.springframework/i,
  /com\.clipping\.mcpserver/i,
  /ConstraintViolation/i,
  /DataIntegrity/i,
  /IllegalState/i,
  /\bbean\b.*\bcreation\b/i,
];

/** camelCase 필드명 패턴 (2단어 이상) */
const CAMEL_CASE_RE = /\b[a-z]+[A-Z][a-zA-Z]*\b/;

/** 추적 ID 패턴 — 디버깅용이므로 사용자 메시지에서 제거 */
const TRACE_ID_RE = /\s*\(추적 ID:\s*[^)]*\)/g;

function isTechnicalMessage(msg: string): boolean {
  if (CAMEL_CASE_RE.test(msg)) return true;
  return TECHNICAL_PATTERNS.some((p) => p.test(msg));
}

function stripTraceId(msg: string): string {
  return msg.replace(TRACE_ID_RE, "").trim();
}

/**
 * 임의의 에러 문자열을 사용자 친화적 한국어로 변환한다.
 *
 * API 에러가 아닌 raw 문자열(예: source.lastCrawlError)을
 * 사용자에게 보여줄 때 사용한다.
 *
 * @returns 번역된 한국어 메시지, 또는 이미 한국어면 원문,
 *          영어/기술 메시지면 제네릭 폴백
 */
export function translateRawMessage(
  msg: string | null | undefined,
  fallback?: string,
): string {
  if (!msg) return fallback ?? "알 수 없는 오류가 발생했어요";

  const stripped = stripTraceId(msg);

  // 번역 맵에서 먼저 탐색
  const translated = translateErrorMessage(stripped);
  if (translated) return translated;

  // 이미 한국어가 포함된 메시지는 그대로 (기술 패턴 제외)
  if (HAS_KOREAN_RE.test(stripped) && !isTechnicalMessage(stripped)) {
    return stripped;
  }

  // 영어/기술 메시지는 제네릭 폴백
  return fallback ?? "오류가 발생했어요. 잠시 후 다시 시도해 주세요";
}

/**
 * 에러를 사용자 친화적 메시지로 변환한다.
 *
 * 모든 사용자 노출 에러 메시지의 단일 게이트웨이 역할을 한다.
 * 영어 기술 메시지가 사용자에게 노출되지 않도록 보장한다.
 *
 * @param error - catch 블록에서 잡은 에러
 * @param context - 실패한 동작의 완성된 접두어.
 *   예: "저장하지 못했어요", "데이터를 불러오지 못했어요"
 */
export function userFriendlyMessage(
  error: unknown,
  context?: string,
): string {
  const prefix = context ? `${context}. ` : "";

  if (error instanceof ApiError) {
    const { status, payload } = error;
    const code = payload?.code;

    switch (status) {
      case 401:
        return `${prefix}로그인이 필요해요.`;
      case 403:
        return `${prefix}권한이 없어요. 관리자에게 문의해 주세요.`;
      case 404:
        return `${prefix}요청한 항목을 찾을 수 없어요. 삭제되었거나 접근할 수 없는 항목이에요.`;
      case 409: {
        // STALE_EDIT(낙관적 잠금 충돌) 은 전용 안내로 고정.
        if (code === "STALE_EDIT") {
          return `${prefix}다른 곳에서 변경이 있었어요. 새로고침 후 다시 시도해 주세요.`;
        }
        // 회원가입 username 중복 — 사용자가 다음에 할 행동(로그인)을 안내.
        if (code === "username_exists") {
          return `${prefix}이미 가입된 계정이에요. 로그인해 주세요.`;
        }
        const conflictMsg = stripTraceId(error.message);
        // 알려진 영어 메시지는 한국어로 번역 (예: "Username already exists").
        const translated = translateErrorMessage(conflictMsg);
        if (translated) {
          return `${prefix}${translated}`;
        }
        // 한국어 사유는 그대로 노출 (예: 하위 팀 잔존, 활성 상태, 중복 이름).
        if (conflictMsg && !isTechnicalMessage(conflictMsg) && HAS_KOREAN_RE.test(conflictMsg)) {
          return `${prefix}${conflictMsg}`;
        }
        return `${prefix}요청이 현재 상태와 맞지 않아 처리하지 못했어요.`;
      }
      case 422:
        if (code === "INVALID_STATE") {
          return `${prefix}현재 상태에서는 이 작업을 할 수 없어요.`;
        }
        return `${prefix}입력값을 확인해 주세요.`;
      case 429:
        return `${prefix}요청이 너무 많아요. 잠시 후 다시 시도해 주세요.`;
      case 502:
        if (code === "DEPENDENCY_FAILURE") {
          return `${prefix}외부 서비스 연결에 문제가 있어요. 잠시 후 다시 시도해 주세요.`;
        }
        return `${prefix}일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.`;
      case 400: {
        const rawMsg = stripTraceId(error.message);
        // 알려진 영어 에러 메시지 번역
        const translated = translateErrorMessage(rawMsg);
        if (translated) return `${prefix}${translated}`;

        if (code === "VALIDATION_ERROR" || code === "INVALID_INPUT") {
          if (isTechnicalMessage(rawMsg)) {
            return `${prefix}입력값을 확인해 주세요.`;
          }
          // 한국어가 없는 영어 전용 메시지는 폴백
          if (!HAS_KOREAN_RE.test(rawMsg)) {
            return `${prefix}입력값을 확인해 주세요.`;
          }
          return `${prefix}${rawMsg}`;
        }
        if (isTechnicalMessage(rawMsg)) {
          return `${prefix}입력값을 확인해 주세요.`;
        }
        // 한국어가 없는 영어 전용 메시지는 폴백
        if (!HAS_KOREAN_RE.test(rawMsg)) {
          return `${prefix}입력값을 확인해 주세요.`;
        }
        return `${prefix}${rawMsg}`;
      }
      default:
        if (status >= 500) {
          return `${prefix}일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.`;
        }
        break;
    }

    // 기타 4xx — 기술 메시지 필터링
    const rawMsg = stripTraceId(error.message);
    if (isTechnicalMessage(rawMsg)) {
      return `${prefix}요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.`;
    }
    // 한국어가 없는 영어 전용 메시지는 폴백
    if (!HAS_KOREAN_RE.test(rawMsg)) {
      return `${prefix}요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.`;
    }
    return `${prefix}${rawMsg}`;
  }

  // TypeError / 네트워크 에러
  if (error instanceof TypeError) {
    return `${prefix}네트워크 연결을 확인해 주세요.`;
  }

  return `${prefix}일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.`;
}
