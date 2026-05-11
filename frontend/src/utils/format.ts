// src/utils/format.ts

export function toInt(value: unknown, fallback: number): number {
  const parsed = Number.parseInt(String(value), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function toFloat(value: unknown, fallback: number): number {
  const parsed = Number.parseFloat(String(value));
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function currentYearMonth(): string {
  return new Date().toISOString().slice(0, 7);
}

export function normalizeLegacyBundleTerm(text: string): string {
  return text.replace(/요약본/g, "뉴스 모음");
}
