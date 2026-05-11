import type { RevisionResource } from "@/types/revisionHistory";

/**
 * 엔티티 변경 이력 쿼리 키 팩토리.
 *
 * `byResource(resource, id)`는 특정 리소스의 최근 revision 목록을 식별한다.
 * 되돌리기 후에는 `byResource`와 해당 리소스의 detail 키를 모두 invalidate한다.
 */
export const historyKeys = {
  all: ["history"] as const,
  byResource: (resource: RevisionResource, id: string) =>
    [...historyKeys.all, resource, id] as const
};
