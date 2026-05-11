import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { RevisionHistoryList } from "@/components/shared/RevisionHistoryList";

// historyService를 mock 해 UI 상호작용만 집중 검증한다.
vi.mock("@/services/historyService", () => ({
  historyService: {
    getHistory: vi.fn(),
    restore: vi.fn()
  }
}));

// toast는 UI 단언 범위 바깥이므로 stub.
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() }
}));

import { historyService } from "@/services/historyService";

function renderWithQueryClient(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } }
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

const sampleRevisions = [
  {
    revisionId: "r2",
    revisionNumber: 2,
    editorId: "admin",
    editorName: "관리자",
    changedFields: ["name", "systemPrompt"],
    createdAt: new Date().toISOString()
  },
  {
    revisionId: "r1",
    revisionNumber: 1,
    editorId: "admin",
    editorName: "관리자",
    changedFields: ["name"],
    createdAt: new Date().toISOString()
  }
];

describe("RevisionHistoryList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("접힘 상태에서는 revision을 조회하지 않는다", () => {
    vi.mocked(historyService.getHistory).mockResolvedValue(sampleRevisions);
    renderWithQueryClient(
      <RevisionHistoryList resource="persona" resourceId="p1" currentUpdatedAt={null} />
    );
    expect(historyService.getHistory).not.toHaveBeenCalled();
  });

  it("펼치면 revision 목록이 노출되고 되돌리기 버튼이 렌더링된다", async () => {
    vi.mocked(historyService.getHistory).mockResolvedValue(sampleRevisions);
    renderWithQueryClient(
      <RevisionHistoryList resource="persona" resourceId="p1" currentUpdatedAt="2026-04-17T10:00:00Z" />
    );

    fireEvent.click(screen.getByRole("button", { name: /변경 이력/ }));

    await waitFor(() => {
      expect(screen.getByText(/v2/)).toBeInTheDocument();
      expect(screen.getByText(/v1/)).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: /되돌리기/ })).toHaveLength(2);
  });

  it("변경 이력이 비어 있으면 EmptyState를 보여준다", async () => {
    vi.mocked(historyService.getHistory).mockResolvedValue([]);
    renderWithQueryClient(
      <RevisionHistoryList
        resource="persona"
        resourceId="p-empty"
        currentUpdatedAt={null}
        defaultOpen
      />
    );
    await waitFor(() => {
      expect(screen.getByText("변경 이력이 없어요")).toBeInTheDocument();
    });
  });

  it("되돌리기 버튼 클릭 시 ConfirmModal이 나타난다", async () => {
    vi.mocked(historyService.getHistory).mockResolvedValue(sampleRevisions);
    renderWithQueryClient(
      <RevisionHistoryList
        resource="persona"
        resourceId="p1"
        currentUpdatedAt="2026-04-17T10:00:00Z"
        defaultOpen
      />
    );
    await waitFor(() => expect(screen.getByText(/v2/)).toBeInTheDocument());

    fireEvent.click(screen.getAllByRole("button", { name: /되돌리기/ })[0]);

    expect(await screen.findByText("이 버전으로 되돌릴까요?")).toBeInTheDocument();
  });
});
