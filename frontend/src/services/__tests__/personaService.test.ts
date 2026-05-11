// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

vi.mock("@/store/authStore", () => ({
  authStore: { getState: vi.fn(() => ({ logout: vi.fn() })) }
}));

vi.mock("@/lib/kyInstance", async () => {
  const ky = (await import("ky")).default;
  const { authStore } = await import("@/store/authStore");
  return {
    api: ky.create({
      prefixUrl: "http://localhost/api",
      credentials: "include",
      headers: { Accept: "application/json" },
      hooks: {
        afterResponse: [
          async (_req: unknown, _opts: unknown, res: Response) => {
            if (res.status === 401) authStore.getState().logout();
          }
        ]
      }
    })
  };
});

import { personaService } from "@/services/personaService";
import type { Persona, PersonaVersionSummary } from "@/types/persona";

const mockPersona: Persona = {
  id: "persona-1",
  name: "IT 전문가",
  description: "기술 뉴스 큐레이터",
  systemPrompt: "당신은 기술 뉴스를 전문적으로 요약하는 AI입니다.",
  summaryStyle: "bullet",
  targetAudience: "개발자",
  maxItems: 10,
  language: "ko",
  isActive: true,
  currentVersion: 1,
  previewTitle: null,
  previewSource: null,
  previewBody: null,
  tone: null,
  lengthPref: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockVersions: PersonaVersionSummary[] = [
  { version: 2, changeSummary: "프롬프트 개선", createdAt: "2026-02-01T00:00:00Z" },
  { version: 1, changeSummary: null, createdAt: "2026-01-01T00:00:00Z" }
];

const handlers = [
  // getAll (admin)
  http.get("http://localhost/api/admin/personas", () =>
    HttpResponse.json([mockPersona])
  ),

  // create (admin)
  http.post("http://localhost/api/admin/personas", () =>
    HttpResponse.json({ ...mockPersona, id: "persona-new" }, { status: 201 })
  ),

  // update (admin)
  http.put("http://localhost/api/admin/personas/persona-1", () =>
    HttpResponse.json({ ...mockPersona, name: "Updated Persona" })
  ),

  // delete (admin)
  http.delete("http://localhost/api/admin/personas/persona-1", () =>
    new HttpResponse(null, { status: 204 })
  ),

  // getVersions
  http.get("http://localhost/api/admin/personas/persona-1/versions", () =>
    HttpResponse.json(mockVersions)
  ),

  // rollback
  http.post("http://localhost/api/admin/personas/persona-1/rollback/1", () =>
    HttpResponse.json({ ...mockPersona, currentVersion: 1 })
  ),

  // getPresets (user)
  http.get("http://localhost/api/user/setup/preset-personas", () =>
    HttpResponse.json([mockPersona])
  ),

  // getUserAll
  http.get("http://localhost/api/user/setup/personas", () =>
    HttpResponse.json([mockPersona])
  ),

  // createUser
  http.post("http://localhost/api/user/setup/personas", () =>
    HttpResponse.json({ ...mockPersona, id: "user-persona-new" }, { status: 201 })
  ),

  // updateUser
  http.put("http://localhost/api/user/setup/personas/persona-1", () =>
    HttpResponse.json({ ...mockPersona, name: "User Updated Persona" })
  ),

  // deleteUser
  http.delete("http://localhost/api/user/setup/personas/persona-1", () =>
    new HttpResponse(null, { status: 204 })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("personaService", () => {
  describe("어드민: 페르소나 CRUD", () => {
    it("getAll은 페르소나 목록을 반환해야 한다", async () => {
      const result = await personaService.getAll();
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("persona-1");
      expect(result[0].name).toBe("IT 전문가");
    });

    it("create는 새 페르소나를 생성해야 한다", async () => {
      const result = await personaService.create({
        name: "IT 전문가",
        systemPrompt: "당신은 기술 뉴스를 전문적으로 요약하는 AI입니다."
      });
      expect(result.id).toBe("persona-new");
    });

    it("update는 페르소나 정보를 수정해야 한다", async () => {
      const result = await personaService.update("persona-1", { name: "Updated Persona" });
      expect(result.name).toBe("Updated Persona");
    });

    it("delete는 페르소나를 삭제하고 undefined를 반환해야 한다", async () => {
      const result = await personaService.delete("persona-1");
      expect(result).toBeUndefined();
    });
  });

  describe("어드민: 버전 관리", () => {
    it("getVersions는 버전 이력 목록을 반환해야 한다", async () => {
      const result = await personaService.getVersions("persona-1");
      expect(result).toHaveLength(2);
      expect(result[0].version).toBe(2);
    });

    it("rollback은 특정 버전으로 롤백하고 페르소나를 반환해야 한다", async () => {
      const result = await personaService.rollback("persona-1", 1);
      expect(result.currentVersion).toBe(1);
    });
  });

  describe("유저: 페르소나 API", () => {
    it("getPresets는 프리셋 페르소나 목록을 반환해야 한다", async () => {
      const result = await personaService.getPresets();
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("persona-1");
    });

    it("getUserAll은 유저 소유 페르소나 목록을 반환해야 한다", async () => {
      const result = await personaService.getUserAll();
      expect(result).toHaveLength(1);
    });

    it("createUser는 유저 페르소나를 생성해야 한다", async () => {
      const result = await personaService.createUser({
        name: "IT 전문가",
        systemPrompt: "당신은 기술 뉴스를 전문적으로 요약하는 AI입니다."
      });
      expect(result.id).toBe("user-persona-new");
    });

    it("updateUser는 유저 페르소나를 수정해야 한다", async () => {
      const result = await personaService.updateUser("persona-1", { name: "User Updated Persona" });
      expect(result.name).toBe("User Updated Persona");
    });

    it("deleteUser는 유저 페르소나를 삭제하고 undefined를 반환해야 한다", async () => {
      const result = await personaService.deleteUser("persona-1");
      expect(result).toBeUndefined();
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/personas", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(personaService.getAll()).rejects.toThrow();
    });
  });
});
