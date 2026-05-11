/** 차단 사유 최대 길이 (UI 카운터와 slice에 사용) */
export const REASON_MAX = 200;

/** 차단된 지 며칠 이상이면 해제 시 확인 다이얼로그를 띄우는지 */
export const OLD_BLOCK_DAYS = 7;

/** 추가 드롭다운의 채널 타입 */
export type ChannelType = "public_channel" | "private_channel";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** UUID 형태의 blockedByUserId는 사용자명 대신 "관리자"로 표시한다. */
export function formatBlockedBy(value: string): string {
  if (!value) return "관리자";
  return UUID_RE.test(value) ? "관리자" : value;
}
