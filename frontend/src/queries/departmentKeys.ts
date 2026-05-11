/**
 * Department / Team 관련 TanStack Query key factory.
 *
 * 외부 조직(경쟁사/고객사) 용 `organizationKeys` 와는 다른 네임스페이스다.
 * 공개 트리와 관리자 트리를 별개로 캐싱해 권한 토글 시 혼선을 막는다.
 */
export const departmentKeys = {
  all: ["departments"] as const,
  /** 공개 signup / 프로필 편집용 — 활성 부서+팀 최소 필드. */
  tree: () => [...departmentKeys.all, "tree"] as const,
  /** 관리자 전용 — 비활성 포함 전체 트리. */
  adminTree: () => [...departmentKeys.all, "admin-tree"] as const,
};
