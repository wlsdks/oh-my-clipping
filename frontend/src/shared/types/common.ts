/**
 * 낙관적 잠금 충돌(409 STALE_EDIT) 시 서버가 반환하는 편집 상태 스냅샷.
 *
 * 백엔드 `com.clipping.mcpserver.error.StaleEditInfo`와 1:1로 매핑된다.
 * 응답 본문에 `code: "STALE_EDIT"`로 들어오며, 프론트는 이 정보로
 * "누가 먼저 저장했는지"와 변경 필드 요약을 사용자에게 보여준다.
 */
export interface StaleEditInfo {
  /** 충돌 분류 코드. 현재는 고정값 "STALE_EDIT". */
  code: "STALE_EDIT";
  /** 서버가 보고 있는 최신 updated_at (ISO 8601). 재저장 시 expectedUpdatedAt으로 사용한다. */
  latestUpdatedAt: string;
  /** 최근 수정자의 display name. 미상이면 "관리자"로 익명화돼 온다. */
  latestEditorName: string;
  /** 최근 patch에서 사용자가 바꾸려고 한 필드 이름 목록. 비어 있을 수 있다. */
  changedFieldNames: string[];
}

export interface ApiErrorShape {
  error?: string;
  code?: string;
  message?: string;
  traceId?: string;
  /** 409 STALE_EDIT 충돌에서만 채워지는 편집 상태 스냅샷. */
  staleEditInfo?: StaleEditInfo | null;
}

export interface Notice {
  type: "info" | "success" | "warning" | "error";
  text: string;
}
