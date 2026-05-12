import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PipelineHistory } from "../PipelineHistory";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { PipelineRunRecord } from "@/types/pipeline";

vi.mock("@/services/pipelineService", () => ({
  pipelineService: {
    listRuns: vi.fn(),
  },
}));

import { pipelineService } from "@/services/pipelineService";

/** 테스트용 run 레코드 */
function makeRun(overrides: Partial<PipelineRunRecord> = {}): PipelineRunRecord {
  return {
    id: "run-001",
    categoryId: "cat-1",
    categoryName: "테크",
    status: "SUCCEEDED",
    startedAt: "2026-04-17T09:00:00Z",
    endedAt: "2026-04-17T09:01:00Z",
    durationMs: 60000,
    totalCollected: 10,
    totalSummarized: 8,
    totalDigestSelected: 5,
    postedToSlack: false,
    stepTraces: [],
    errorMessage: null,
    ...overrides,
  };
}

function renderHistory(props: Partial<React.ComponentProps<typeof PipelineHistory>> = {}) {
  return render(<PipelineHistory categories={[]} {...props} />, {
    wrapper: createQueryClientWrapper(),
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pipelineService.listRuns).mockResolvedValue({
    content: [makeRun({ id: "run-001", categoryName: "테크" })],
    totalCount: 1,
  } as Awaited<ReturnType<typeof pipelineService.listRuns>>);
});

describe("PipelineHistory — URL ?runId= 연동", () => {
  it("expandedRunId가 없으면 행 클릭 시 onExpandChange가 해당 runId로 호출된다", async () => {
    const onExpandChange = vi.fn();
    renderHistory({ onExpandChange });

    const row = await screen.findByText("테크");
    fireEvent.click(row.closest("tr")!);

    expect(onExpandChange).toHaveBeenCalledWith("run-001");
  });

  it("expandedRunId와 같은 행을 다시 클릭하면 onExpandChange(null)이 호출된다", async () => {
    const onExpandChange = vi.fn();
    renderHistory({ expandedRunId: "run-001", onExpandChange });

    const row = await screen.findByText("테크");
    fireEvent.click(row.closest("tr")!);

    expect(onExpandChange).toHaveBeenCalledWith(null);
  });

  it("expandedRunId가 일치하는 행은 세부 정보 영역이 렌더링된다", async () => {
    vi.mocked(pipelineService.listRuns).mockResolvedValue({
      content: [
        makeRun({
          id: "run-abc",
          categoryName: "마케팅",
          errorMessage: "테스트 에러 메시지",
          stepTraces: [],
        }),
      ],
      totalCount: 1,
    } as Awaited<ReturnType<typeof pipelineService.listRuns>>);

    renderHistory({ expandedRunId: "run-abc" });

    expect(await screen.findByText("테스트 에러 메시지")).toBeInTheDocument();
  });

  it("onExpandChange가 없으면 로컬 상태로 토글된다", async () => {
    renderHistory();

    const row = await screen.findByText("테크");
    // 상세 영역 없음
    expect(screen.queryByText("상세 정보가 없어요.")).not.toBeInTheDocument();

    fireEvent.click(row.closest("tr")!);

    // 로컬 상태로 펼쳐짐 (stepTraces 없음 → "상세 정보가 없어요." 표시)
    expect(await screen.findByText("상세 정보가 없어요.")).toBeInTheDocument();
  });
});
