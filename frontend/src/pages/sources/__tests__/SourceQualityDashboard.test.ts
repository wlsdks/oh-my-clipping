import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { Source } from "@/types/source";

/**
 * SourceQualityDashboard의 classifyCompliance 로직을 단위 테스트한다.
 * 컴포넌트 내부 함수이므로 동일한 로직을 추출하여 테스트한다.
 */

function classifyCompliance(sources: Pick<Source, "termsReviewedAt">[]) {
  const now = Date.now();
  const ninetyDays = 90 * 24 * 60 * 60 * 1000;

  let reviewed = 0;
  let expired = 0;
  let never = 0;

  for (const s of sources) {
    if (!s.termsReviewedAt) {
      never += 1;
    } else {
      const reviewedAt = new Date(s.termsReviewedAt).getTime();
      if (now - reviewedAt > ninetyDays) {
        expired += 1;
      } else {
        reviewed += 1;
      }
    }
  }

  return { reviewed, expired, never };
}

function makeSource(overrides: Partial<Source> = {}): Source {
  return {
    id: "1",
    name: "Test",
    url: "http://test.com",
    sourceRegion: "DOMESTIC",
    isActive: true,
    crawlApproved: true,
    legalBasis: "QUOTATION_ONLY",
    summaryAllowed: true,
    fulltextAllowed: false,
    verificationStatus: "VERIFIED",
    reliabilityScore: 80,
    crawlFailCount: 0,
    curated: false,
    categoryId: "cat-1",
    createdAt: "2026-03-10T00:00:00Z",
    updatedAt: "2026-03-18T00:00:00Z",
    ...overrides,
  };
}

describe("classifyCompliance", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-04-16T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("termsReviewedAt이 null이면 미검토로 분류한다", () => {
    const result = classifyCompliance([
      makeSource({ termsReviewedAt: null }),
      makeSource({ termsReviewedAt: undefined }),
    ]);

    expect(result.never).toBe(2);
    expect(result.reviewed).toBe(0);
    expect(result.expired).toBe(0);
  });

  it("90일 이내 검토된 소스는 검토 완료로 분류한다", () => {
    const result = classifyCompliance([
      makeSource({ termsReviewedAt: "2026-03-01T00:00:00Z" }),
      makeSource({ termsReviewedAt: "2026-04-10T00:00:00Z" }),
    ]);

    expect(result.reviewed).toBe(2);
    expect(result.expired).toBe(0);
    expect(result.never).toBe(0);
  });

  it("90일 초과 검토된 소스는 기한 만료로 분류한다", () => {
    const result = classifyCompliance([
      makeSource({ termsReviewedAt: "2025-12-01T00:00:00Z" }),
    ]);

    expect(result.expired).toBe(1);
    expect(result.reviewed).toBe(0);
  });

  it("혼합된 상태를 올바르게 분류한다", () => {
    const result = classifyCompliance([
      makeSource({ termsReviewedAt: "2026-04-10T00:00:00Z" }), // 검토 완료
      makeSource({ termsReviewedAt: "2025-01-01T00:00:00Z" }), // 만료
      makeSource({ termsReviewedAt: null }),                     // 미검토
    ]);

    expect(result.reviewed).toBe(1);
    expect(result.expired).toBe(1);
    expect(result.never).toBe(1);
  });

  it("빈 배열이면 모두 0이다", () => {
    const result = classifyCompliance([]);

    expect(result.reviewed).toBe(0);
    expect(result.expired).toBe(0);
    expect(result.never).toBe(0);
  });
});
