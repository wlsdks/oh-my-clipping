import { api } from "@/lib/kyInstance";
import type {
  AdminDepartment,
  AdminDepartmentTreeResponse,
  AdminTeam,
  CreateDepartmentRequest,
  CreateTeamRequest,
  PublicDepartmentTreeResponse,
  UpdateDepartmentRequest,
  UpdateTeamRequest,
} from "@/types/department";

/**
 * Department / Team API 클라이언트.
 *
 * 경로 체계:
 * - `public/departments/tree` — 익명 접근 가능 (signup / 프로필 편집)
 * - `admin/departments/**` — 관리자 전용 CRUD
 * - `admin/teams/**` — 관리자 전용 팀 CRUD (부서 이동 포함)
 */
export const departmentService = {
  /** 공개 트리 — 활성 부서 + 활성 팀 최소 필드. */
  getPublicTree: (): Promise<PublicDepartmentTreeResponse> =>
    api.get("public/departments/tree").json(),

  /** 관리자 트리 — 비활성 포함 전체 메타. */
  getAdminTree: (): Promise<AdminDepartmentTreeResponse> =>
    api.get("admin/departments/tree").json(),

  /** 부서 신규 생성. */
  createDepartment: (data: CreateDepartmentRequest): Promise<AdminDepartment> =>
    api.post("admin/departments", { json: data }).json(),

  /** 부서 이름/정렬값 수정. */
  updateDepartment: (
    id: string,
    data: UpdateDepartmentRequest
  ): Promise<AdminDepartment> =>
    api.put(`admin/departments/${encodeURIComponent(id)}`, { json: data }).json(),

  /** 부서 활성/비활성 토글. soft-delete 대체 역할. */
  setDepartmentActive: (id: string, isActive: boolean): Promise<AdminDepartment> =>
    api
      .patch(`admin/departments/${encodeURIComponent(id)}/active`, {
        json: { isActive },
      })
      .json(),

  /** 부서 아래에 팀 신규 생성. */
  createTeam: (departmentId: string, data: CreateTeamRequest): Promise<AdminTeam> =>
    api
      .post(`admin/departments/${encodeURIComponent(departmentId)}/teams`, {
        json: data,
      })
      .json(),

  /** 팀 수정 — `departmentId` 를 바꾸면 부서 이동으로 처리된다. */
  updateTeam: (id: string, data: UpdateTeamRequest): Promise<AdminTeam> =>
    api.put(`admin/teams/${encodeURIComponent(id)}`, { json: data }).json(),

  /** 팀 활성/비활성 토글. */
  setTeamActive: (id: string, isActive: boolean): Promise<AdminTeam> =>
    api
      .patch(`admin/teams/${encodeURIComponent(id)}/active`, {
        json: { isActive },
      })
      .json(),

  /**
   * 부서 물리 삭제. BE 에서 이중 가드(비활성 상태 + 하위 팀 없음 + 참조 사용자 없음)를 통과해야 성공.
   * 204 No Content 응답이므로 body 파싱을 하지 않는다.
   */
  deleteDepartment: async (id: string): Promise<void> => {
    await api.delete(`admin/departments/${encodeURIComponent(id)}`);
  },

  /** 팀 물리 삭제. 비활성 + 참조 사용자 0 일 때만 성공. 204 No Content 반환. */
  deleteTeam: async (id: string): Promise<void> => {
    await api.delete(`admin/teams/${encodeURIComponent(id)}`);
  },
};
