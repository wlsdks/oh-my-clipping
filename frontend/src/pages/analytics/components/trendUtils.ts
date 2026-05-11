/**
 * 퍼센트 변화율 계산.
 * previous가 0이면 current > 0일 때 100, 아니면 0 반환.
 */
export function pct(current: number, previous: number): number {
  if (previous === 0) return current > 0 ? 100 : 0;
  return Math.round(((current - previous) / previous) * 100);
}

/**
 * 증감 방향 판별.
 */
export function trendDir(current: number, previous: number): "up" | "down" | "neutral" {
  if (current > previous) return "up";
  if (current < previous) return "down";
  return "neutral";
}
