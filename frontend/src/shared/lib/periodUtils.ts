export type PeriodKey = "this-week" | "last-week" | "this-month" | "last-month";

/** ISO 날짜 문자열 "YYYY-MM-DD" 변환 */
function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** 해당 날짜가 속한 주의 월요일(ISO 8601) 반환 */
function getMonday(d: Date): Date {
  const result = new Date(d);
  const dayOfWeek = result.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat
  // 일요일(0)이면 6일 전, 그 외는 (dayOfWeek - 1)일 전
  const diff = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
  result.setDate(result.getDate() - diff);
  return result;
}

/** 기간 키에 해당하는 from/to ISO date 문자열 반환 */
export function getPeriodRange(period: PeriodKey, now: Date = new Date()): { from: string; to: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  switch (period) {
    case "this-week": {
      // 이번주 월요일 ~ 오늘
      const monday = getMonday(today);
      return { from: toISODate(monday), to: toISODate(today) };
    }
    case "last-week": {
      // 지난주 월요일 ~ 지난주 일요일
      const thisMonday = getMonday(today);
      const lastSunday = new Date(thisMonday);
      lastSunday.setDate(thisMonday.getDate() - 1);
      const lastMonday = new Date(thisMonday);
      lastMonday.setDate(thisMonday.getDate() - 7);
      return { from: toISODate(lastMonday), to: toISODate(lastSunday) };
    }
    case "this-month": {
      // 이번달 1일 ~ 오늘
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
      return { from: toISODate(firstDay), to: toISODate(today) };
    }
    case "last-month": {
      // 지난달 1일 ~ 지난달 말일
      const lastMonthFirst = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastMonthLast = new Date(today.getFullYear(), today.getMonth(), 0);
      return { from: toISODate(lastMonthFirst), to: toISODate(lastMonthLast) };
    }
  }
}

/** 비교 기간 계산 (동일 길이의 바로 이전 기간) */
export function getPreviousPeriodRange(period: PeriodKey, now: Date = new Date()): { from: string; to: string } {
  switch (period) {
    case "this-week": {
      // 지난주 동일 요일 범위 (동일 길이)
      const current = getPeriodRange("this-week", now);
      const fromDate = new Date(current.from);
      const toDate = new Date(current.to);
      fromDate.setDate(fromDate.getDate() - 7);
      toDate.setDate(toDate.getDate() - 7);
      return { from: toISODate(fromDate), to: toISODate(toDate) };
    }
    case "last-week": {
      // 지지난주 (월~일)
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const thisMonday = getMonday(today);
      const twoWeeksAgoMonday = new Date(thisMonday);
      twoWeeksAgoMonday.setDate(thisMonday.getDate() - 14);
      const twoWeeksAgoSunday = new Date(twoWeeksAgoMonday);
      twoWeeksAgoSunday.setDate(twoWeeksAgoMonday.getDate() + 6);
      return { from: toISODate(twoWeeksAgoMonday), to: toISODate(twoWeeksAgoSunday) };
    }
    case "this-month": {
      // 지난달 동일 날짜 범위 (동일 길이)
      const current = getPeriodRange("this-month", now);
      const currentFrom = new Date(current.from);
      const currentTo = new Date(current.to);
      const prevFrom = new Date(currentFrom.getFullYear(), currentFrom.getMonth() - 1, 1);
      // 동일 날짜, 단 지난달 말일 초과 방지
      const lastDayOfPrevMonth = new Date(currentFrom.getFullYear(), currentFrom.getMonth(), 0).getDate();
      const prevToDay = Math.min(currentTo.getDate(), lastDayOfPrevMonth);
      const prevTo = new Date(prevFrom.getFullYear(), prevFrom.getMonth(), prevToDay);
      return { from: toISODate(prevFrom), to: toISODate(prevTo) };
    }
    case "last-month": {
      // 지지난달 (1일 ~ 말일)
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const twoMonthsAgoFirst = new Date(today.getFullYear(), today.getMonth() - 2, 1);
      const twoMonthsAgoLast = new Date(today.getFullYear(), today.getMonth() - 1, 0);
      return { from: toISODate(twoMonthsAgoFirst), to: toISODate(twoMonthsAgoLast) };
    }
  }
}

/** PeriodKey를 일수(days)로 변환한다 */
export function periodToDays(period: PeriodKey): number {
  const range = getPeriodRange(period);
  const from = new Date(range.from);
  const to = new Date(range.to);
  return Math.ceil((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24)) + 1;
}

/** 날짜 범위를 "3/3 ~ 3/9" 형태로 포맷 */
export function formatPeriodLabel(from: string, to: string): string {
  const f = new Date(from);
  const t = new Date(to);
  const fMonth = f.getMonth() + 1;
  const fDay = f.getDate();
  const tMonth = t.getMonth() + 1;
  const tDay = t.getDate();
  return `${fMonth}/${fDay} ~ ${tMonth}/${tDay}`;
}
