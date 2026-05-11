import AxeBuilder from "@axe-core/playwright";
import { Page, expect } from "@playwright/test";

/**
 * axe-core를 사용하여 WCAG 2.0/2.1 A/AA 수준의 접근성 위반을 검사한다.
 *
 * @param page - Playwright Page 객체
 * @param context - 에러 메시지에 포함할 컨텍스트 설명 (선택)
 */
export async function checkA11y(page: Page, context?: string) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa"])
    .analyze();

  expect(
    results.violations,
    `A11y violations${context ? ` on ${context}` : ""}: ${results.violations
      .map((v) => `${v.id}: ${v.description} (${v.nodes.length} nodes)`)
      .join(", ")}`
  ).toEqual([]);
}
