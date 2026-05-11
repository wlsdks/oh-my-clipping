import { z } from "zod";

/**
 * 관리자/유저 입력 공용 zod 스키마.
 *
 * BE의 `InputSanitizer`(저장 경계 길이 상한)와 DB VARCHAR 제약에 정확히 맞춘다.
 * 세부 매트릭스는 입력 검증 매트릭스 참고.
 *
 * 모든 문자열은 서버 측과 동일하게 `.trim()`을 선 적용한 뒤 길이를 평가한다.
 */

/** Slack 채널 ID 패턴 — C(채널)/G(프라이빗)/D(DM)/U(유저 DM) */
export const slackChannelIdPattern = /^[CGDU][A-Z0-9]{8,}$/;

/** 관리자 입력용 스키마 묶음 — BE [InputSanitizer] 경계 상한과 동기화 */
export const adminInputSchemas = {
  personaName: z
    .string()
    .trim()
    .min(1, "이름을 입력하세요")
    .max(200, "이름은 최대 200자까지 입력할 수 있어요"),
  personaSystemPrompt: z
    .string()
    .trim()
    .min(1, "시스템 프롬프트를 입력하세요")
    .max(5000, "시스템 프롬프트는 최대 5000자까지 입력할 수 있어요"),
  personaSummaryStyle: z
    .string()
    .trim()
    .max(1000, "요약 스타일은 최대 1000자까지 입력할 수 있어요")
    .optional(),
  personaTargetAudience: z
    .string()
    .trim()
    .max(1000, "대상 독자는 최대 1000자까지 입력할 수 있어요")
    .optional(),
  personaDescription: z
    .string()
    .trim()
    .max(1000, "설명은 최대 1000자까지 입력할 수 있어요")
    .optional(),
  personaPreviewTitle: z.string().trim().max(200, "제목은 최대 200자까지 입력할 수 있어요").optional(),
  personaPreviewSource: z.string().trim().max(200, "출처는 최대 200자까지 입력할 수 있어요").optional(),
  personaPreviewBody: z
    .string()
    .trim()
    .max(2000, "본문은 최대 2000자까지 입력할 수 있어요")
    .optional(),

  categoryName: z
    .string()
    .trim()
    .min(1, "주제 이름을 입력하세요")
    .max(200, "주제 이름은 최대 200자까지 입력할 수 있어요"),
  categoryDescription: z
    .string()
    .trim()
    .max(1000, "주제 설명은 최대 1000자까지 입력할 수 있어요")
    .optional(),

  competitorName: z
    .string()
    .trim()
    .min(1, "경쟁사 이름을 입력하세요")
    .max(100, "경쟁사 이름은 최대 100자까지 입력할 수 있어요"),
  competitorAlias: z.string().trim().max(60, "별칭은 최대 60자까지 입력할 수 있어요"),
  competitorExcludeKeyword: z
    .string()
    .trim()
    .max(60, "제외 키워드는 최대 60자까지 입력할 수 있어요"),

  sourceName: z
    .string()
    .trim()
    .min(1, "소스 이름을 입력하세요")
    .max(200, "소스 이름은 최대 200자까지 입력할 수 있어요"),
  sourceUrl: z
    .string()
    .trim()
    .min(1, "URL을 입력하세요")
    .max(2000, "URL은 최대 2000자까지 입력할 수 있어요")
    .url("올바른 URL을 입력하세요"),
  sourceReviewNotes: z
    .string()
    .trim()
    .max(1000, "검토 메모는 최대 1000자까지 입력할 수 있어요")
    .optional(),

  channelId: z
    .string()
    .trim()
    .regex(slackChannelIdPattern, "C/G/D/U로 시작하는 채널 ID 형식이어야 해요")
    .optional(),
} as const;

/** 유저 입력용 스키마 묶음 — 구독 요청 등 사용자 경계에서 재사용 */
export const userInputSchemas = {
  requestName: z
    .string()
    .trim()
    .min(1, "요청 이름을 입력하세요")
    .max(120, "요청 이름은 최대 120자까지 입력할 수 있어요"),
  sourceName: z
    .string()
    .trim()
    .min(1, "소스 이름을 입력하세요")
    .max(120, "소스 이름은 최대 120자까지 입력할 수 있어요"),
  sourceUrl: z
    .string()
    .trim()
    .min(1, "URL을 입력하세요")
    .max(2000, "URL은 최대 2000자까지 입력할 수 있어요")
    .url("올바른 URL을 입력하세요"),
  personaName: z
    .string()
    .trim()
    .min(1, "페르소나 이름을 입력하세요")
    .max(120, "페르소나 이름은 최대 120자까지 입력할 수 있어요"),
  personaPrompt: z
    .string()
    .trim()
    .min(1, "페르소나 프롬프트를 입력하세요")
    .max(5000, "페르소나 프롬프트는 최대 5000자까지 입력할 수 있어요"),
  requestNote: z
    .string()
    .trim()
    .max(1000, "요청 메모는 최대 1000자까지 입력할 수 있어요")
    .optional(),
} as const;
