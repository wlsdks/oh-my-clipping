import { api } from "@/lib/kyInstance";
import type {
  EditingResourceType,
  EditingSession
} from "@/types/editingPresence";

/**
 * 편집 presence API 래퍼. 백엔드 `/api/admin/editing-sessions` 세 엔드포인트에 1:1 매핑된다.
 *
 * - `heartbeat`: 편집 모달이 열려 있는 동안 30초 주기로 호출
 * - `release`: 모달 닫힘/언마운트 시 즉시 호출
 * - `listActive`: 다른 관리자가 편집 중인지 주기적으로 조회
 *
 * 모든 호출은 실패해도 `editing presence 는 UX 보조 기능이므로 UI 에서 무시한다` 는 정책에 따라
 * 호출자가 try/catch 또는 TanStack Query 의 retry=false 로 방어한다.
 */
export const editingPresenceService = {
  heartbeat: (resourceType: EditingResourceType, resourceId: string) =>
    api
      .post("admin/editing-sessions/heartbeat", {
        json: { resourceType, resourceId }
      })
      .text(),
  release: (resourceType: EditingResourceType, resourceId: string) =>
    api
      .delete("admin/editing-sessions", {
        json: { resourceType, resourceId }
      })
      .text(),
  listActive: (resourceType: EditingResourceType, resourceId: string) =>
    api
      .get("admin/editing-sessions", {
        searchParams: { resourceType, resourceId }
      })
      .json<EditingSession[]>()
};
