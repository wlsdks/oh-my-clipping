export type PeriodKey = "this-week" | "last-week" | "this-month" | "last-month";

/** KST 기준 현재 Date 객체를 반환한다 (연/월/일은 KST 기준). */
function nowKST(): Date {
  const kstStr = new Date().toLocaleString("en-US", { timeZone: "Asia/Seoul" });
  return new Date(kstStr);
}

function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function getMonday(d: Date): Date {
  const result = new Date(d);
  const dayOfWeek = result.getDay();
  const diff = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
  result.setDate(result.getDate() - diff);
  return result;
}

export function getPeriodRange(period: PeriodKey, now: Date = nowKST()): { from: string; to: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  switch (period) {
    case "this-week": {
      const monday = getMonday(today);
      return { from: toISODate(monday), to: toISODate(today) };
    }
    case "last-week": {
      const thisMonday = getMonday(today);
      const lastSunday = new Date(thisMonday);
      lastSunday.setDate(thisMonday.getDate() - 1);
      const lastMonday = new Date(thisMonday);
      lastMonday.setDate(thisMonday.getDate() - 7);
      return { from: toISODate(lastMonday), to: toISODate(lastSunday) };
    }
    case "this-month": {
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
      return { from: toISODate(firstDay), to: toISODate(today) };
    }
    case "last-month": {
      const lastMonthFirst = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastMonthLast = new Date(today.getFullYear(), today.getMonth(), 0);
      return { from: toISODate(lastMonthFirst), to: toISODate(lastMonthLast) };
    }
  }
}

export function getPreviousPeriodRange(period: PeriodKey, now: Date = nowKST()): { from: string; to: string } {
  switch (period) {
    case "this-week": {
      const current = getPeriodRange("this-week", now);
      const fromDate = new Date(current.from);
      const toDate = new Date(current.to);
      fromDate.setDate(fromDate.getDate() - 7);
      toDate.setDate(toDate.getDate() - 7);
      return { from: toISODate(fromDate), to: toISODate(toDate) };
    }
    case "last-week": {
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const thisMonday = getMonday(today);
      const twoWeeksAgoMonday = new Date(thisMonday);
      twoWeeksAgoMonday.setDate(thisMonday.getDate() - 14);
      const twoWeeksAgoSunday = new Date(twoWeeksAgoMonday);
      twoWeeksAgoSunday.setDate(twoWeeksAgoMonday.getDate() + 6);
      return { from: toISODate(twoWeeksAgoMonday), to: toISODate(twoWeeksAgoSunday) };
    }
    case "this-month": {
      const current = getPeriodRange("this-month", now);
      const currentFrom = new Date(current.from);
      const currentTo = new Date(current.to);
      const prevFrom = new Date(currentFrom.getFullYear(), currentFrom.getMonth() - 1, 1);
      const lastDayOfPrevMonth = new Date(currentFrom.getFullYear(), currentFrom.getMonth(), 0).getDate();
      const prevToDay = Math.min(currentTo.getDate(), lastDayOfPrevMonth);
      const prevTo = new Date(prevFrom.getFullYear(), prevFrom.getMonth(), prevToDay);
      return { from: toISODate(prevFrom), to: toISODate(prevTo) };
    }
    case "last-month": {
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const twoMonthsAgoFirst = new Date(today.getFullYear(), today.getMonth() - 2, 1);
      const twoMonthsAgoLast = new Date(today.getFullYear(), today.getMonth() - 1, 0);
      return { from: toISODate(twoMonthsAgoFirst), to: toISODate(twoMonthsAgoLast) };
    }
  }
}

export function periodToDays(period: PeriodKey): number {
  const range = getPeriodRange(period);
  const from = new Date(range.from);
  const to = new Date(range.to);
  return Math.ceil((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24)) + 1;
}

export function formatPeriodLabel(from: string, to: string): string {
  const f = new Date(from);
  const t = new Date(to);
  return `${f.getMonth() + 1}/${f.getDate()} ~ ${t.getMonth() + 1}/${t.getDate()}`;
}
