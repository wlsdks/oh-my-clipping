// src/utils/date.ts

const KOREA_DATETIME_FORMAT = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false
});

const KOREA_DATE_FORMAT = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});

export function formatKoreanDateTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const parts = KOREA_DATETIME_FORMAT.formatToParts(date);
  const p = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`;
}

export function relativeTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return "방금 전";
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 30) return `${diffDay}일 전`;
  const diffMonth = Math.floor(diffDay / 30);
  return `${diffMonth}개월 전`;
}

export function formatRelativeDate(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";

  // 한국 시간 기준으로 날짜만 비교하여 "오늘/어제/N일 전" 계산
  const koreaDate = (d: Date) => {
    const s = d.toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" });
    return new Date(s + "T00:00:00");
  };
  const todayKst = koreaDate(new Date());
  const dateKst = koreaDate(date);
  const diffDays = Math.round((todayKst.getTime() - dateKst.getTime()) / (1000 * 60 * 60 * 24));

  if (diffDays <= 0) return "오늘";
  if (diffDays === 1) return "어제";
  if (diffDays < 7) return `${diffDays}일 전`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)}주 전`;
  if (diffDays < 365) return `${Math.floor(diffDays / 30)}개월 전`;
  return `${Math.floor(diffDays / 365)}년 전`;
}

/** ISO 날짜 문자열을 "M/D" 형식으로 축약한다. 차트 X축 레이블용. */
export function shortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function formatKoreanDate(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const parts = KOREA_DATE_FORMAT.formatToParts(date);
  const p = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${p.year}. ${p.month}. ${p.day}`;
}

/* ────────────────────────────────────────────────────────────────────────
 * 월간 리포트용 yearMonth 유틸 (모두 Asia/Seoul 기준)
 *
 * 백엔드 통계는 KST로 기록되므로 프론트엔드의 "오늘/이번 달" 계산도 같은
 * 타임존으로 맞춘다. 로컬 타임존에 의존하면 자정 근방에서 하루가 어긋난다.
 * ──────────────────────────────────────────────────────────────────────── */

/** Asia/Seoul 기준으로 "YYYY-MM-DD" ISO 형식 문자열을 만든다. */
function kstDateParts(instant = new Date()): { year: number; month: number; day: number } {
  const iso = instant.toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" });
  const [year, month, day] = iso.split("-").map(Number);
  return { year, month, day };
}

/** KST 기준 현재 월 → "YYYY-MM" */
export function currentYearMonthKst(instant = new Date()): string {
  const { year, month } = kstDateParts(instant);
  return `${year}-${String(month).padStart(2, "0")}`;
}

/** "YYYY-MM"의 한 달 전을 문자열로. "2026-01" → "2025-12" */
export function prevYearMonth(ym: string): string {
  const [y, m] = ym.split("-").map(Number);
  const prevY = m === 1 ? y - 1 : y;
  const prevM = m === 1 ? 12 : m - 1;
  return `${prevY}-${String(prevM).padStart(2, "0")}`;
}

/** 최근 N개월 드롭다운 옵션 (현재월 포함). KST 기준. */
export function generatePastMonthsKst(count: number, instant = new Date()): { value: string; label: string }[] {
  const { year, month } = kstDateParts(instant);
  const result: { value: string; label: string }[] = [];
  for (let i = 0; i < count; i++) {
    const totalMonths = month - i - 1;
    const y = year + Math.floor(totalMonths / 12);
    const m = ((totalMonths % 12) + 12) % 12 + 1;
    result.push({
      value: `${y}-${String(m).padStart(2, "0")}`,
      label: `${y}년 ${m}월`
    });
  }
  return result;
}

/**
 * 월 범위를 `{from, to}` (ISO 날짜)로 반환한다. KST 기준.
 * - 이번 달이면 to = 오늘 (KST)
 * - 과거 달이면 to = 월의 마지막 날
 */
export function getMonthRangeKst(ym: string, instant = new Date()): { from: string; to: string } {
  const [y, m] = ym.split("-").map(Number);
  const today = kstDateParts(instant);
  const firstDay = `${ym}-01`;
  const isCurrentMonth = y === today.year && m === today.month;
  if (isCurrentMonth) {
    return { from: firstDay, to: `${ym}-${String(today.day).padStart(2, "0")}` };
  }
  // 과거 달의 마지막 날: JavaScript Date(y, m, 0)은 로컬 타임존이지만 마지막 날짜 계산에는 타임존 영향이 없다 (월만 사용).
  const lastDay = new Date(y, m, 0).getDate();
  return { from: firstDay, to: `${ym}-${String(lastDay).padStart(2, "0")}` };
}
