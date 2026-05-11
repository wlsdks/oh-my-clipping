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

import { userService } from "@/services/userService";
import type { UserClippingRequest, UserAccountApproval, UserSubscriptionPreference, DeliverySchedule, BulkReviewResult } from "@/types/user";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

const mockClippingRequest: UserClippingRequest = {
  id: "req-1",
  requesterUserId: "user-1",
  requestName: "IT 뉴스",
  sourceName: "TechCrunch",
  sourceUrl: "https://techcrunch.com/feed",
  slackChannelId: "C12345",
  personaName: "IT 전문가",
  personaPrompt: "기술 뉴스 큐레이터",
  summaryStyle: "bullet",
  targetAudience: "개발자",
  selectedPresetId: null,
  requestNote: null,
  status: "PENDING",
  reviewNote: null,
  reviewedByUserId: null,
  reviewedAt: null,
  approvedCategoryId: null,
  approvedCategoryName: null,
  approvedPersonaId: null,
  approvedSourceId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  deliveryState: "PENDING_REVIEW",
  collectingReady: false,
  totalSourceCount: 1,
  readySourceCount: 0,
  representativeSourceVerificationStatus: null
};

const mockUserAccount: UserAccountApproval = {
  id: "ua-1",
  username: "testuser",
  displayName: "테스트 유저",
  department: "개발팀",
  isActive: true,
  approvalStatus: "PENDING",
  approvalNote: null,
  approvedByUserId: null,
  approvedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  lastLoginAt: null,
  subscriptionCount: 0,
  recentActivitySummary: null,
  role: "USER",
  approvedByUsername: null
};

const mockSubscriptionPref: UserSubscriptionPreference = {
  requestId: "req-1",
  categoryId: "cat-1",
  requestName: "IT 뉴스",
  isActive: true,
  maxItems: 10,
  excludeKeywords: [],
  includeThreshold: 0.5,
  deliveryDays: null,
  deliveryHour: null,
  deliveryPreset: null,
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockDeliverySchedule: DeliverySchedule = {
  deliveryDays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  deliveryHour: 9,
  preset: "WEEKDAYS",
  updatedAt: "2026-01-01T00:00:00Z"
};

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

const mockCategory: Category = {
  id: "cat-1",
  name: "IT/기술",
  description: "IT 기술 뉴스",
  slackChannelId: "C12345",
  isActive: true,
  isPublic: true,
  maxItems: 10,
  personaId: null,
  sourceCount: 5,
  subscriberCount: 3,
  lastDeliveryAt: null,
  errorSourceCount: 0,
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockBulkResult: BulkReviewResult = {
  succeeded: ["ua-1", "ua-2"],
  failed: []
};

const handlers = [
  // listClippingRequests
  http.get("http://localhost/api/user/requests", () =>
    HttpResponse.json([mockClippingRequest])
  ),

  // createClippingRequest
  http.post("http://localhost/api/user/requests", () =>
    HttpResponse.json(mockClippingRequest, { status: 201 })
  ),

  // registerWizardOwnership
  http.post("http://localhost/api/user/requests/wizard-ownership", () =>
    HttpResponse.json(mockClippingRequest, { status: 201 })
  ),

  // withdrawClippingRequest
  http.post("http://localhost/api/user/requests/:id/withdraw", () =>
    HttpResponse.json({ ...mockClippingRequest, status: "WITHDRAWN" })
  ),

  // unsubscribeClippingRequest
  http.post("http://localhost/api/user/requests/:id/unsubscribe", () =>
    HttpResponse.json({ ...mockClippingRequest, status: "WITHDRAWN" })
  ),

  // deleteClippingRequest
  http.delete("http://localhost/api/user/requests/:id/remove", () =>
    new HttpResponse(null, { status: 204 })
  ),

  // getSubscriptionPreferences
  http.get("http://localhost/api/user/subscriptions/:id/preferences", () =>
    HttpResponse.json(mockSubscriptionPref)
  ),

  // updateSubscriptionPreferences
  http.put("http://localhost/api/user/subscriptions/:id/preferences", () =>
    HttpResponse.json(mockSubscriptionPref)
  ),

  // getDeliverySchedule
  http.get("http://localhost/api/user/delivery-schedule", () =>
    HttpResponse.json(mockDeliverySchedule)
  ),

  // updateDeliverySchedule
  http.put("http://localhost/api/user/delivery-schedule", () =>
    HttpResponse.json(mockDeliverySchedule)
  ),

  // createSetupCategory
  http.post("http://localhost/api/user/setup/categories", () =>
    HttpResponse.json(mockCategory, { status: 201 })
  ),

  // createSetupSource
  http.post("http://localhost/api/user/setup/sources", () =>
    HttpResponse.json(mockSource, { status: 201 })
  ),

  // verifySetupSource
  http.post("http://localhost/api/user/setup/sources/:id/verify", () =>
    HttpResponse.json({ status: "VERIFIED" })
  ),

  // approveSetupSource
  http.post("http://localhost/api/user/setup/sources/:id/approve", () =>
    HttpResponse.json(mockSource)
  ),

  // validateSetupSourceUrl
  http.post("http://localhost/api/user/setup/sources/validate-url", () =>
    HttpResponse.json({ valid: true, status: "200", reason: "OK" })
  ),

  // searchKnownSources
  http.get("http://localhost/api/user/setup/sources/known-sources", () =>
    HttpResponse.json([{ name: "TechCrunch", domain: "techcrunch.com", region: "GLOBAL", aliases: ["TC"] }])
  ),

  // getUserAccountSummary
  http.get("http://localhost/api/admin/user-accounts/summary", () =>
    HttpResponse.json({ pendingCount: 3, rejectedCount: 1, weeklyProcessedCount: 10 })
  ),

  // listAdminUserAccounts
  http.get("http://localhost/api/admin/user-accounts", () =>
    HttpResponse.json([mockUserAccount])
  ),

  // approveAdminUserAccount
  http.post("http://localhost/api/admin/user-accounts/:id/approve", () =>
    HttpResponse.json({ ...mockUserAccount, approvalStatus: "APPROVED" })
  ),

  // rejectAdminUserAccount
  http.post("http://localhost/api/admin/user-accounts/:id/reject", () =>
    HttpResponse.json({ ...mockUserAccount, approvalStatus: "REJECTED" })
  ),

  // withdrawAdminUserAccount
  http.post("http://localhost/api/admin/user-accounts/:id/withdraw", () =>
    HttpResponse.json({ ...mockUserAccount, approvalStatus: "PENDING" })
  ),

  // bulkApproveAdminUserAccounts
  http.post("http://localhost/api/admin/user-accounts/bulk-approve", () =>
    HttpResponse.json(mockBulkResult)
  ),

  // bulkRejectAdminUserAccounts
  http.post("http://localhost/api/admin/user-accounts/bulk-reject", () =>
    HttpResponse.json(mockBulkResult)
  ),

  // listAdminClippingRequests
  http.get("http://localhost/api/admin/user-requests", () =>
    HttpResponse.json([mockClippingRequest])
  ),

  // approveAdminClippingRequest
  http.post("http://localhost/api/admin/user-requests/:id/approve", () =>
    HttpResponse.json({ ...mockClippingRequest, status: "APPROVED" })
  ),

  // rejectAdminClippingRequest
  http.post("http://localhost/api/admin/user-requests/:id/reject", () =>
    HttpResponse.json({ ...mockClippingRequest, status: "REJECTED" })
  ),

  // browseCategories
  http.get("http://localhost/api/user/categories/browse", () =>
    HttpResponse.json([{
      id: "cat-1",
      name: "IT/기술",
      description: "IT 기술 뉴스",
      slackChannelId: "C12345",
      subscriberCount: 5,
      isSubscribed: false,
      deliveryHour: 9,
      maxItems: 10
    }])
  ),

  // subscribeCategoryDm
  http.post("http://localhost/api/user/categories/:id/subscribe", () =>
    HttpResponse.json({ requestId: "req-new", categoryId: "cat-1", status: "PENDING" })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("userService", () => {
  describe("사용자 클리핑 신청 API", () => {
    it("listClippingRequests는 신청 목록을 반환해야 한다", async () => {
      const result = await userService.listClippingRequests();
      expect(result).toEqual([mockClippingRequest]);
      expect(result).toHaveLength(1);
    });

    it("createClippingRequest는 새 신청을 생성해야 한다", async () => {
      const result = await userService.createClippingRequest({
        requestName: "IT 뉴스",
        sourceName: "TechCrunch",
        sourceUrl: "https://techcrunch.com/feed",
        slackChannelId: "C12345",
        personaName: "IT 전문가",
        personaPrompt: "기술 뉴스 큐레이터"
      });
      expect(result.id).toBe("req-1");
      expect(result.requestName).toBe("IT 뉴스");
    });

    it("registerWizardOwnership는 위자드 소유권 등록을 해야 한다", async () => {
      const result = await userService.registerWizardOwnership({
        requestName: "IT 뉴스",
        sourceName: "TechCrunch",
        sourceUrl: "https://techcrunch.com/feed",
        slackChannelId: "C12345",
        personaName: "IT 전문가",
        personaPrompt: "기술 뉴스 큐레이터",
        categoryId: "cat-1"
      });
      expect(result.id).toBe("req-1");
    });

    it("withdrawClippingRequest는 신청을 철회해야 한다", async () => {
      const result = await userService.withdrawClippingRequest("req-1");
      expect(result.status).toBe("WITHDRAWN");
    });

    it("unsubscribeClippingRequest는 WITHDRAWN 상태로 전환된 신청을 반환해야 한다", async () => {
      let capturedPath: string | undefined;
      server.use(
        http.post("http://localhost/api/user/requests/:id/unsubscribe", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json({ ...mockClippingRequest, status: "WITHDRAWN" });
        })
      );

      const result = await userService.unsubscribeClippingRequest("req-1");

      expect(capturedPath).toBe("/api/user/requests/req-1/unsubscribe");
      expect(result.status).toBe("WITHDRAWN");
    });

    it("deleteClippingRequest는 삭제 후 undefined를 반환해야 한다", async () => {
      const result = await userService.deleteClippingRequest("req-1");
      expect(result).toBeUndefined();
    });
  });

  describe("사용자 구독 설정 API", () => {
    it("getSubscriptionPreferences는 구독 설정을 반환해야 한다", async () => {
      const result = await userService.getSubscriptionPreferences("req-1");
      expect(result.requestId).toBe("req-1");
      expect(result.isActive).toBe(true);
    });

    it("updateSubscriptionPreferences는 구독 설정을 업데이트해야 한다", async () => {
      const result = await userService.updateSubscriptionPreferences("req-1", {
        isActive: false,
        maxItems: 5
      });
      expect(result.requestId).toBe("req-1");
    });
  });

  describe("배송 스케줄 API", () => {
    it("getDeliverySchedule는 발송 스케줄을 반환해야 한다", async () => {
      const result = await userService.getDeliverySchedule();
      expect(result.preset).toBe("WEEKDAYS");
      expect(result.deliveryHour).toBe(9);
    });

    it("updateDeliverySchedule는 body를 PUT으로 전송하고 결과를 반환해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.put("http://localhost/api/user/delivery-schedule", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockDeliverySchedule);
        })
      );

      const result = await userService.updateDeliverySchedule({
        deliveryDays: ["MONDAY", "WEDNESDAY", "FRIDAY"],
        deliveryHour: 10,
        preset: "CUSTOM"
      });

      expect(capturedBody).toEqual({
        deliveryDays: ["MONDAY", "WEDNESDAY", "FRIDAY"],
        deliveryHour: 10,
        preset: "CUSTOM"
      });
      expect(result.deliveryHour).toBe(9); // mock returns existing schedule
      expect(result.preset).toBe("WEEKDAYS");
    });
  });

  describe("사용자용 카테고리/소스 설정 API", () => {
    it("createSetupCategory는 카테고리를 생성해야 한다", async () => {
      const result = await userService.createSetupCategory({
        name: "IT/기술",
        description: "IT 기술 뉴스",
        slackChannelId: "C12345"
      });
      expect(result.id).toBe("cat-1");
      expect(result.name).toBe("IT/기술");
    });

    it("createSetupSource는 소스를 생성해야 한다", async () => {
      const result = await userService.createSetupSource({
        name: "TechCrunch",
        url: "https://techcrunch.com/feed",
        categoryId: "cat-1"
      });
      expect(result.id).toBe("src-1");
    });

    it("verifySetupSource는 소스 검증 결과를 반환해야 한다", async () => {
      const result = await userService.verifySetupSource("src-1");
      expect(result.status).toBe("VERIFIED");
    });

    it("approveSetupSource는 소스 승인 결과를 반환해야 한다", async () => {
      const result = await userService.approveSetupSource("src-1", {
        approved: true,
        approvedBy: "admin"
      });
      expect(result.id).toBe("src-1");
    });

    it("validateSetupSourceUrl은 URL 유효성을 반환해야 한다", async () => {
      const result = await userService.validateSetupSourceUrl("https://example.com/feed");
      expect(result.valid).toBe(true);
      expect(result.reason).toBe("OK");
    });

    it("searchKnownSources는 알려진 소스 목록을 반환해야 한다", async () => {
      const result = await userService.searchKnownSources("tech");
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe("TechCrunch");
    });

    it("searchKnownSources는 파라미터 없이 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/user/setup/sources/known-sources", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json([{ name: "TechCrunch", domain: "techcrunch.com", region: "GLOBAL", aliases: ["TC"] }]);
        })
      );

      const result = await userService.searchKnownSources();

      expect(capturedUrl?.search).toBe("");
      expect(result).toHaveLength(1);
    });

    it("searchKnownSources는 q/region을 쿼리스트링에 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/setup/sources/known-sources", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([]);
        })
      );

      await userService.searchKnownSources("tech", "GLOBAL");

      expect(capturedSearch?.get("q")).toBe("tech");
      expect(capturedSearch?.get("region")).toBe("GLOBAL");
    });
  });

  describe("어드민: 사용자 계정 관리 API", () => {
    it("getUserAccountSummary는 계정 요약을 반환해야 한다", async () => {
      const result = await userService.getUserAccountSummary();
      expect(result.pendingCount).toBe(3);
      expect(result.rejectedCount).toBe(1);
      expect(result.weeklyProcessedCount).toBe(10);
    });

    it("listAdminUserAccounts는 전체 계정 목록을 반환해야 한다", async () => {
      const result = await userService.listAdminUserAccounts();
      expect(result).toEqual([mockUserAccount]);
    });

    it("listAdminUserAccounts는 status를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/user-accounts", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([mockUserAccount]);
        })
      );

      const result = await userService.listAdminUserAccounts("PENDING");

      expect(capturedSearch?.get("status")).toBe("PENDING");
      expect(result).toEqual([mockUserAccount]);
    });

    it("listAdminUserAccounts는 personaId 옵션을 searchParams로 전달해야 한다", async () => {
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/user-accounts", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([mockUserAccount]);
        })
      );

      await userService.listAdminUserAccounts("APPROVED", { personaId: "persona-xyz" });

      const url = new URL(capturedUrl);
      expect(url.searchParams.get("status")).toBe("APPROVED");
      expect(url.searchParams.get("personaId")).toBe("persona-xyz");
    });

    it("listAdminUserAccounts는 personaId가 없으면 쿼리에 포함하지 않는다", async () => {
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/user-accounts", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([mockUserAccount]);
        })
      );

      await userService.listAdminUserAccounts("APPROVED");

      const url = new URL(capturedUrl);
      expect(url.searchParams.has("personaId")).toBe(false);
    });

    it("approveAdminUserAccount는 계정을 승인해야 한다", async () => {
      const result = await userService.approveAdminUserAccount("ua-1");
      expect(result.approvalStatus).toBe("APPROVED");
    });

    it("rejectAdminUserAccount는 계정을 반려해야 한다", async () => {
      const result = await userService.rejectAdminUserAccount("ua-1", {
        reviewNote: "불일치"
      });
      expect(result.approvalStatus).toBe("REJECTED");
    });

    it("withdrawAdminUserAccount는 계정 승인을 철회해야 한다", async () => {
      const result = await userService.withdrawAdminUserAccount("ua-1");
      expect(result.approvalStatus).toBe("PENDING");
    });

    it("bulkApproveAdminUserAccounts는 일괄 승인 결과를 반환해야 한다", async () => {
      const result = await userService.bulkApproveAdminUserAccounts({
        ids: ["ua-1", "ua-2"]
      });
      expect(result.succeeded).toEqual(["ua-1", "ua-2"]);
      expect(result.failed).toEqual([]);
    });

    it("bulkRejectAdminUserAccounts는 일괄 반려 결과를 반환해야 한다", async () => {
      const result = await userService.bulkRejectAdminUserAccounts({
        ids: ["ua-1", "ua-2"],
        reviewNote: "부적합"
      });
      expect(result.succeeded).toEqual(["ua-1", "ua-2"]);
    });
  });

  describe("어드민: 사용자 신청 관리 API", () => {
    it("listAdminClippingRequests는 전체 신청 목록을 반환해야 한다", async () => {
      const result = await userService.listAdminClippingRequests();
      expect(result).toEqual([mockClippingRequest]);
    });

    it("listAdminClippingRequests는 status를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/user-requests", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([mockClippingRequest]);
        })
      );

      const result = await userService.listAdminClippingRequests("PENDING");

      expect(capturedSearch?.get("status")).toBe("PENDING");
      expect(result).toEqual([mockClippingRequest]);
    });

    it("approveAdminClippingRequest는 신청을 승인해야 한다", async () => {
      const result = await userService.approveAdminClippingRequest("req-1", {
        legalBasis: "QUOTATION_ONLY",
        summaryAllowed: true,
        fulltextAllowed: false,
        reviewNotes: null,
        responsibilityAcknowledged: true,
      });
      expect(result.status).toBe("APPROVED");
    });

    it("rejectAdminClippingRequest는 신청을 반려해야 한다", async () => {
      const result = await userService.rejectAdminClippingRequest("req-1", {
        reviewNote: "부적합"
      });
      expect(result.status).toBe("REJECTED");
    });
  });

  describe("카테고리 탐색/구독 API", () => {
    it("browseCategories는 카테고리 목록을 반환해야 한다", async () => {
      const result = await userService.browseCategories();
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("cat-1");
      expect(result[0].subscriberCount).toBe(5);
    });

    it("subscribeCategoryDm은 구독 응답을 반환해야 한다", async () => {
      const result = await userService.subscribeCategoryDm("cat-1");
      expect(result.requestId).toBe("req-new");
      expect(result.categoryId).toBe("cat-1");
      expect(result.status).toBe("PENDING");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/user/requests", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );

      await expect(userService.listClippingRequests()).rejects.toThrow();
    });
  });
});
