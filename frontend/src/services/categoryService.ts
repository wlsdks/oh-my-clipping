import { api } from "@/lib/kyInstance";
import type { Category, CategoryPage, CreateCategoryRequest, UpdateCategoryRequest } from "@/types/category";

export const categoryService = {
  /** 카테고리를 페이지네이션으로 조회한다. */
  getPage: (params?: URLSearchParams): Promise<CategoryPage> => {
    const suffix = params?.toString() ? `?${params.toString()}` : "";
    return api.get(`admin/categories${suffix}`).json();
  },

  /** 전체 카테고리를 조회한다 (드롭다운, 셀렉터 등 내부 용도). */
  getAll: (): Promise<Category[]> =>
    api
      .get("admin/categories?size=500")
      .json<CategoryPage>()
      .then((page) => page.content),

  getById: (id: string): Promise<Category> => api.get(`admin/categories/${encodeURIComponent(id)}`).json(),

  create: (data: CreateCategoryRequest): Promise<Category> => api.post("admin/categories", { json: data }).json(),

  update: (id: string, data: UpdateCategoryRequest): Promise<Category> =>
    api.put(`admin/categories/${encodeURIComponent(id)}`, { json: data }).json(),

  delete: (id: string): Promise<void> => api.delete(`admin/categories/${encodeURIComponent(id)}`).then(() => undefined),

  pause: (id: string): Promise<Category> => api.put(`admin/categories/${encodeURIComponent(id)}/pause`).json(),

  resume: (id: string): Promise<Category> => api.put(`admin/categories/${encodeURIComponent(id)}/resume`).json(),
};
