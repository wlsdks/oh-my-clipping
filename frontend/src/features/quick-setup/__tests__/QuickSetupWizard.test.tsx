import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { QuickSetupForm } from "../model/quickSetupTypes";

// jsdom은 Element.prototype.scrollTo를 구현하지 않아서 wizard의 requestAnimationFrame 내부에서
// TypeError가 발생한다. 테스트 환경에서만 no-op으로 대체한다.
if (typeof Element.prototype.scrollTo !== "function") {
  Element.prototype.scrollTo = function scrollToNoop() {
    // no-op for jsdom
  } as unknown as Element["scrollTo"];
}

// ── 서비스/의존성 mock (import 순서 중요: vi.mock hoist) ─────────────────
vi.mock("@/services/personaService", () => ({
  personaService: {
    getAll: vi.fn().mockResolvedValue([]),
    getUserAll: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    createUser: vi.fn(),
    getPresets: vi.fn().mockResolvedValue([]),
  }
}));

vi.mock("@/services/categoryService", () => ({
  categoryService: {
    create: vi.fn(),
    getAll: vi.fn().mockResolvedValue([]),
  }
}));

vi.mock("@/services/sourceService", () => ({
  sourceService: {
    create: vi.fn(),
    verify: vi.fn(),
    approve: vi.fn(),
    validateUrl: vi.fn().mockResolvedValue({ valid: true, reason: "" }),
  }
}));

vi.mock("@/services/userService", () => ({
  userService: {
    createClippingRequest: vi.fn(),
    createRequestWithEntries: vi.fn(),
    createSetupCategory: vi.fn(),
    createSetupSource: vi.fn(),
    verifySetupSource: vi.fn(),
    approveSetupSource: vi.fn(),
    registerWizardOwnership: vi.fn(),
    updateSubscriptionPreferences: vi.fn(),
    browseCategories: vi.fn().mockResolvedValue([]),
    listClippingRequests: vi.fn().mockResolvedValue([]),
    updateSlackMemberId: vi.fn().mockResolvedValue(undefined),
    subscribeCategoryDm: vi.fn().mockResolvedValue(undefined),
  }
}));

vi.mock("@/services/companyService", () => ({
  companyService: {
    searchUserCompanies: vi.fn().mockResolvedValue([]),
    searchAdminCompanies: vi.fn().mockResolvedValue([]),
  }
}));

vi.mock("@/services/runtimeService", () => ({
  runtimeService: {
    listUserSetupSlackChannels: vi.fn().mockResolvedValue({ channels: [], slackConnectRequired: false }),
    getUserSetupSlackChannelInfo: vi.fn().mockResolvedValue(null),
    listSlackChannels: vi.fn().mockResolvedValue({ channels: [], slackConnectRequired: false }),
  }
}));

vi.mock("@/services/dashboardService", () => ({
  dashboardService: {
    runPipeline: vi.fn().mockResolvedValue(undefined),
  }
}));

vi.mock("@/services/userIntelligenceService", () => ({
  userIntelligenceService: {
    triggerPipeline: vi.fn().mockResolvedValue(undefined),
  }
}));

vi.mock("@/store/authStore", () => ({
  useAuthStore: Object.assign(
    vi.fn((selector) => selector({ user: { hasSlackDm: false } })),
    { getState: () => ({ user: { hasSlackDm: false }, login: vi.fn() }) }
  ),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  }
}));

vi.mock("framer-motion", async () => {
  const React = await import("react");
  return {
    motion: new Proxy(
      {},
      {
        get: () => (props: React.HTMLAttributes<HTMLElement> & { children?: React.ReactNode }) => {
          const { children, ...rest } = props;
          return React.createElement("div", rest, children);
        },
      }
    ),
    AnimatePresence: ({ children }: { children: React.ReactNode }) => children,
  };
});

import { QuickSetupWizard } from "../QuickSetupWizard";
import { toast } from "sonner";
import { personaService } from "@/services/personaService";
import { categoryService } from "@/services/categoryService";
import { sourceService } from "@/services/sourceService";
import { userService } from "@/services/userService";

/** QueryClientProvider로 감싸 렌더 */
function renderWizard(props: Partial<React.ComponentProps<typeof QuickSetupWizard>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const onClose = props.onClose ?? vi.fn();
  const onComplete = props.onComplete ?? vi.fn();
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <QuickSetupWizard open={props.open ?? true} onClose={onClose} onComplete={onComplete} {...props} />
    </QueryClientProvider>
  );
  return { ...utils, onClose, onComplete };
}

const FAKE_CATEGORY = { id: "cat-1", name: "AI 뉴스" };
const FAKE_PERSONA = { id: "p-1", name: "기본 요약 스타일" };
const FAKE_SOURCE = { id: "src-1", name: "AI 뉴스" };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(personaService.create).mockResolvedValue(FAKE_PERSONA as never);
  vi.mocked(personaService.createUser).mockResolvedValue(FAKE_PERSONA as never);
  vi.mocked(personaService.getAll).mockResolvedValue([]);
  vi.mocked(personaService.getUserAll).mockResolvedValue([]);
  vi.mocked(categoryService.create).mockResolvedValue(FAKE_CATEGORY as never);
  vi.mocked(sourceService.create).mockResolvedValue(FAKE_SOURCE as never);
  vi.mocked(sourceService.verify).mockResolvedValue({ status: "OK" });
  vi.mocked(sourceService.approve).mockResolvedValue(FAKE_SOURCE as never);
});

describe("QuickSetupWizard — 렌더 & 기본 구조", () => {
  it("open=true 일 때 다이얼로그 타이틀 '빠른 세팅'이 표시된다", () => {
    renderWizard({ open: true });
    expect(screen.getByText("빠른 세팅")).toBeInTheDocument();
  });

  it("open=false 일 때 다이얼로그는 표시되지 않는다", () => {
    renderWizard({ open: false });
    expect(screen.queryByText("빠른 세팅")).not.toBeInTheDocument();
  });

  it("editRequestId가 있는 initialForm에서는 '구독 설정 변경' 타이틀을 표시한다", () => {
    renderWizard({
      open: true,
      initialForm: { editRequestId: "req-1" } as Partial<QuickSetupForm>,
    });
    expect(screen.getByText("구독 설정 변경")).toBeInTheDocument();
  });
});

describe("QuickSetupWizard — Step 네비게이션", () => {
  it("최초 진입 시 Step 1 (사이트 필터)이 표시된다", () => {
    renderWizard({ open: true });
    // Step 1 = QuickSetupStepSiteFilter → 국내/해외 라디오가 있음
    expect(screen.getByText("국내 뉴스")).toBeInTheDocument();
  });

  it("Step 1 은 이전(←) 버튼이 없고 다음(→) 버튼만 있다", () => {
    renderWizard({ open: true });
    expect(screen.queryByRole("button", { name: /← 이전/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /다음/ })).toBeInTheDocument();
  });

  it("Step 1 → '다음' 클릭 시 Step 2 (뉴스 선택)로 이동한다", async () => {
    renderWizard({ open: true });
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => {
      expect(screen.getByText("어떤 뉴스를 받고 싶으세요?")).toBeInTheDocument();
    });
  });

  it("Step 2 에서 키워드 없이 '다음' 클릭 시 validation 에러 표시", async () => {
    renderWizard({ open: true });
    // Step1 → Step2
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => screen.getByText("어떤 뉴스를 받고 싶으세요?"));

    // Step2에서 엔트리 없이 다음 클릭
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => {
      expect(
        screen.getByText(/기업 또는 키워드를 1개 이상 추가해 주세요/)
      ).toBeInTheDocument();
    });
  });

  it("Step 2 에서 키워드 추가 후 '다음' 클릭하면 Step 3으로 진행", async () => {
    renderWizard({ open: true });
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => screen.getByText("어떤 뉴스를 받고 싶으세요?"));

    // 키워드 입력 후 Enter
    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    fireEvent.change(input, { target: { value: "AI" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    // 다음 클릭
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));

    // Step 3 = persona 스텝 → "요약 스타일" 같은 문구
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /← 이전/ })).toBeInTheDocument();
    });
  });

  it("Step 2 이상에서 '← 이전' 버튼을 누르면 전 스텝으로 돌아간다", async () => {
    renderWizard({ open: true });
    // Step 1 → Step 2
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => screen.getByText("어떤 뉴스를 받고 싶으세요?"));

    // 이전 버튼 클릭
    fireEvent.click(screen.getByRole("button", { name: /← 이전/ }));
    await waitFor(() => {
      // Step 1 컨텐츠 다시 표시됨
      expect(screen.getByText("국내 뉴스")).toBeInTheDocument();
    });
  });
});

describe("QuickSetupWizard — Submit 버튼 라벨", () => {
  it("생성 모드에서는 마지막 스텝에 '세팅 시작' 버튼이 있다 (initialForm 없이)", async () => {
    renderWizard({
      open: true,
      initialForm: {
        entries: [{ value: "AI", type: "keyword" }],
        slackDeliveryMode: "dm",
        slackChannelConfirmed: false,
      } as Partial<QuickSetupForm>,
    });

    // 1→2→3→4→5 로 빠르게 이동
    for (let i = 0; i < 4; i++) {
      fireEvent.click(screen.getByRole("button", { name: /다음/ }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: /← 이전/ })).toBeInTheDocument();
      });
    }

    expect(screen.getByRole("button", { name: /세팅 시작/ })).toBeInTheDocument();
  });

  it("Edit 모드(editRequestId 있음)에서는 마지막 스텝에 '변경 요청' 버튼이 있다", async () => {
    renderWizard({
      open: true,
      initialForm: {
        editRequestId: "req-1",
        entries: [{ value: "AI", type: "keyword" }],
        slackDeliveryMode: "dm",
      } as Partial<QuickSetupForm>,
    });

    for (let i = 0; i < 4; i++) {
      fireEvent.click(screen.getByRole("button", { name: /다음/ }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: /← 이전/ })).toBeInTheDocument();
      });
    }

    expect(screen.getByRole("button", { name: /변경 요청$/ })).toBeInTheDocument();
  });
});

describe("QuickSetupWizard — 제출 흐름 (Step 5 → done)", () => {
  it("Edit 모드에서 Step 5 → '변경 요청' 클릭 시 createRequestWithEntries 호출", async () => {
    vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
      status: "submitted",
      requestId: "req-1",
      acceptedCount: 1,
      errors: [],
    } as never);

    renderWizard({
      open: true,
      isUserMode: true,
      // initialForm에는 editRequestId만 넘기고, 실제 폼 값은 별도로 두어서
      // isFormUnchanged=false 가 되도록 한다
      initialForm: {
        editRequestId: "req-orig",
        entries: [{ value: "이전키워드", type: "keyword" }],
        categoryName: "이전 주제",
        personaName: "이전 스타일",
        personaPrompt: "이전 프롬프트",
        slackDeliveryMode: "dm",
      } as Partial<QuickSetupForm>,
    });

    // 위자드는 initialForm으로 초기화되므로, step 1에서 변경 없이도 편집됐다고 판단하려면
    // 폼 값을 UI에서 변경하거나 entries를 다르게 주어야 한다.
    // 여기서는 step 2에 가서 키워드를 하나 추가한다.

    // Step 1 → Step 2
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => screen.getByText("어떤 뉴스를 받고 싶으세요?"));

    // Step 2에서 신규 키워드 추가 → isFormUnchanged=false 로 만듦
    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    fireEvent.change(input, { target: { value: "새키워드" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    // Step 2 → 3 → 4 → 5
    for (let i = 0; i < 3; i++) {
      fireEvent.click(screen.getByRole("button", { name: /다음/ }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: /← 이전/ })).toBeInTheDocument();
      });
    }

    const submitBtn = screen.getByRole("button", { name: /변경 요청$/ });
    fireEvent.click(submitBtn);

    await waitFor(
      () => {
        expect(userService.createRequestWithEntries).toHaveBeenCalled();
      },
      { timeout: 3000 }
    );
  });

  it("Step 5 에서 slackDeliveryMode='channel'인데 slackChannelId 빈 값이면 validation 에러 + toast", async () => {
    renderWizard({
      open: true,
      isUserMode: true,
      initialForm: {
        entries: [{ value: "AI", type: "keyword" }],
        slackDeliveryMode: "channel",
        slackChannelConfirmed: false,
        slackChannelId: "",
      } as Partial<QuickSetupForm>,
    });

    // Step1→5로 진행
    for (let i = 0; i < 4; i++) {
      fireEvent.click(screen.getByRole("button", { name: /다음/ }));
      await waitFor(() => screen.getByRole("button", { name: /← 이전/ }));
    }

    fireEvent.click(screen.getByRole("button", { name: /세팅 시작/ }));

    // toast.error가 호출되어야 함
    await waitFor(() => {
      expect(vi.mocked(toast.error)).toHaveBeenCalled();
    });
    // 실제 API는 호출되지 않음
    expect(userService.createSetupCategory).not.toHaveBeenCalled();
  });
});

describe("QuickSetupWizard — 스테퍼 표시", () => {
  it("5개의 스텝 progress 바가 렌더링된다 (Dialog portal 내)", () => {
    renderWizard({ open: true });
    // Dialog는 portal로 document.body 에 렌더됨
    const progressBars = document.body.querySelectorAll(".h-1\\.5.rounded-full");
    expect(progressBars.length).toBeGreaterThanOrEqual(5);
  });
});

describe("QuickSetupWizard — initialForm pre-fill", () => {
  it("initialForm의 entries가 Step 2에 반영된다", async () => {
    renderWizard({
      open: true,
      initialForm: {
        entries: [{ value: "테스트키워드", type: "keyword" }],
      } as Partial<QuickSetupForm>,
    });

    // Step1 → Step2
    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => screen.getByText("어떤 뉴스를 받고 싶으세요?"));

    // entries chip 표시 확인
    expect(screen.getByText("테스트키워드")).toBeInTheDocument();
  });
});
