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

import { runtimeService } from "@/services/runtimeService";
import type {
  RuntimeSettings,
  RuntimeSettingAudit,
  SlackConnectionVerifyResult,
  SlackBlockKitPreviewResult,
  SlackBlockKitTestSendResult,
  SlackChannelItem,
  SlackChannelListResponse
} from "@/types/runtime";

const mockSettings: RuntimeSettings = {
  defaultHoursBack: 24,
  summaryInputMaxChars: 5000,
  digestMinImportanceScore: 0.3,
  digestDefaultMaxItems: 10,
  digestMaxMessageChars: 3000,
  digestItemSummaryMaxChars: 500,
  digestKeywordMaxCount: 5,
  jobWorkerBatchSize: 10,
  jobMaxAttempts: 3,
  jobInitialBackoffSeconds: 60,
  slackBotToken: "xoxb-***",
  slackBotTokenConfigured: true,
  slackDigestBlockKitTemplate: "{}",
  slackAutoDigestEnabled: true,
  slackDigestCron: "0 9 * * 1-5",
  slackAutoDigestMaxItems: 10,
  slackAutoDigestUnsentOnly: true,
  slackDailyChannelMessageLimit: 50,
  ralphOrchestrationEnabled: false,
  ralphLoopEnabled: false,
  ralphLoopMaxIterations: 3,
  ralphLoopStopPhrase: "STOP",
  maintenanceMode: false,
  maintenanceMessage: "",
  opsLogChannelId: "",
  opsRequestChannelId: "",
  securityAlertChannelId: "",
  competitorWeeklyEnabled: false,
  competitorWeeklyChannelId: "",
  competitorWeeklyDmMode: "off",
  competitorWeeklyDmUserIds: "",
  competitorWeeklyDay: "MONDAY",
  competitorWeeklyHour: 9,
  reviewBatchUxEnabled: false,
  defaultReviewPerCategory: 20,
  retentionRssItemsDays: 30,
  retentionBatchSummariesDays: 90,
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockAudit: RuntimeSettingAudit = {
  settingKey: "defaultHoursBack",
  oldValue: "12",
  newValue: "24",
  action: "UPDATE",
  changedBy: "admin",
  changedAt: "2026-01-01T00:00:00Z"
};

const mockSlackVerify: SlackConnectionVerifyResult = {
  ok: true,
  botUser: "clipping-bot",
  team: "MyTeam",
  channelId: "C12345",
  channelName: "general",
  message: "연결 성공",
  warning: null
};

const mockPreview: SlackBlockKitPreviewResult = {
  valid: true,
  message: "미리보기 성공",
  renderedText: "테스트 메시지",
  blocks: [{ type: "section" }],
  placeholders: ["{{title}}"],
  templateUsed: "custom",
  defaultTemplate: "{}"
};

const mockTestSend: SlackBlockKitTestSendResult = {
  ok: true,
  message: "발송 성공",
  channelId: "C12345",
  messageTs: "1234567890.123456",
  renderedText: "테스트 메시지",
  blocks: [{ type: "section" }]
};

const mockChannel: SlackChannelItem = {
  id: "C12345",
  name: "general",
  isPrivate: false
};

const mockChannelListResponse: SlackChannelListResponse = {
  channels: [mockChannel],
  slackConnectRequired: false
};

const handlers = [
  // getSettings
  http.get("http://localhost/api/admin/runtime-settings", () =>
    HttpResponse.json(mockSettings)
  ),

  // updateSettings
  http.put("http://localhost/api/admin/runtime-settings", () =>
    HttpResponse.json(mockSettings)
  ),

  // resetSettings
  http.post("http://localhost/api/admin/runtime-settings/reset", () =>
    HttpResponse.json(mockSettings)
  ),

  // verifySlackConnection
  http.post("http://localhost/api/admin/runtime-settings/slack/verify", () =>
    HttpResponse.json(mockSlackVerify)
  ),

  // verifyUserSetupSlackConnection
  http.post("http://localhost/api/user/setup/slack/verify", () =>
    HttpResponse.json(mockSlackVerify)
  ),

  // previewSlackBlockKit
  http.post("http://localhost/api/admin/runtime-settings/slack/block-kit/preview", () =>
    HttpResponse.json(mockPreview)
  ),

  // testSendSlackBlockKit
  http.post("http://localhost/api/admin/runtime-settings/slack/block-kit/test-send", () =>
    HttpResponse.json(mockTestSend)
  ),

  // listAudits
  http.get("http://localhost/api/admin/runtime-settings/audits", () =>
    HttpResponse.json([mockAudit])
  ),

  // listUserSetupSlackChannels
  http.get("http://localhost/api/user/setup/slack/channels", () =>
    HttpResponse.json(mockChannelListResponse)
  ),

  // getUserSetupSlackChannelInfo
  http.get("http://localhost/api/user/setup/slack/channels/:id", () =>
    HttpResponse.json(mockChannel)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("runtimeService", () => {
  describe("getSettings", () => {
    it("런타임 설정을 반환해야 한다", async () => {
      const result = await runtimeService.getSettings();
      expect(result).toEqual(mockSettings);
      expect(result.defaultHoursBack).toBe(24);
      expect(result.slackBotTokenConfigured).toBe(true);
    });

    it("defaultReviewPerCategory 필드를 포함해야 한다", async () => {
      // 새 필드가 응답 타입에 포함되어 있고 기본값 20이 노출되어야 한다.
      const result = await runtimeService.getSettings();
      expect(result.defaultReviewPerCategory).toBe(20);
    });
  });

  describe("updateSettings", () => {
    it("설정을 업데이트하고 결과를 반환해야 한다", async () => {
      const result = await runtimeService.updateSettings({
        defaultHoursBack: 48,
        slackAutoDigestEnabled: false
      });
      expect(result).toEqual(mockSettings);
    });

    it("defaultReviewPerCategory를 바디에 포함해 호출할 수 있다", async () => {
      // 요청 바디에 새 필드를 실어 보낼 수 있어야 한다 (타입 컴파일 보장 + 런타임 정상 경로).
      const result = await runtimeService.updateSettings({
        defaultReviewPerCategory: 5
      });
      expect(result).toEqual(mockSettings);
    });
  });

  describe("resetSettings", () => {
    it("설정을 초기화하고 결과를 반환해야 한다", async () => {
      const result = await runtimeService.resetSettings();
      expect(result).toEqual(mockSettings);
    });
  });

  describe("verifySlackConnection", () => {
    it("Slack 연결 검증 결과를 반환해야 한다", async () => {
      const result = await runtimeService.verifySlackConnection({
        slackBotToken: "xoxb-test",
        slackChannelId: "C12345"
      });
      expect(result.ok).toBe(true);
      expect(result.botUser).toBe("clipping-bot");
      expect(result.team).toBe("MyTeam");
    });
  });

  describe("verifyUserSetupSlackConnection", () => {
    it("사용자 설정용 Slack 연결 검증 결과를 반환해야 한다", async () => {
      const result = await runtimeService.verifyUserSetupSlackConnection({
        slackChannelId: "C12345"
      });
      expect(result.ok).toBe(true);
      expect(result.channelName).toBe("general");
    });
  });

  describe("previewSlackBlockKit", () => {
    it("Block Kit 미리보기 결과를 반환해야 한다", async () => {
      const result = await runtimeService.previewSlackBlockKit({
        template: "{}",
        slackChannelId: "C12345"
      });
      expect(result.valid).toBe(true);
      expect(result.renderedText).toBe("테스트 메시지");
      expect(result.placeholders).toContain("{{title}}");
    });
  });

  describe("testSendSlackBlockKit", () => {
    it("Block Kit 테스트 발송 결과를 반환해야 한다", async () => {
      const result = await runtimeService.testSendSlackBlockKit({
        template: "{}",
        slackChannelId: "C12345"
      });
      expect(result.ok).toBe(true);
      expect(result.channelId).toBe("C12345");
      expect(result.messageTs).toBe("1234567890.123456");
    });
  });

  describe("listAudits", () => {
    it("감사 이력을 반환해야 한다", async () => {
      const result = await runtimeService.listAudits();
      expect(result).toEqual([mockAudit]);
      expect(result[0].settingKey).toBe("defaultHoursBack");
    });

    it("limit 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/runtime-settings/audits", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([mockAudit]);
        })
      );

      const result = await runtimeService.listAudits(10);

      expect(capturedSearch?.get("limit")).toBe("10");
      expect(result).toEqual([mockAudit]);
    });
  });

  describe("listUserSetupSlackChannels", () => {
    it("Slack 채널 목록을 래핑된 응답으로 반환해야 한다", async () => {
      const result = await runtimeService.listUserSetupSlackChannels("public_channel");
      expect(result).toEqual(mockChannelListResponse);
      expect(result.channels[0].name).toBe("general");
      expect(result.slackConnectRequired).toBe(false);
    });
  });

  describe("getUserSetupSlackChannelInfo", () => {
    it("특정 Slack 채널 정보를 반환해야 한다", async () => {
      const result = await runtimeService.getUserSetupSlackChannelInfo("C12345");
      expect(result).toEqual(mockChannel);
      expect(result.id).toBe("C12345");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/runtime-settings", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );

      await expect(runtimeService.getSettings()).rejects.toThrow();
    });
  });
});
