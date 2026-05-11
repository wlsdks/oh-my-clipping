export interface UserInfo {
  id: string;
  username: string;
  displayName: string | null;
  role: "ADMIN" | "USER";
  approvalStatus: "PENDING" | "APPROVED" | "REJECTED";
  hasSlackDm?: boolean;
  mustChangePassword?: boolean;
  /**
   * V124(Phase 3 PR1): 프로필 편집 모달의 legacy 초기값.
   * V129 이후에도 JOIN 결과가 같이 동기화되므로 하위 호환 목적으로 유지한다.
   */
  department?: string | null;
  team?: string | null;
  /**
   * V129(Phase 2 FE): 부서/팀 FK id 와 JOIN 된 이름.
   * 백엔드 `/api/me` 응답이 아직 이 필드들을 포함하지 않을 수 있으므로 항상 optional 이다.
   * 새 값이 있으면 우선 사용하고, 없으면 legacy `department`/`team` 문자열로 폴백한다.
   */
  departmentId?: string | null;
  departmentName?: string | null;
  teamId?: string | null;
  teamName?: string | null;
}
