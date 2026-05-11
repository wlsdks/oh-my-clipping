/**
 * 날짜/시간 관련 유틸리티 함수 모음.
 *
 * 상대 시간 표시 등 사용자 친화적 날짜 포맷을 제공한다.
 */

/** ISO 날짜를 상대 시간 문자열로 변환한다. */
export function formatRelativeTime(iso: string): string {
  const diff = Math.max(0, Date.now() - new Date(iso).getTime());
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  return `${days}일 전`;
}
