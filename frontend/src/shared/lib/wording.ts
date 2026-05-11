export function normalizeLegacyBundleTerm(text: string): string {
  return text.replace(/요약본/g, "뉴스 모음");
}
