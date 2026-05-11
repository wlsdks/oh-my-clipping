/**
 * 클라이언트 사이드 digest mode 결정 로직.
 *
 * Backend `resolveDigestMode(keywordCount, orgCount)` 와 같은 매트릭스를 따르되,
 * (0, 0) 인 경우에만 null 을 반환한다 (BE 는 IllegalState 던짐 — UI 는 "아직 설정 안 됨" 상태로 표시).
 */
export type DigestMode = "TOPIC_ONLY" | "ACCOUNT_ONLY" | "CROSSFILTER" | "DUAL_SECTION";

/**
 * 키워드/조직 개수로 digest mode 를 결정한다.
 * (0, 0) → null (아직 구성 미완료); 그 외 매트릭스는 BE 와 일치.
 */
export function resolveDigestModeClient(keywordCount: number, orgCount: number): DigestMode | null {
  if (keywordCount === 0 && orgCount === 0) return null;
  if (orgCount === 0) return "TOPIC_ONLY";
  if (keywordCount === 0) return "ACCOUNT_ONLY";
  if (keywordCount === 1 || orgCount === 1) return "CROSSFILTER";
  return "DUAL_SECTION";
}

/**
 * 사용자에게 노출할 모드 레이블 — toast 및 드롭다운에서 사용.
 */
export function modeLabel(mode: DigestMode | null): string {
  switch (mode) {
    case "TOPIC_ONLY": return "주제 뉴스";
    case "ACCOUNT_ONLY": return "기업 동향";
    case "CROSSFILTER": return "주제×기업 교차 필터";
    case "DUAL_SECTION": return "주제+기업 듀얼 섹션";
    default: return "—";
  }
}
