export const ACTION_LABELS: Record<string, string> = {
  CREATE: "생성",
  UPDATE: "수정",
  DELETE: "삭제",
  RESET: "기본값 복원",
  APPROVE: "승인",
  REJECT: "반려",
  UNSUBSCRIBE: "구독 해제",
  DEACTIVATE: "비활성화",
  UPSERT: "저장",
  LOGIN: "로그인",
  PIPELINE_RUN: "파이프라인 실행",
  TRIGGER: "자동 실행",
};

export const TARGET_TYPE_LABELS: Record<string, string> = {
  SUBSCRIPTION: "구독",
  USER: "사용자",
  SOURCE: "소스",
  CATEGORY: "카테고리",
  PERSONA: "페르소나",
  RUNTIME_SETTING: "시스템 설정",
  PIPELINE: "파이프라인",
  REVIEW_ITEM: "검토 항목",
  RULE: "규칙",
  USER_ACCOUNT: "회원",
  Unknown: "기타",
};

export function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

export function targetTypeLabel(type: string): string {
  return TARGET_TYPE_LABELS[type] ?? type;
}
