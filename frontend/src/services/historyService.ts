import { api } from "@/lib/kyInstance";
import type { RevisionSummary, RevisionResource } from "@/types/revisionHistory";

/**
 * 4개 리소스 타입별 admin path segment 매핑.
 * AdminHistoryController의 path와 동일해야 한다.
 */
const RESOURCE_PATH: Record<RevisionResource, string> = {
  persona: "personas",
  category: "categories",
  category_rule: "category-rules",
  rss_source: "sources"
};

export interface RestoreRequest {
  revisionId: string;
  /** 현재 엔티티의 updatedAt — 낙관적 잠금 필수. */
  expectedUpdatedAt: string;
}

export const historyService = {
  /**
   * 특정 리소스의 최근 revision 목록을 반환한다. 서버 기본 limit=20, 최대 100.
   */
  getHistory: (resource: RevisionResource, id: string, limit = 20): Promise<RevisionSummary[]> =>
    api
      .get(`admin/${RESOURCE_PATH[resource]}/${encodeURIComponent(id)}/history`, {
        searchParams: { limit }
      })
      .json(),

  /**
   * 특정 revision snapshot으로 리소스를 복원한다. 서버가 새 revision을 append하고 복원된 엔티티를 반환한다.
   */
  restore: <T>(resource: RevisionResource, id: string, request: RestoreRequest): Promise<T> =>
    api
      .post(`admin/${RESOURCE_PATH[resource]}/${encodeURIComponent(id)}/restore`, { json: request })
      .json()
};
