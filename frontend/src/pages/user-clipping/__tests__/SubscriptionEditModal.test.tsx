import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { UserClippingRequest, UserSubscriptionPreference } from "@/types/user";

vi.mock("@/services/userService", () => ({
  userService: {
    updateSubscriptionPreferences: vi.fn(),
    renameClippingRequest: vi.fn(),
  },
}));

// sonner의 toast는 jsdom에서 내부적으로 Radix/Portal을 건드려 노이즈 로그가 나올 수 있어 명시 mock.
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}));

import { userService } from "@/services/userService";
import { toast } from "sonner";
import { SubscriptionEditModal } from "../SubscriptionEditModal";

const baseRequest: UserClippingRequest = {
  id: "req-1",
  requesterUserId: "u-1",
  requestName: "핀테크 뉴스",
  sourceName: "Example Economic Times",
  sourceUrl: "https://example.com",
  slackChannelId: "C123",
  personaName: "마케터",
  personaPrompt: "프롬프트",
  summaryStyle: null,
  targetAudience: null,
  selectedPresetId: null,
  requestNote: null,
  status: "APPROVED",
  reviewNote: null,
  reviewedByUserId: null,
  reviewedAt: null,
  approvedCategoryId: "cat-1",
  approvedCategoryName: "핀테크",
  approvedPersonaId: "p1",
  approvedSourceId: "s1",
  createdAt: "2026-04-01T00:00:00Z",
  updatedAt: "2026-04-01T00:00:00Z",
  deliveryState: "ACTIVE",
  collectingReady: true,
  totalSourceCount: 1,
  readySourceCount: 1,
  representativeSourceVerificationStatus: null,
};

function makePref(overrides: Partial<UserSubscriptionPreference> = {}): UserSubscriptionPreference {
  return {
    requestId: "req-1",
    categoryId: "cat-1",
    requestName: "핀테크 뉴스",
    isActive: true,
    maxItems: 5,
    excludeKeywords: [],
    includeThreshold: 0.55,
    deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"],
    deliveryHour: 9,
    deliveryPreset: "WEEKDAYS",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

function renderModal(
  props: Partial<React.ComponentProps<typeof SubscriptionEditModal>> = {}
) {
  const onClose = vi.fn();
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  const utils = render(
    <QueryClientProvider client={qc}>
      <SubscriptionEditModal
        open
        request={baseRequest}
        preference={makePref()}
        onClose={onClose}
        {...props}
      />
    </QueryClientProvider>
  );
  return { ...utils, onClose, qc };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userService.updateSubscriptionPreferences).mockResolvedValue(makePref());
  vi.mocked(userService.renameClippingRequest).mockResolvedValue(baseRequest);
});

describe("SubscriptionEditModal — 초기 렌더 (기본값 동기화)", () => {
  it("preference 값이 기본 폼에 주입되어 초기 선택이 표시된다", () => {
    renderModal({ preference: makePref({ maxItems: 3 }) });
    // 위자드와 동일한 옵션 [1, 3, 5] 중 주입된 '3건'이 표시되어야 한다.
    expect(screen.getByRole("button", { name: "3건" })).toBeInTheDocument();
  });

  it("preference가 null이면 위자드 기본값(3건, 평일)과 동일하게 폴백한다", () => {
    renderModal({ preference: null });
    expect(screen.getByRole("button", { name: "3건" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "평일만" })).toBeInTheDocument();
  });

  it("위자드에 없는 옵션(10/15/20건)은 더 이상 렌더되지 않는다", () => {
    renderModal({ preference: makePref() });
    expect(screen.queryByRole("button", { name: "10건" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "15건" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "20건" })).not.toBeInTheDocument();
  });

  it("중요도 필터 섹션은 어드민 정책 영역이므로 유저 모달에 노출되지 않는다", () => {
    renderModal({ preference: makePref({ includeThreshold: 0.75 }) });
    expect(screen.queryByText("뉴스 중요도 필터")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "낮음" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "보통" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "높음" })).not.toBeInTheDocument();
  });

  it("정확한 슬롯이 아닌 deliveryHour(예: 10)는 가장 가까운 슬롯(8)으로 보정된다", () => {
    // DELIVERY_SLOTS = [8, 12, 18] — 10은 8과 가장 가깝다.
    renderModal({ preference: makePref({ deliveryHour: 10 }) });
    expect(screen.getByRole("button", { name: "오전 8시" })).toBeInTheDocument();
  });

  it("백엔드가 내려준 deliveryHour=8 은 해당 pill 을 바로 pre-select 한다", () => {
    // 백엔드가 글로벌 user_delivery_schedules 값을 merge 해 내려주는 경로를 모사.
    renderModal({ preference: makePref({ deliveryHour: 8, deliveryPreset: "WEEKDAYS" }) });
    expect(screen.getByRole("button", { name: "오전 8시" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "평일만" })).toBeInTheDocument();
  });
});

describe("SubscriptionEditModal — 제외 키워드", () => {
  it("입력 후 Enter를 누르면 칩이 추가되고 입력창이 비워진다", async () => {
    renderModal({ preference: makePref({ excludeKeywords: [] }) });
    const input = screen.getByPlaceholderText(/Enter로 추가/);
    await userEvent.type(input, "광고{Enter}");

    expect(await screen.findByText("광고")).toBeInTheDocument();
    expect((input as HTMLInputElement).value).toBe("");
  });

  it("이미 존재하는 키워드를 다시 추가하면 중복이 생성되지 않는다", async () => {
    renderModal({ preference: makePref({ excludeKeywords: ["광고"] }) });
    const input = screen.getByPlaceholderText(/Enter로 추가/);
    await userEvent.type(input, "광고{Enter}");

    // 여전히 1개만 렌더되어야 한다 (제거 버튼 기준)
    const removeButtons = screen.getAllByRole("button", { name: /광고 제거/ });
    expect(removeButtons).toHaveLength(1);
  });

  it("칩 옆 X 버튼을 클릭하면 키워드가 제거된다", async () => {
    renderModal({ preference: makePref({ excludeKeywords: ["광고", "후원"] }) });

    const removeAd = screen.getByRole("button", { name: "광고 제거" });
    await userEvent.click(removeAd);

    expect(screen.queryByText("광고")).not.toBeInTheDocument();
    expect(screen.getByText("후원")).toBeInTheDocument();
  });
});

describe("SubscriptionEditModal — 발송 설정 프리셋", () => {
  it("'매일' 선택 시 내부 상태가 7일로 확장된다 (저장 호출로 간접 확인)", async () => {
    const onClose = vi.fn();
    renderModal({ preference: makePref({ deliveryPreset: "WEEKDAYS" }), onClose });

    await userEvent.click(screen.getByRole("button", { name: "매일" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(userService.updateSubscriptionPreferences).toHaveBeenCalled();
    });
    const [, payload] = vi.mocked(userService.updateSubscriptionPreferences).mock.calls[0];
    expect(payload.deliveryPreset).toBe("EVERYDAY");
    expect(payload.deliveryDays).toHaveLength(7);
  });

  it("'직접 선택' 모드에서 요일 없이 저장하면 warning toast를 띄우고 API는 호출되지 않는다", async () => {
    renderModal({ preference: makePref({ deliveryPreset: "WEEKDAYS" }) });

    await userEvent.click(screen.getByRole("button", { name: "직접 선택" }));
    // 초기 WEEKDAYS 5일 전부 해제
    for (const d of ["월", "화", "수", "목", "금"]) {
      await userEvent.click(screen.getByRole("button", { name: d }));
    }
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(toast.warning).toHaveBeenCalledWith("최소 1개 요일을 선택해주세요");
    expect(userService.updateSubscriptionPreferences).not.toHaveBeenCalled();
  });
});

describe("SubscriptionEditModal — 배치 저장 (PR #328)", () => {
  it("이름이 바뀌지 않았으면 rename API는 호출하지 않고 preferences만 저장한다", async () => {
    const onClose = vi.fn();
    renderModal({ preference: makePref(), onClose });

    await userEvent.click(screen.getByRole("button", { name: "1건" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(userService.updateSubscriptionPreferences).toHaveBeenCalledTimes(1);
    });
    expect(userService.renameClippingRequest).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
    expect(toast.success).toHaveBeenCalledWith("구독 설정을 저장했어요");
  });

  it("저장 payload 에 어드민 정책 필드(includeThreshold)를 포함하지 않는다", async () => {
    renderModal({ preference: makePref({ includeThreshold: 0.75 }) });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(userService.updateSubscriptionPreferences).toHaveBeenCalled();
    });
    const [, payload] = vi.mocked(userService.updateSubscriptionPreferences).mock.calls[0];
    expect(payload).not.toHaveProperty("includeThreshold");
  });

  it("저장 실패 시 error toast를 띄우고 모달을 닫지 않는다", async () => {
    vi.mocked(userService.updateSubscriptionPreferences).mockRejectedValue(
      new Error("boom")
    );
    const onClose = vi.fn();
    renderModal({ preference: makePref(), onClose });

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled();
    });
    expect(onClose).not.toHaveBeenCalled();
  });
});
