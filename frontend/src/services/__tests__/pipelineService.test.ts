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

import { pipelineService } from "@/services/pipelineService";
import type { PipelineRunRecord, PipelineRunsPage, PipelineExecuteResponse } from "@/types/pipeline";

const mockRunRecord: PipelineRunRecord = {
  id: "run-1",
  categoryId: "cat-1",
  categoryName: "IT/기술",
  triggeredBy: "admin",
  status: "SUCCEEDED",
  orchestrationMode: "DETERMINISTIC",
  totalCollected: 50,
  totalSummarized: 30,
  totalDigestSelected: 10,
  postedToSlack: true,
  startedAt: "2026-01-01T09:00:00Z",
  endedAt: "2026-01-01T09:05:00Z",
  durationMs: 300000,
  errorMessage: null,
  stepTraces: [
    {
      id: "trace-1",
      step: "COLLECT",
      status: "SUCCEEDED",
      startedAt: "2026-01-01T09:00:00Z",
      endedAt: "2026-01-01T09:01:00Z",
      durationMs: 60000,
      detail: null
    }
  ]
};

const mockRunsPage: PipelineRunsPage = {
  content: [mockRunRecord],
  totalCount: 1,
  page: 0,
  size: 20
};

const mockExecuteResponse: PipelineExecuteResponse = {
  runId: "run-new"
};

const handlers = [
  // execute
  http.post("http://localhost/api/admin/pipeline/execute", () =>
    HttpResponse.json(mockExecuteResponse)
  ),

  // listRuns
  http.get("http://localhost/api/admin/pipeline/runs", ({ request }) => {
    const url = new URL(request.url);
    const runId = url.pathname.split("/").pop();
    // If it's just /runs (no sub-path), return the page
    if (runId === "runs") {
      return HttpResponse.json(mockRunsPage);
    }
    return HttpResponse.json(mockRunsPage);
  }),

  // getRunDetail
  http.get("http://localhost/api/admin/pipeline/runs/:runId", () =>
    HttpResponse.json(mockRunRecord)
  ),

  // getLatest
  http.get("http://localhost/api/admin/pipeline/latest", () =>
    HttpResponse.json(mockRunRecord)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("pipelineService", () => {
  describe("execute", () => {
    it("파이프라인을 실행하고 runId를 반환해야 한다", async () => {
      const result = await pipelineService.execute({
        categoryId: "cat-1",
        hoursBack: 24,
        maxItems: 10,
        sendToSlack: true
      });
      expect(result.runId).toBe("run-new");
    });

    it("최소 필수 파라미터만으로 실행할 수 있어야 한다", async () => {
      const result = await pipelineService.execute({
        categoryId: "cat-1"
      });
      expect(result.runId).toBe("run-new");
    });
  });

  describe("listRuns", () => {
    it("파라미터 없이 호출하면 전체 실행 이력을 반환해야 한다", async () => {
      const result = await pipelineService.listRuns();
      expect(result).toEqual(mockRunsPage);
      expect(result.content).toHaveLength(1);
    });

    it("URLSearchParams의 키/값이 쿼리스트링에 그대로 전달되어야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/pipeline/runs", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockRunsPage);
        })
      );

      const params = new URLSearchParams({ page: "1", size: "10" });
      const result = await pipelineService.listRuns(params);

      expect(capturedSearch?.get("page")).toBe("1");
      expect(capturedSearch?.get("size")).toBe("10");
      expect(result.content).toHaveLength(1);
    });

    it("within 파라미터를 쿼리스트링에 추가해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/pipeline/runs", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockRunsPage);
        })
      );

      await pipelineService.listRuns(undefined, "1d");

      expect(capturedSearch?.get("within")).toBe("1d");
    });

    it("personaId 파라미터를 전달하면 서버 쿼리에 포함되어야 한다", async () => {
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/pipeline/runs", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(mockRunsPage);
        })
      );

      const params = new URLSearchParams({
        page: "0",
        size: "20",
        personaId: "persona-xyz"
      });
      await pipelineService.listRuns(params);

      const url = new URL(capturedUrl);
      expect(url.searchParams.get("personaId")).toBe("persona-xyz");
      expect(url.searchParams.get("page")).toBe("0");
    });

    it("personaId가 없으면 쿼리에 포함하지 않는다", async () => {
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/pipeline/runs", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(mockRunsPage);
        })
      );

      const params = new URLSearchParams({ page: "0", size: "20" });
      await pipelineService.listRuns(params);

      const url = new URL(capturedUrl);
      expect(url.searchParams.has("personaId")).toBe(false);
    });
  });

  describe("getRunDetail", () => {
    it("특정 파이프라인 실행 상세를 반환해야 한다", async () => {
      const result = await pipelineService.getRunDetail("run-1");
      expect(result.id).toBe("run-1");
      expect(result.status).toBe("SUCCEEDED");
      expect(result.totalCollected).toBe(50);
      expect(result.stepTraces).toHaveLength(1);
    });
  });

  describe("getLatest", () => {
    it("카테고리의 최근 파이프라인 실행을 반환해야 한다", async () => {
      const result = await pipelineService.getLatest("cat-1");
      expect(result).not.toBeNull();
      expect(result!.categoryId).toBe("cat-1");
    });

    it("서버 오류 시 null을 반환해야 한다 (catch 처리)", async () => {
      server.use(
        http.get("http://localhost/api/admin/pipeline/latest", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );

      const result = await pipelineService.getLatest("cat-nonexistent");
      expect(result).toBeNull();
    });
  });

  describe("에러 처리", () => {
    it("실행 실패 시 에러를 throw해야 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/pipeline/execute", () =>
          HttpResponse.json({ message: "파이프라인 실행 실패" }, { status: 500 })
        )
      );

      await expect(
        pipelineService.execute({ categoryId: "cat-1" })
      ).rejects.toThrow();
    });
  });
});
