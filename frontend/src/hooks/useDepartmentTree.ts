import { useQuery } from "@tanstack/react-query";
import { departmentKeys } from "@/queries/departmentKeys";
import { departmentService } from "@/services/departmentService";
import type {
  AdminDepartmentTreeNode,
  PublicDepartment,
} from "@/types/department";

/**
 * useDepartmentTree 반환 타입.
 *
 * - 공개 모드(`admin: false`): `{ id, name, teams: [{ id, name }] }` 만 담긴 배열.
 * - 관리자 모드(`admin: true`): 비활성 포함 + timestamps/displayOrder 등 전체 메타.
 *
 * 호출 측에서 모드별 정확한 타입을 구분할 수 있도록 discriminated union 으로 노출한다.
 */
type PublicResult = {
  admin: false;
  departments: PublicDepartment[];
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};

type AdminResult = {
  admin: true;
  departments: AdminDepartmentTreeNode[];
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};

export interface UseDepartmentTreeOptions {
  /** `true` 면 관리자용 전체 트리를 가져온다. 기본 `false` (공개). */
  admin?: boolean;
  /** 필요 시 트리 호출을 명시적으로 끌 수 있다. 기본 `true`. */
  enabled?: boolean;
}

/**
 * 부서/팀 트리를 TanStack Query 로 조회하는 훅.
 *
 * - `staleTime: 10분` — 부서 구조는 자주 바뀌지 않으므로 네트워크 비용을 아낀다.
 * - 공개 모드는 익명 호출을 허용한다. 로그인 전 signup 페이지에서도 호출 가능.
 * - React Hook 규칙을 지키기 위해 두 개의 `useQuery` 중 `enabled` 로만 활성화를 토글한다.
 */
export function useDepartmentTree(options: { admin: true; enabled?: boolean }): AdminResult;
export function useDepartmentTree(options?: { admin?: false; enabled?: boolean }): PublicResult;
export function useDepartmentTree(options?: UseDepartmentTreeOptions): PublicResult | AdminResult {
  const admin = options?.admin ?? false;
  const enabled = options?.enabled ?? true;
  const staleTime = 10 * 60 * 1000;

  // 두 쿼리를 모두 등록하되, 한 쪽은 `enabled: false` 로 묶어 실제 네트워크 호출은 한 번만 일어나게 한다.
  const publicQuery = useQuery({
    queryKey: departmentKeys.tree(),
    queryFn: () => departmentService.getPublicTree(),
    staleTime,
    enabled: enabled && !admin,
  });

  const adminQuery = useQuery({
    queryKey: departmentKeys.adminTree(),
    queryFn: () => departmentService.getAdminTree(),
    staleTime,
    enabled: enabled && admin,
  });

  if (admin) {
    return {
      admin: true,
      departments: adminQuery.data?.content ?? [],
      isLoading: adminQuery.isLoading,
      isError: adminQuery.isError,
      refetch: () => {
        adminQuery.refetch();
      },
    };
  }

  return {
    admin: false,
    departments: publicQuery.data?.departments ?? [],
    isLoading: publicQuery.isLoading,
    isError: publicQuery.isError,
    refetch: () => {
      publicQuery.refetch();
    },
  };
}
