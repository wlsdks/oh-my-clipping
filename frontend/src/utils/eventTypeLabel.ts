const EVENT_TYPE_MAP: Record<string, string> = {
  PRODUCT_LAUNCH: "제품 출시",
  PARTNERSHIP: "제휴/협력",
  FUNDING: "투자/인수",
  POLICY: "정책/규제",
  PERSONNEL: "인사/조직",
};

/**
 * 경쟁사 기사의 이벤트 유형(영어 enum)을 한국어 라벨로 변환한다.
 * "OTHER"이거나 매핑 없는 값이면 null을 반환한다 (표시하지 않음).
 */
export function getEventTypeLabel(eventType: string): string | null {
  return EVENT_TYPE_MAP[eventType] ?? null;
}
