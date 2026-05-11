import { getChoseong } from "es-hangul";

/**
 * 초성 또는 일반 텍스트로 한글 검색
 * - 쿼리가 초성(ㄱ-ㅎ)으로만 구성된 경우: getChoseong으로 비교
 * - 그 외: toLowerCase().includes()로 비교
 */
export function matchesKoreanSearch(target: string, query: string): boolean {
  if (!query) return true;
  const normalized = query.trim().toLowerCase();
  const isChoseongOnly = /^[ㄱ-ㅎ]+$/.test(normalized);
  if (isChoseongOnly) {
    return getChoseong(target).includes(normalized);
  }
  return target.toLowerCase().includes(normalized);
}
