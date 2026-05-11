/**
 * 편집 presence 도메인 타입.
 *
 * 백엔드 `EditingPresenceController.EditingSessionResponse` 와 1:1 대응한다.
 * 편집 모달이 열려 있는 관리자 한 명을 표현한다.
 */
export interface EditingSession {
  /** 편집자 userId (Spring Security `authentication.name`) */
  userId: string;
  /** UI 에 표시되는 이름. 기본값 "관리자" 는 계정에 displayName 이 없을 때. */
  displayName: string;
  /** 편집 세션이 처음 시작된 시각 (ISO 8601 UTC) */
  startedAt: string;
}

/**
 * presence 가 추적하는 리소스 타입.
 *
 * 백엔드 `EditingPresenceService.ALLOWED_RESOURCE_TYPES` 와 동기 유지.
 */
export type EditingResourceType =
  | "persona"
  | "category"
  | "categoryRule"
  | "rssSource";
