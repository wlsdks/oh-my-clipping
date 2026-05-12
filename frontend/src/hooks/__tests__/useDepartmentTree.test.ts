import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { createQueryClientWrapper } from "@/test/queryClient";

vi.mock("@/services/departmentService", () => ({
  departmentService: {
    getPublicTree: vi.fn(),
    getAdminTree: vi.fn(),
  },
}));

import { useDepartmentTree } from "@/hooks/useDepartmentTree";
import { departmentService } from "@/services/departmentService";

describe("useDepartmentTree", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("기본(public) 모드에서는 공개 트리를 조회한다", async () => {
    vi.mocked(departmentService.getPublicTree).mockResolvedValue({
      departments: [
        { id: "d1", name: "부서1", teams: [{ id: "t1", name: "팀1" }] },
      ],
    });

    const { result } = renderHook(() => useDepartmentTree(), {
      wrapper: createQueryClientWrapper(),
    });

    await waitFor(() => {
      expect(result.current.departments.length).toBe(1);
    });
    expect(result.current.admin).toBe(false);
    expect(departmentService.getPublicTree).toHaveBeenCalledTimes(1);
    expect(departmentService.getAdminTree).not.toHaveBeenCalled();
  });

  it("admin: true 모드에서는 관리자 트리를 조회한다", async () => {
    vi.mocked(departmentService.getAdminTree).mockResolvedValue({
      content: [
        {
          department: {
            id: "d1",
            name: "부서1",
            displayOrder: 0,
            isActive: true,
            createdAt: "",
            updatedAt: "",
          },
          teams: [],
        },
      ],
      totalCount: 1,
    });

    const { result } = renderHook(() => useDepartmentTree({ admin: true }), {
      wrapper: createQueryClientWrapper(),
    });

    await waitFor(() => {
      expect(result.current.departments.length).toBe(1);
    });
    expect(result.current.admin).toBe(true);
    expect(departmentService.getAdminTree).toHaveBeenCalledTimes(1);
    expect(departmentService.getPublicTree).not.toHaveBeenCalled();
  });

  it("서비스 호출이 실패하면 isError=true 를 반환한다", async () => {
    vi.mocked(departmentService.getPublicTree).mockRejectedValue(
      new Error("network down")
    );

    const { result } = renderHook(() => useDepartmentTree(), {
      wrapper: createQueryClientWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(result.current.departments).toEqual([]);
  });

  it("enabled=false 이면 서비스를 호출하지 않는다", async () => {
    renderHook(() => useDepartmentTree({ enabled: false }), {
      wrapper: createQueryClientWrapper(),
    });
    // 마이크로태스크 드레인 후에도 호출되면 안 된다.
    await Promise.resolve();
    expect(departmentService.getPublicTree).not.toHaveBeenCalled();
    expect(departmentService.getAdminTree).not.toHaveBeenCalled();
  });
});
