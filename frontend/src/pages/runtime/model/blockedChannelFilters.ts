import { matchesKoreanSearch } from "@/utils/search";
import type { BlockedSlackChannel } from "@/types/runtime";

export type BlockedChannelTypeFilter = "all" | "public" | "private";
export type BlockedChannelSort = "recent" | "oldest";

export interface BlockedChannelFilters {
  search: string;
  typeFilter: BlockedChannelTypeFilter;
  sort: BlockedChannelSort;
}

/** 차단 채널 목록에 검색/필터/정렬을 적용한다. */
export function applyBlockedChannelFilters(
  channels: readonly BlockedSlackChannel[],
  filters: BlockedChannelFilters,
): BlockedSlackChannel[] {
  const filtered = channels.filter((ch) => {
    // 타입 필터
    if (filters.typeFilter === "public" && ch.isPrivate) return false;
    if (filters.typeFilter === "private" && !ch.isPrivate) return false;
    // 검색: 채널명 + 사유 모두 대상
    if (filters.search.trim()) {
      const name = ch.channelName || "";
      const reason = ch.reason ?? "";
      if (!matchesKoreanSearch(name, filters.search) && !matchesKoreanSearch(reason, filters.search)) {
        return false;
      }
    }
    return true;
  });

  // 정렬
  return filtered.sort((a, b) => {
    const diff = new Date(a.blockedAt).getTime() - new Date(b.blockedAt).getTime();
    return filters.sort === "recent" ? -diff : diff;
  });
}

/** 차단된 지 얼마나 됐는지 일수 단위로 계산한다. */
export function daysSinceBlocked(blockedAt: string, now: Date = new Date()): number {
  const diff = now.getTime() - new Date(blockedAt).getTime();
  return Math.floor(diff / 86_400_000);
}

/** 오래된(7일 이상) 차단인지 판단한다. 해제 시 확인 다이얼로그 여부에 사용. */
export function isOldBlock(blockedAt: string, now: Date = new Date()): boolean {
  return daysSinceBlocked(blockedAt, now) >= 7;
}
