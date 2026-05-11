import type { EditingResourceType } from "@/types/editingPresence";

/**
 * 편집 presence TanStack Query 쿼리 키.
 *
 * `listActive` 응답만 캐싱하고, heartbeat/release 는 mutation 이므로 키를 두지 않는다.
 */
export const editingPresenceKeys = {
  all: ["editing-presence"] as const,
  active: (resourceType: EditingResourceType, resourceId: string) =>
    [...editingPresenceKeys.all, "active", resourceType, resourceId] as const
};
