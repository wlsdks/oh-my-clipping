export type PeriodKey = "today" | "this-week" | "this-month";

export interface PeriodOption {
  key: PeriodKey;
  label: string;
  days: number;
}

/** KST 기준 현재 Date 객체를 반환한다. */
function nowKST(): Date {
  const kstStr = new Date().toLocaleString("en-US", { timeZone: "Asia/Seoul" });
  return new Date(kstStr);
}

function daysSinceMonday(): number {
  const dow = nowKST().getDay();
  return (dow === 0 ? 6 : dow - 1) + 1;
}

function daysSinceFirstOfMonth(): number {
  return nowKST().getDate();
}

export function getCompetitorPeriodOptions(): PeriodOption[] {
  return [
    { key: "today", label: "오늘", days: 1 },
    { key: "this-week", label: "이번 주", days: daysSinceMonday() },
    { key: "this-month", label: "이번 달", days: daysSinceFirstOfMonth() }
  ];
}

export function getPeriodDays(key: PeriodKey): number {
  switch (key) {
    case "today":
      return 1;
    case "this-week":
      return daysSinceMonday();
    case "this-month":
      return daysSinceFirstOfMonth();
  }
}
