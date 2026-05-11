import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PresetDetailModal } from "../PresetDetailModal";
import type { Persona } from "@/types/persona";

// ResizeObserver polyfill for jsdom
beforeAll(() => {
  global.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

// mock personaService to prevent actual API calls
vi.mock("@/services/personaService", () => ({
  personaService: {
    getAll: vi.fn().mockResolvedValue([]),
    getById: vi.fn().mockResolvedValue({ id: "p1", updatedAt: "2026-03-01T00:00:00Z" }),
    create: vi.fn().mockResolvedValue({ id: "new" }),
    update: vi.fn().mockResolvedValue({ id: "p1" }),
    delete: vi.fn().mockResolvedValue(undefined),
    getVersions: vi.fn().mockResolvedValue([]),
    getVersionDetail: vi.fn(),
    rollback: vi.fn(),
    getPresets: vi.fn().mockResolvedValue([])
  }
}));

// Editing presence는 heartbeat/polling이 모달 생명주기에 붙어 있으므로 테스트에서는 모두 no-op 처리.
vi.mock("@/services/editingPresenceService", () => ({
  editingPresenceService: {
    heartbeat: vi.fn().mockResolvedValue(""),
    release: vi.fn().mockResolvedValue(""),
    listActive: vi.fn().mockResolvedValue([])
  }
}));

function makePersona(overrides: Partial<Persona> = {}): Persona {
  return {
    id: "p1",
    name: "테스트 프리셋",
    description: "설명",
    systemPrompt: "prompt",
    summaryStyle: null,
    targetAudience: null,
    maxItems: 5,
    language: "ko",
    isActive: true,
    isPreset: true,
    currentVersion: 2,
    previewTitle: null,
    previewSource: null,
    previewBody: null,
    tone: null,
    lengthPref: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides
  };
}

function renderModal(props: Partial<React.ComponentProps<typeof PresetDetailModal>> = {}) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <QueryClientProvider client={qc}>
      <PresetDetailModal persona={makePersona()} subscriptionCount={0} open={true} onOpenChange={() => {}} {...props} />
    </QueryClientProvider>
  );
}

describe("PresetDetailModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("편집 탭과 이력 탭을 전환할 수 있다", () => {
    renderModal();

    // 편집 탭이 기본 활성
    expect(screen.getByText("편집")).toBeInTheDocument();
    expect(screen.getByText("이력")).toBeInTheDocument();

    // 이력 탭 클릭
    fireEvent.click(screen.getByText("이력"));
    // 이력 탭 내용이 표시
    expect(screen.getByText("이력이 없어요")).toBeInTheDocument();
  });

  it("구독이 있으면 삭제 버튼이 비활성이다", () => {
    renderModal({ persona: makePersona({ isPreset: false }), subscriptionCount: 3 });

    const deleteButton = screen.getByRole("button", { name: "삭제" });
    expect(deleteButton).toBeDisabled();
  });

  it("생성 모드에서는 이력 탭이 비활성이다", () => {
    renderModal({ persona: null });

    const historyTab = screen.getByText("이력");
    expect(historyTab).toBeDisabled();
  });
});
