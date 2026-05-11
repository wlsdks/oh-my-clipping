/** 밀리초 → 사람이 읽기 쉬운 한국어 표기 */
export function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(1)}초`;
}
