import { describe, it, expect } from "vitest";
import {
  applyBlockedChannelFilters,
  daysSinceBlocked,
  isOldBlock,
} from "../blockedChannelFilters";
import type { BlockedSlackChannel } from "@/types/runtime";

function makeChannel(overrides: Partial<BlockedSlackChannel> = {}): BlockedSlackChannel {
  return {
    id: "1",
    channelId: "C1",
    channelName: "general",
    isPrivate: false,
    blockedByUserId: "admin",
    blockedAt: "2026-04-01T00:00:00Z",
    reason: null,
    ...overrides,
  };
}

describe("applyBlockedChannelFilters", () => {
  const channels: BlockedSlackChannel[] = [
    makeChannel({ id: "1", channelName: "spam-alerts", isPrivate: false, blockedAt: "2026-04-05T00:00:00Z" }),
    makeChannel({ id: "2", channelName: "private-test", isPrivate: true, blockedAt: "2026-04-03T00:00:00Z", reason: "테스트 전용" }),
    makeChannel({ id: "3", channelName: "marketing", isPrivate: false, blockedAt: "2026-04-01T00:00:00Z" }),
  ];

  it("검색어가 있으면 채널명으로 필터링한다", () => {
    const result = applyBlockedChannelFilters(channels, { search: "spam", typeFilter: "all", sort: "recent" });
    expect(result).toHaveLength(1);
    expect(result[0].channelName).toBe("spam-alerts");
  });

  it("검색어가 사유도 매칭한다", () => {
    const result = applyBlockedChannelFilters(channels, { search: "테스트", typeFilter: "all", sort: "recent" });
    expect(result).toHaveLength(1);
    expect(result[0].channelName).toBe("private-test");
  });

  it("typeFilter=public은 공개 채널만 반환한다", () => {
    const result = applyBlockedChannelFilters(channels, { search: "", typeFilter: "public", sort: "recent" });
    expect(result.every((c) => !c.isPrivate)).toBe(true);
    expect(result).toHaveLength(2);
  });

  it("typeFilter=private은 비공개 채널만 반환한다", () => {
    const result = applyBlockedChannelFilters(channels, { search: "", typeFilter: "private", sort: "recent" });
    expect(result.every((c) => c.isPrivate)).toBe(true);
    expect(result).toHaveLength(1);
  });

  it("sort=recent는 최신순 정렬", () => {
    const result = applyBlockedChannelFilters(channels, { search: "", typeFilter: "all", sort: "recent" });
    expect(result.map((c) => c.id)).toEqual(["1", "2", "3"]);
  });

  it("sort=oldest는 오래된순 정렬", () => {
    const result = applyBlockedChannelFilters(channels, { search: "", typeFilter: "all", sort: "oldest" });
    expect(result.map((c) => c.id)).toEqual(["3", "2", "1"]);
  });

  it("빈 검색어는 모두 통과", () => {
    const result = applyBlockedChannelFilters(channels, { search: "   ", typeFilter: "all", sort: "recent" });
    expect(result).toHaveLength(3);
  });
});

describe("daysSinceBlocked", () => {
  const now = new Date("2026-04-09T00:00:00Z");

  it("같은 날이면 0", () => {
    expect(daysSinceBlocked("2026-04-09T00:00:00Z", now)).toBe(0);
  });

  it("하루 전이면 1", () => {
    expect(daysSinceBlocked("2026-04-08T00:00:00Z", now)).toBe(1);
  });

  it("10일 전이면 10", () => {
    expect(daysSinceBlocked("2026-03-30T00:00:00Z", now)).toBe(10);
  });
});

describe("isOldBlock", () => {
  const now = new Date("2026-04-09T00:00:00Z");

  it("6일 전은 오래된 것이 아님", () => {
    expect(isOldBlock("2026-04-03T00:00:00Z", now)).toBe(false);
  });

  it("7일 전은 오래된 것", () => {
    expect(isOldBlock("2026-04-02T00:00:00Z", now)).toBe(true);
  });

  it("30일 전은 오래된 것", () => {
    expect(isOldBlock("2026-03-10T00:00:00Z", now)).toBe(true);
  });
});
