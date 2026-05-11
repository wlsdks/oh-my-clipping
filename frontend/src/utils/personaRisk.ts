/**
 * Persona risk / growth signal helpers — pure utilities (no React, no I/O).
 *
 * Spec reference: docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md §2, §5.4.
 *
 * All helpers are deliberately small and side-effect-free so they can be
 * unit-tested without DOM / time mocking tricks (except `isInProgressWeek`
 * which accepts an injectable `now`).
 */

/**
 * Returns true when the non-null values in `values` span at least `minDelta`
 * between their min and max.
 *
 * - All-null / empty / single non-null returns false.
 * - Negatives are fine — the comparison is on max - min.
 */
export function hasVariation(
  values: Array<number | null>,
  minDelta = 1,
): boolean {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  let count = 0;
  for (const v of values) {
    if (v === null || v === undefined) continue;
    if (v < min) min = v;
    if (v > max) max = v;
    count += 1;
  }
  if (count < 2) return false;
  return max - min >= minDelta;
}

/**
 * Returns today's Y / M / D parts in the `Asia/Seoul` timezone.
 * Uses `Intl.DateTimeFormat` (`en-CA`) so the formatted string is the
 * canonical `YYYY-MM-DD` regardless of locale.
 */
function kstDateParts(now: Date): { year: number; month: number; day: number } {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = formatter.formatToParts(now);
  const map: Record<string, string> = {};
  for (const p of parts) {
    if (p.type !== "literal") map[p.type] = p.value;
  }
  return {
    year: Number(map.year),
    month: Number(map.month),
    day: Number(map.day),
  };
}

/**
 * ISO 8601 week number of `(year, month, day)` (1-based, Monday-starting).
 * Returns `{ year: isoYear, week }` where `isoYear` may differ from the input
 * year around January / December.
 */
function isoWeekOf(
  year: number,
  month: number,
  day: number,
): { year: number; week: number } {
  // Build UTC date (YMD is TZ-independent once we already have KST parts).
  const d = new Date(Date.UTC(year, month - 1, day));
  // ISO 8601: Monday = 1, Sunday = 7.
  const dayNum = d.getUTCDay() || 7;
  // Shift to the Thursday of the current ISO week — that Thursday's year is
  // the ISO year, and its Jan 4th anchor gives us week 1.
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const isoYear = d.getUTCFullYear();
  const firstThursday = new Date(Date.UTC(isoYear, 0, 4));
  const firstThursdayDay = firstThursday.getUTCDay() || 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() + 4 - firstThursdayDay);
  const week = 1 +
    Math.round((d.getTime() - firstThursday.getTime()) / (7 * 24 * 3_600_000));
  return { year: isoYear, week };
}

/**
 * Parse a string of the form `"YYYY-Www"` into numeric year/week.
 * Returns null if the string doesn't match the expected format.
 */
function parseIsoWeek(weekIso: string): { year: number; week: number } | null {
  const match = /^(\d{4})-W(\d{1,2})$/.exec(weekIso);
  if (!match) return null;
  return { year: Number(match[1]), week: Number(match[2]) };
}

/**
 * Format a parsed `{year, week}` back to `YYYY-Www` with zero-padded week.
 */
function formatIsoWeek(year: number, week: number): string {
  return `${year}-W${String(week).padStart(2, "0")}`;
}

/**
 * Returns true when `weekIso` (e.g. `"2026-W16"`) contains "today"
 * in the `Asia/Seoul` timezone.
 *
 * Use the second argument to inject a deterministic `Date` from tests.
 */
export function isInProgressWeek(weekIso: string, now: Date = new Date()): boolean {
  const parsed = parseIsoWeek(weekIso);
  if (!parsed) return false;
  const today = kstDateParts(now);
  const current = isoWeekOf(today.year, today.month, today.day);
  return current.year === parsed.year && current.week === parsed.week;
}

/**
 * Produce the canonical dismiss-key shape used in localStorage.
 */
export function dismissKey(
  personaId: string,
  signalType: string,
  weekIso: string,
): string {
  return `${personaId}:${signalType}:${weekIso}`;
}

/**
 * Return the subset of `keys` whose week portion is within 4 ISO weeks of
 * `currentWeekIso` (inclusive — keys at the current week are kept).
 *
 * Weeks are compared numerically. Older keys are dropped.
 * Keys that don't match the `id:type:YYYY-Www` shape are kept as-is (callers
 * may have stored ad-hoc values; we don't want to silently lose them).
 */
export function pruneDismissKeys(
  keys: string[],
  currentWeekIso: string,
): string[] {
  const currentParsed = parseIsoWeek(currentWeekIso);
  if (!currentParsed) return [...keys];

  const currentOrdinal = currentParsed.year * 53 + currentParsed.week;
  const kept: string[] = [];
  for (const key of keys) {
    const parts = key.split(":");
    if (parts.length < 3) {
      kept.push(key);
      continue;
    }
    const keyParsed = parseIsoWeek(parts[2]);
    if (!keyParsed) {
      kept.push(key);
      continue;
    }
    const keyOrdinal = keyParsed.year * 53 + keyParsed.week;
    // Keep keys at the current week or within the last 4 weeks (inclusive).
    if (currentOrdinal - keyOrdinal <= 4) {
      kept.push(key);
    }
  }
  return kept;
}

/**
 * Sort a list of signal items by `persistentWeeks` ascending (newest first)
 * with ties broken by a secondary absolute magnitude — larger first.
 *
 * Returns a new array; does not mutate the input.
 */
export function sortByPersistentWeeksAsc<T extends { persistentWeeks: number }>(
  items: T[],
  secondaryAbs: (item: T) => number,
): T[] {
  return [...items].sort((a, b) => {
    if (a.persistentWeeks !== b.persistentWeeks) {
      return a.persistentWeeks - b.persistentWeeks;
    }
    return Math.abs(secondaryAbs(b)) - Math.abs(secondaryAbs(a));
  });
}

// Internal helpers are intentionally not exported — keep public surface minimal.
export const __internal = { kstDateParts, isoWeekOf, parseIsoWeek, formatIsoWeek };
