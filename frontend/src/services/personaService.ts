import { api } from "@/lib/kyInstance";
import type { Persona, PersonaVersionSummary } from "@/types/persona";

export interface PersonaCreateRequest {
  name: string;
  description?: string | null;
  systemPrompt: string;
  summaryStyle?: string | null;
  targetAudience?: string | null;
  maxItems?: number;
  language?: string;
}

export interface PersonaUpdateRequest {
  name?: string;
  description?: string | null;
  systemPrompt?: string;
  summaryStyle?: string | null;
  targetAudience?: string | null;
  maxItems?: number;
  language?: string;
  isActive?: boolean;
  /**
   * 낙관적 잠금용 updated_at. 클라이언트가 로드했을 때의 값과 DB가 일치하지 않으면
   * 서버가 409 STALE_EDIT로 응답한다. 미지정 시 경합 검증을 생략한다.
   */
  expectedUpdatedAt?: string | null;
}

export const personaService = {
  getAll: (): Promise<Persona[]> => api.get("admin/personas").json(),

  /** 단건 조회 — ChangeDetectionStrip 이 `updatedAt` 변경을 감지하기 위해 사용한다. */
  getById: (id: string): Promise<Persona> =>
    api.get(`admin/personas/${encodeURIComponent(id)}`).json(),

  create: (data: PersonaCreateRequest): Promise<Persona> => api.post("admin/personas", { json: data }).json(),

  update: (id: string, data: PersonaUpdateRequest): Promise<Persona> =>
    api.put(`admin/personas/${encodeURIComponent(id)}`, { json: data }).json(),

  delete: (id: string): Promise<void> => api.delete(`admin/personas/${encodeURIComponent(id)}`).then(() => undefined),

  getVersions: (id: string): Promise<PersonaVersionSummary[]> =>
    api.get(`admin/personas/${encodeURIComponent(id)}/versions`).json(),

  rollback: (id: string, version: number): Promise<Persona> =>
    api.post(`admin/personas/${encodeURIComponent(id)}/rollback/${version}`).json(),

  getPresets: (): Promise<Persona[]> => api.get("user/setup/preset-personas").json(),

  // ── 사용자용 페르소나 API (USER 역할) ──
  getUserAll: (): Promise<Persona[]> => api.get("user/setup/personas").json(),

  createUser: (data: PersonaCreateRequest): Promise<Persona> => api.post("user/setup/personas", { json: data }).json(),

  updateUser: (id: string, data: PersonaUpdateRequest): Promise<Persona> =>
    api.put(`user/setup/personas/${encodeURIComponent(id)}`, { json: data }).json(),

  deleteUser: (id: string): Promise<void> =>
    api.delete(`user/setup/personas/${encodeURIComponent(id)}`).then(() => undefined)
};
