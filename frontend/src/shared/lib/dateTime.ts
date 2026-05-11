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
  const partByType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${partByType.year}-${partByType.month}-${partByType.day} ${partByType.hour}:${partByType.minute}:${partByType.second}`;
}

/** 날짜만 표시 (시간 없이) — 사용자 화면용 */
export function formatKoreanDate(value?: string | null): string {
  if (!value) return "-";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const parts = KOREA_DATE_FORMAT.formatToParts(date);
  const partByType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${partByType.year}. ${partByType.month}. ${partByType.day}`;
}
