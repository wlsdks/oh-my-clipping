/**
 * 엔티티 변경 이력 공용 타입.
 *
 * 4개 도메인(Persona, Category, CategoryRule, RssSource)의 변경 이력 API 응답 모양.
 * 백엔드의 `EntityRevisionResourceType`의 wire string과 일치한다.
 */
export type RevisionResource = "persona" | "category" | "category_rule" | "rss_source";

export interface RevisionSummary {
  revisionId: string;
  revisionNumber: number;
  editorId: string;
  /** 익명화된 표시 이름. UUID/system은 "관리자"/"시스템"으로 치환된다. */
  editorName: string;
  changedFields: string[];
  /** ISO-8601 UTC 문자열. */
  createdAt: string;
}
