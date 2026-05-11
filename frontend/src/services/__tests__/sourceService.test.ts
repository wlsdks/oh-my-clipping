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

import { sourceService } from "@/services/sourceService";
import type { Source, SourcePage } from "@/types/source";

const mockSource: Source = {
  id: "src-1",
  name: "TechCrunch",
  url: "https://techcrunch.com/feed",
  sourceRegion: "GLOBAL",
  emoji: null,
  isActive: true,
  crawlApproved: true,
  approvedBy: "admin",
  approvedAt: "2026-01-01T00:00:00Z",
  legalBasis: "공정이용",
  summaryAllowed: true,
  fulltextAllowed: false,
  verificationStatus: "VERIFIED",
  reliabilityScore: 0.95,
  crawlFailCount: 0,
  curated: false,
  categoryId: "cat-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockSourcePage: SourcePage = {
  content: [mockSource],
  totalCount: 1,
  page: 0,
  size: 20
};

const handlers = [
  // getPage
  http.get("http://localhost/api/admin/sources", () =>
    HttpResponse.json(mockSourcePage)
  ),

  // create
  http.post("http://localhost/api/admin/sources", () =>
    HttpResponse.json({ ...mockSource, id: "src-new" }, { status: 201 })
  ),

  // update
  http.put("http://localhost/api/admin/sources/src-1", () =>
    HttpResponse.json({ ...mockSource, name: "Updated Source" })
  ),

  // delete
  http.delete("http://localhost/api/admin/sources/src-1", () =>
    new HttpResponse(null, { status: 204 })
  ),

  // validateUrl — valid
  http.post("http://localhost/api/admin/sources/validate-url", () =>
    HttpResponse.json({ valid: true, status: "200", reason: "OK" })
  ),

  // verify
  http.post("http://localhost/api/admin/sources/src-1/verify", () =>
    HttpResponse.json({ status: "VERIFIED" })
  ),

  // approve
  http.post("http://localhost/api/admin/sources/src-1/approve", () =>
    HttpResponse.json({ ...mockSource, crawlApproved: true })
  ),

  // discoverSource
  http.post("http://localhost/api/admin/sources/discover", () =>
    HttpResponse.json({
      knownMatch: { name: "TechCrunch", rssUrl: "https://techcrunch.com/feed", region: "GLOBAL" },
      discoveredFeeds: [{ url: "https://techcrunch.com/feed", title: "TechCrunch" }]
    })
  ),

  // getCompliance
  http.get("http://localhost/api/admin/source-compliance/src-1", () =>
    HttpResponse.json({ id: "src-1", legalBasis: "공정이용", summaryAllowed: true, fulltextAllowed: false })
  ),

  // updateCompliance
  http.put("http://localhost/api/admin/source-compliance/src-1", () =>
    HttpResponse.json({ id: "src-1", legalBasis: "저작권법", summaryAllowed: false, fulltextAllowed: false })
  ),

  // compliance-summary (재검토 필요 건수)
  http.get("http://localhost/api/admin/sources/compliance-summary", () =>
    HttpResponse.json({ attentionCount: 7 })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("sourceService", () => {
  describe("소스 목록 조회", () => {
    it("getPage는 페이지네이션 소스 목록을 반환해야 한다", async () => {
      const result = await sourceService.getPage();
      expect(result).toEqual(mockSourcePage);
      expect(result.content).toHaveLength(1);
      expect(result.content[0].name).toBe("TechCrunch");
    });

    it("getPage는 URLSearchParams의 키/값을 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/sources", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockSourcePage);
        })
      );

      const params = new URLSearchParams({ categoryId: "cat-1", size: "10" });
      const result = await sourceService.getPage(params);

      expect(capturedSearch?.get("categoryId")).toBe("cat-1");
      expect(capturedSearch?.get("size")).toBe("10");
      expect(result.content).toHaveLength(1);
    });

    it("getAll은 소스 배열만 반환해야 한다", async () => {
      const result = await sourceService.getAll();
      expect(Array.isArray(result)).toBe(true);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("src-1");
    });

    it("getAll은 categoryId 필터로 조회할 수 있어야 한다", async () => {
      const result = await sourceService.getAll("cat-1");
      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe("소스 생성 / 수정 / 삭제", () => {
    it("create는 새 소스를 생성해야 한다", async () => {
      const result = await sourceService.create({
        name: "TechCrunch",
        url: "https://techcrunch.com/feed",
        categoryId: "cat-1",
        legalBasis: "공정이용",
        summaryAllowed: true,
        fulltextAllowed: false
      });
      expect(result.id).toBe("src-new");
      expect(result.name).toBe("TechCrunch");
    });

    it("update는 소스 정보를 수정해야 한다", async () => {
      const result = await sourceService.update("src-1", { name: "Updated Source" });
      expect(result.name).toBe("Updated Source");
    });

    it("delete는 소스를 삭제하고 undefined를 반환해야 한다", async () => {
      const result = await sourceService.delete("src-1");
      expect(result).toBeUndefined();
    });
  });

  describe("소스 검증 / 승인", () => {
    it("validateUrl은 유효한 URL 검증 결과를 반환해야 한다", async () => {
      const result = await sourceService.validateUrl("https://techcrunch.com/feed");
      expect(result.valid).toBe(true);
      expect(result.reason).toBe("OK");
    });

    it("verify는 소스 검증 상태를 반환해야 한다", async () => {
      const result = await sourceService.verify("src-1");
      expect(result.status).toBe("VERIFIED");
    });

    it("approve는 소스 승인 결과를 반환해야 한다", async () => {
      const result = await sourceService.approve("src-1", {
        approved: true,
        legalBasis: "공정이용",
        summaryAllowed: true
      });
      expect(result.crawlApproved).toBe(true);
    });
  });

  describe("소스 검색 / 컴플라이언스", () => {
    it("discoverSource는 알려진 소스와 발견된 피드를 반환해야 한다", async () => {
      const result = await sourceService.discoverSource("techcrunch");
      expect(result.knownMatch).not.toBeNull();
      expect(result.knownMatch?.name).toBe("TechCrunch");
      expect(result.discoveredFeeds).toHaveLength(1);
    });

    it("getCompliance는 소스 컴플라이언스 정보를 반환해야 한다", async () => {
      const result = await sourceService.getCompliance("src-1");
      expect(result.legalBasis).toBe("공정이용");
      expect(result.summaryAllowed).toBe(true);
    });

    it("updateCompliance는 컴플라이언스 정보를 업데이트해야 한다", async () => {
      const result = await sourceService.updateCompliance("src-1", {
        legalBasis: "저작권법",
        summaryAllowed: false
      });
      expect(result.legalBasis).toBe("저작권법");
      expect(result.summaryAllowed).toBe(false);
    });

    it("getComplianceSummary 는 재검토 필요 건수를 반환해야 한다", async () => {
      const result = await sourceService.getComplianceSummary();
      expect(result.attentionCount).toBe(7);
    });

    it("getPage 는 complianceStatus 쿼리 파라미터를 서버에 전달해야 한다", async () => {
      let receivedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/sources", ({ request }) => {
          receivedUrl = request.url;
          return HttpResponse.json(mockSourcePage);
        })
      );
      const params = new URLSearchParams({ complianceStatus: "EXPIRED" });
      await sourceService.getPage(params);
      expect(receivedUrl).toContain("complianceStatus=EXPIRED");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/sources", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(sourceService.getPage()).rejects.toThrow();
    });
  });
});
