import { api } from "@/lib/kyInstance";
import type {
  CreateOrganizationRequest,
  Organization,
  OrganizationListResponse,
  OrganizationType,
  SetCategoryOrganizationsRequest,
  UpdateOrganizationRequest,
} from "@/types/organization";

/** Organization + Category 링크 관리 HTTP 클라이언트. */
export const organizationService = {
  /** 조직 목록 — 선택적 type 필터. */
  list: (type?: OrganizationType): Promise<OrganizationListResponse> => {
    const suffix = type ? `?type=${encodeURIComponent(type)}` : "";
    return api.get(`admin/organizations${suffix}`).json();
  },

  /** 단건 조회. */
  getById: (id: string): Promise<Organization> =>
    api.get(`admin/organizations/${encodeURIComponent(id)}`).json(),

  /** 신규 생성. */
  create: (data: CreateOrganizationRequest): Promise<Organization> =>
    api.post("admin/organizations", { json: data }).json(),

  /** 부분 수정. */
  update: (id: string, data: UpdateOrganizationRequest): Promise<Organization> =>
    api.patch(`admin/organizations/${encodeURIComponent(id)}`, { json: data }).json(),

  /** 삭제 — 연결된 링크는 DB CASCADE. */
  delete: (id: string): Promise<void> =>
    api.delete(`admin/organizations/${encodeURIComponent(id)}`).then(() => undefined),

  /** 카테고리에 연결된 조직 목록. */
  listByCategoryId: (categoryId: string): Promise<OrganizationListResponse> =>
    api.get(`admin/categories/${encodeURIComponent(categoryId)}/organizations`).json(),

  /** 카테고리 ↔ 조직 링크 교체. */
  setCategoryOrganizations: (
    categoryId: string,
    data: SetCategoryOrganizationsRequest,
  ): Promise<OrganizationListResponse> =>
    api
      .put(`admin/categories/${encodeURIComponent(categoryId)}/organizations`, { json: data })
      .json(),
};
