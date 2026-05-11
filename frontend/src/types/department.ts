/**
 * Department / Team 관련 프론트엔드 타입.
 *
 * 백엔드는 두 개의 트리 엔드포인트를 제공한다:
 * - `/api/public/departments/tree` — 최소 필드 (id/name/teams) 만. 익명 접근 가능.
 * - `/api/admin/departments/tree` — 관리자 전용, 비활성 포함 전체 메타(timestamps, displayOrder, isActive).
 *
 * 외부 "Organization" 도메인(경쟁사/고객사)과 혼동하지 않도록 반드시 Department / Team 네임스페이스를 유지한다.
 */

/** 공개 signup / 프로필 편집에서 사용하는 최소 팀 모양. */
export interface PublicTeam {
  id: string;
  name: string;
}

/** 공개 signup / 프로필 편집에서 사용하는 최소 부서 모양. */
export interface PublicDepartment {
  id: string;
  name: string;
  teams: PublicTeam[];
}

/** `/api/public/departments/tree` 응답 루트. */
export interface PublicDepartmentTreeResponse {
  departments: PublicDepartment[];
}

/** 관리자 응답: 부서 단건 (비활성 포함, timestamps 포함). */
export interface AdminDepartment {
  id: string;
  name: string;
  displayOrder: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 관리자 응답: 팀 단건 (비활성 포함). */
export interface AdminTeam {
  id: string;
  departmentId: string;
  name: string;
  displayOrder: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 트리 노드 — 부서 + 하위 팀. */
export interface AdminDepartmentTreeNode {
  department: AdminDepartment;
  teams: AdminTeam[];
}

/** `/api/admin/departments/tree` 응답 루트. */
export interface AdminDepartmentTreeResponse {
  content: AdminDepartmentTreeNode[];
  totalCount: number;
}

/** 어드민: 부서 생성 요청. */
export interface CreateDepartmentRequest {
  name: string;
  displayOrder?: number;
}

/** 어드민: 부서 수정 요청 (null 필드 = 변경 없음). */
export interface UpdateDepartmentRequest {
  name?: string;
  displayOrder?: number;
}

/** 어드민: 팀 생성 요청. 경로에 departmentId 포함. */
export interface CreateTeamRequest {
  name: string;
  displayOrder?: number;
}

/** 어드민: 팀 수정 요청. `departmentId` 를 바꾸면 부서 간 이동으로 처리된다. */
export interface UpdateTeamRequest {
  name?: string;
  displayOrder?: number;
  departmentId?: string;
}

/** 활성/비활성 토글 요청. */
export interface SetActiveRequest {
  isActive: boolean;
}
