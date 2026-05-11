import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { getSourceSubText, getStatusKey, getIconBg, getHealthLevel } from "../sourceHelpers";
import type { Source } from "@/types/source";

function makeSource(overrides: Partial<Source> = {}): Source {
  return {
    id: "1", name: "Test", url: "http://test.com", sourceRegion: "DOMESTIC",
    isActive: true, crawlApproved: true, legalBasis: "fair-use",
    summaryAllowed: true, fulltextAllowed: false, verificationStatus: "VERIFIED",
    reliabilityScore: 95, crawlFailCount: 0, curated: false, categoryId: "cat-1",
    createdAt: "2026-03-10T00:00:00Z", updatedAt: "2026-03-18T00:00:00Z",
    ...overrides,
  };
}

describe("getHealthLevel", () => {
  it("returns 'healthy' for active source with successful crawl", () => {
    expect(getHealthLevel(makeSource({ lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("healthy");
  });
  it("returns 'warning' for crawlFailCount 1-9", () => {
    expect(getHealthLevel(makeSource({ crawlFailCount: 1, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("warning");
    expect(getHealthLevel(makeSource({ crawlFailCount: 9, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("warning");
  });
  it("returns 'error' for crawlFailCount >= 10", () => {
    expect(getHealthLevel(makeSource({ crawlFailCount: 10, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("error");
    expect(getHealthLevel(makeSource({ crawlFailCount: 50, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("error");
  });
  it("returns 'pending' for unapproved source", () => {
    expect(getHealthLevel(makeSource({ crawlApproved: false }))).toBe("pending");
  });
  it("returns 'pending' for active source without lastSuccessAt", () => {
    expect(getHealthLevel(makeSource({ lastSuccessAt: null }))).toBe("pending");
  });
  it("returns 'archived' for approved but inactive source", () => {
    expect(getHealthLevel(makeSource({ crawlApproved: true, isActive: false }))).toBe("archived");
  });
  it("returns 'pending' for unapproved and inactive source", () => {
    expect(getHealthLevel(makeSource({ crawlApproved: false, isActive: false }))).toBe("pending");
  });
});

describe("getStatusKey", () => {
  it("returns 'active' when crawlFailCount 1-9 (warning level)", () => {
    expect(getStatusKey(makeSource({ crawlFailCount: 3, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("active");
  });
  it("returns 'error' when crawlFailCount >= 10", () => {
    expect(getStatusKey(makeSource({ crawlFailCount: 10, lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("error");
  });
  it("returns 'error' when verificationStatus is FAILED", () => {
    expect(getStatusKey(makeSource({ verificationStatus: "FAILED" }))).toBe("error");
  });
  it("returns 'unapproved' when not crawlApproved", () => {
    expect(getStatusKey(makeSource({ crawlApproved: false }))).toBe("unapproved");
  });
  it("returns 'inactive' when approved but not active", () => {
    expect(getStatusKey(makeSource({ isActive: false }))).toBe("inactive");
  });
  it("returns 'active' for healthy source", () => {
    expect(getStatusKey(makeSource({ lastSuccessAt: "2026-03-18T00:00:00Z" }))).toBe("active");
  });
  it("FAILED verification takes priority over warning-level crawl failures", () => {
    expect(getStatusKey(makeSource({ crawlFailCount: 1, verificationStatus: "FAILED" }))).toBe("error");
  });
});

describe("getSourceSubText", () => {
  beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date("2026-03-19T12:00:00Z")); });
  afterEach(() => vi.useRealTimers());

  it("returns error text for crawlFailCount >= 10", () => {
    const s = makeSource({ crawlFailCount: 10, updatedAt: "2026-03-19T10:00:00Z", lastSuccessAt: "2026-03-18T00:00:00Z" });
    expect(getSourceSubText(s)).toBe("연결 실패 (10회) · 2시간 전 수정");
  });
  it("returns warning text for crawlFailCount 1-9", () => {
    const s = makeSource({ crawlFailCount: 3, updatedAt: "2026-03-19T10:00:00Z", lastSuccessAt: "2026-03-18T00:00:00Z" });
    expect(getSourceSubText(s)).toBe("주의 (실패 3회) · 2시간 전 수정");
  });
  it("returns pending text for unapproved sources", () => {
    const s = makeSource({ crawlApproved: false, createdAt: "2026-03-18T12:00:00Z" });
    expect(getSourceSubText(s)).toBe("승인 대기 중 · 1일 전 등록");
  });
  it("returns last success time for healthy sources", () => {
    const s = makeSource({ lastSuccessAt: "2026-03-19T08:00:00Z", updatedAt: "2026-03-17T12:00:00Z" });
    expect(getSourceSubText(s)).toBe("마지막 수집 4시간 전 · 2일 전 수정");
  });
  it("returns pending text for active sources without collection history", () => {
    const s = makeSource({ lastSuccessAt: null, updatedAt: "2026-03-17T12:00:00Z" });
    expect(getSourceSubText(s)).toBe("수집 대기중 · 2일 전 수정");
  });
  it("returns archived text for inactive source", () => {
    const s = makeSource({ isActive: false, updatedAt: "2026-03-05T12:00:00Z" });
    expect(getSourceSubText(s)).toBe("비활성 · 14일 전 수정");
  });
});

describe("getIconBg", () => {
  it("returns correct bg for each status", () => {
    expect(getIconBg("error")).toBe("bg-[var(--status-danger-bg)]");
    expect(getIconBg("unapproved")).toBe("bg-[var(--status-warning-bg)]");
    expect(getIconBg("active")).toBe("bg-[var(--status-neutral-bg)]");
    expect(getIconBg("inactive")).toBe("bg-muted");
  });
});
