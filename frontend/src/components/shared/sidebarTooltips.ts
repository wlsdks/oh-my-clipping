export type BadgeKind =
  | "userAccounts"
  | "reviewQueue"
  | "subscriptions"
  | "delivery"
  | "pipeline"
  | "sources";

/**
 * 사이드바 배지의 hover 툴팁 텍스트를 생성한다.
 * - 대기 항목(userAccounts, reviewQueue, subscriptions): "현재 … N건 · 최장 {urgency} 대기"
 * - 실패 항목(delivery, pipeline): "최근 24h … N건 · 최초 실패 {urgency} 전"
 * - oldestCreatedAt 이 null 이면 " · " 이후 urgency 부분은 생략한다.
 */
export function formatBadgeTooltip(
  kind: BadgeKind,
  count: number,
  oldestCreatedAt: string | null,
  now: Date,
): string {
  const urgency = oldestCreatedAt ? relativeDurationShort(new Date(oldestCreatedAt), now) : "";

  switch (kind) {
    case "userAccounts":
      return urgency
        ? `현재 승인 대기 ${count}건 · 최장 ${urgency} 대기`
        : `현재 승인 대기 ${count}건`;
    case "reviewQueue":
      return urgency
        ? `현재 판단 대기 뉴스 ${count}건 · 최장 ${urgency} 대기`
        : `현재 판단 대기 뉴스 ${count}건`;
    case "subscriptions":
      return urgency
        ? `현재 구독 요청 대기 ${count}건 · 최장 ${urgency} 대기`
        : `현재 구독 요청 대기 ${count}건`;
    case "delivery":
      return urgency
        ? `최근 24h 발송 실패 ${count}건 · 최초 실패 ${urgency} 전`
        : `최근 24h 발송 실패 ${count}건`;
    case "pipeline":
      return urgency
        ? `최근 24h 파이프라인 실패 ${count}건 · 최초 실패 ${urgency} 전`
        : `최근 24h 파이프라인 실패 ${count}건`;
    case "sources":
      return `저작권 재검토 필요 ${count}건`;
  }
}

function relativeDurationShort(from: Date, now: Date): string {
  // 서버-클라이언트 시계 skew 로 음수가 나올 수 있어 0으로 클램프
  const diffMinutes = Math.max(0, Math.floor((now.getTime() - from.getTime()) / 60_000));
  if (diffMinutes < 1) return "방금";
  if (diffMinutes < 60) return `${diffMinutes}분`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}일`;
}
