import { describe, expect, it } from "vitest";
import { formatBadgeTooltip } from "../sidebarTooltips";

const now = new Date("2026-04-18T12:00:00Z");

describe("formatBadgeTooltip", () => {
  describe("대기 항목 (대기 표현)", () => {
    it("회원관리: 현재 승인 대기 N건 · 최장 N 대기", () => {
      expect(formatBadgeTooltip("userAccounts", 3, "2026-04-16T12:00:00Z", now))
        .toBe("현재 승인 대기 3건 · 최장 2일 대기");
    });
    it("뉴스 검토: 판단 대기 뉴스", () => {
      expect(formatBadgeTooltip("reviewQueue", 5, "2026-04-17T18:00:00Z", now))
        .toBe("현재 판단 대기 뉴스 5건 · 최장 18시간 대기");
    });
    it("구독 요청: 대기 표현", () => {
      expect(formatBadgeTooltip("subscriptions", 2, "2026-04-18T09:00:00Z", now))
        .toBe("현재 구독 요청 대기 2건 · 최장 3시간 대기");
    });
  });

  describe("실패 항목 (최초 실패 전 표현)", () => {
    it("발송 실패: 최근 24h ... · 최초 실패 N 전", () => {
      expect(formatBadgeTooltip("delivery", 2, "2026-04-18T00:00:00Z", now))
        .toBe("최근 24h 발송 실패 2건 · 최초 실패 12시간 전");
    });
    it("파이프라인 실패: 동일", () => {
      expect(formatBadgeTooltip("pipeline", 1, "2026-04-18T11:30:00Z", now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 30분 전");
    });
  });

  describe("urgency 없는 경우 (oldestCreatedAt null)", () => {
    it("대기 항목은 · 이후 부분 생략", () => {
      expect(formatBadgeTooltip("userAccounts", 3, null, now))
        .toBe("현재 승인 대기 3건");
    });
    it("실패 항목도 생략", () => {
      expect(formatBadgeTooltip("delivery", 2, null, now))
        .toBe("최근 24h 발송 실패 2건");
    });
  });

  describe("상대 시간 포맷 경계", () => {
    it("30분 → 30분", () => {
      expect(formatBadgeTooltip("pipeline", 1, "2026-04-18T11:30:00Z", now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 30분 전");
    });
    it("3시간 → 3시간", () => {
      expect(formatBadgeTooltip("pipeline", 1, "2026-04-18T09:00:00Z", now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 3시간 전");
    });
    it("2일 → 2일", () => {
      expect(formatBadgeTooltip("userAccounts", 1, "2026-04-16T12:00:00Z", now))
        .toBe("현재 승인 대기 1건 · 최장 2일 대기");
    });
    it("59분 → 59분 (시간 경계 미만)", () => {
      // now - 59분
      const oldest = new Date("2026-04-18T11:01:00Z").toISOString();
      expect(formatBadgeTooltip("pipeline", 1, oldest, now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 59분 전");
    });

    it("정확히 60분 → 1시간", () => {
      const oldest = new Date("2026-04-18T11:00:00Z").toISOString();
      expect(formatBadgeTooltip("pipeline", 1, oldest, now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 1시간 전");
    });

    it("정확히 24시간 → 1일", () => {
      const oldest = new Date("2026-04-17T12:00:00Z").toISOString();
      expect(formatBadgeTooltip("userAccounts", 1, oldest, now))
        .toBe("현재 승인 대기 1건 · 최장 1일 대기");
    });
  });

  describe("clock skew 및 near-zero 방어", () => {
    it("동일 시각 또는 미래 시각 → '방금'", () => {
      expect(formatBadgeTooltip("pipeline", 1, "2026-04-18T12:00:00Z", now))
        .toBe("최근 24h 파이프라인 실패 1건 · 최초 실패 방금 전");
      expect(formatBadgeTooltip("userAccounts", 1, "2026-04-18T12:30:00Z", now))
        .toBe("현재 승인 대기 1건 · 최장 방금 대기");
    });

    it("count=0 도 '건' 표시 (배지 제거는 호출부 책임)", () => {
      expect(formatBadgeTooltip("userAccounts", 0, null, now))
        .toBe("현재 승인 대기 0건");
    });
  });
});
