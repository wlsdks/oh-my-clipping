import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { ReactElement } from "react";
import { QuickSetupStepSlack } from "../QuickSetupStepSlack";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { QuickSetupForm } from "../model/quickSetupTypes";

// runtimeService mock — 빈 채널 목록 반환
vi.mock("@/services/runtimeService", () => ({
  runtimeService: {
    listUserSetupSlackChannels: vi.fn().mockResolvedValue({ channels: [], slackConnectRequired: false }),
    getUserSetupSlackChannelInfo: vi.fn().mockResolvedValue(null)
  }
}));

// userService mock
vi.mock("@/services/userService", () => ({
  userService: {
    listClippingRequests: vi.fn().mockResolvedValue([]),
    updateSlackMemberId: vi.fn().mockResolvedValue(undefined)
  }
}));

// categoryService mock
vi.mock("@/services/categoryService", () => ({
  categoryService: {
    getAll: vi.fn().mockResolvedValue([])
  }
}));

// authStore mock — Slack DM 미연동 상태
vi.mock("@/store/authStore", () => ({
  useAuthStore: vi.fn((selector) =>
    selector({ user: { hasSlackDm: false } })
  )
}));

// framer-motion mock — 애니메이션 무력화
vi.mock("framer-motion", async () => {
  const React = await import("react");
  return {
    motion: {
      div: ({ children, ...rest }: React.HTMLAttributes<HTMLDivElement>) =>
        React.createElement("div", rest, children)
    },
    AnimatePresence: ({ children }: { children: React.ReactNode }) => children
  };
});

/** 최소 QuickSetupForm 기본값 */
function makeForm(overrides: Partial<QuickSetupForm>): QuickSetupForm {
  return {
    entries: [],
    newsRegion: "domestic",
    siteSelectionMode: "all",
    siteFilters: [],
    categoryName: "",
    categoryDescription: "",
    slackChannelId: "",
    slackChannelType: "public_channel",
    maxItems: 3,
    includeSource: false,
    sourceName: "",
    sourceUrl: "",
    autoApproveSource: true,
    createPersona: true,
    personaName: "",
    personaDescription: "",
    personaSummaryStyle: "",
    personaTargetAudience: "",
    personaPrompt: "",
    slackDeliveryMode: "channel",
    slackChannelConfirmed: false,
    excludeKeywords: [],
    deliveryPreset: "WEEKDAYS",
    deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"],
    deliveryHour: 9,
    ...overrides
  };
}

function renderWithClient(ui: ReactElement) {
  return render(ui, { wrapper: createQueryClientWrapper() });
}

describe("QuickSetupStepSlack — 공개 채널 가이드 배너", () => {
  const defaultOnChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("public_channel 타입일 때 가이드 배너가 표시된다", () => {
    const form = makeForm({ slackDeliveryMode: "channel", slackChannelType: "public_channel" });
    renderWithClient(
      <QuickSetupStepSlack
        form={form}
        onChange={defaultOnChange}
        disabled={false}
        isUserMode={true}
      />
    );

    expect(screen.getByText("ℹ️ 봇이 초대된 채널만 표시됩니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "채널 추가 방법" })).toBeInTheDocument();
  });

  it("'채널 추가 방법' 버튼 클릭 시 가이드 내용이 펼쳐진다", () => {
    const form = makeForm({ slackDeliveryMode: "channel", slackChannelType: "public_channel" });
    renderWithClient(
      <QuickSetupStepSlack
        form={form}
        onChange={defaultOnChange}
        disabled={false}
        isUserMode={true}
      />
    );

    // 초기에는 가이드 내용이 숨겨져 있다
    expect(screen.queryByText("Slack에서 새 채널을 만드세요")).not.toBeInTheDocument();

    // 버튼 클릭 시 가이드가 펼쳐진다
    fireEvent.click(screen.getByRole("button", { name: "채널 추가 방법" }));

    expect(screen.getByText("Slack에서 새 채널을 만드세요")).toBeInTheDocument();
    expect(screen.getByText("닫기")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /새로고침/ })).toBeInTheDocument();
  });

  it("private_channel 타입일 때 가이드 배너가 표시되지 않는다", () => {
    const form = makeForm({
      slackDeliveryMode: "channel",
      slackChannelType: "private_channel",
      slackChannelConfirmed: false
    });

    // private_channel + hasSlackDm=false → 연동 프롬프트가 표시되므로 채널 목록 영역이 없음
    renderWithClient(
      <QuickSetupStepSlack
        form={form}
        onChange={defaultOnChange}
        disabled={false}
        isUserMode={true}
      />
    );

    expect(screen.queryByText("ℹ️ 봇이 초대된 채널만 표시됩니다.")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "채널 추가 방법" })).not.toBeInTheDocument();
  });
});
