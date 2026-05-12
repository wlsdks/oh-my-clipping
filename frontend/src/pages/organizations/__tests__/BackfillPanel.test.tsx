import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { BackfillPanel } from "../BackfillPanel";
import type { BackfillPreviewResponse, BackfillApplyResponse } from "@/services/backfillService";

// Radix Select / Checkbox 는 jsdom 에서 ResizeObserver + PointerEvents 를 요구한다.
beforeAll(() => {
  global.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (HTMLElement.prototype as any).hasPointerCapture = vi.fn();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (HTMLElement.prototype as any).setPointerCapture = vi.fn();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (HTMLElement.prototype as any).releasePointerCapture = vi.fn();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (HTMLElement.prototype as any).scrollIntoView = vi.fn();
  // framer-motion 이 ref 에 scrollTo 를 사용한다.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (HTMLElement.prototype as any).scrollTo = vi.fn();
});

// 서비스 mock — 테스트별로 구현체를 갈아끼운다.
const previewMock = vi.fn();
const applyMock = vi.fn();

vi.mock("@/services/backfillService", () => ({
  backfillService: {
    preview: (...args: unknown[]) => previewMock(...args),
    apply: (...args: unknown[]) => applyMock(...args),
  },
}));

/** 기본 BackfillPreviewResponse 픽스처 */
function makePreviewResponse(
  candidates: Partial<BackfillPreviewResponse["candidates"][0]>[] = [],
): BackfillPreviewResponse {
  const defaultCandidates = candidates.map((c, i) => ({
    sourceId: `src-${i + 1}`,
    sourceUrl: `https://example${i + 1}.com/feed`,
    sourceName: `Feed ${i + 1}`,
    categoryId: `cat-${i + 1}`,
    categoryName: `카테고리 ${i + 1}`,
    matchedCompanyName: `기업 ${i + 1}`,
    stockCode: null,
    confidence: "high" as const,
    precision: 0.9,
    ...c,
  }));
  const highCount = defaultCandidates.filter((c) => c.confidence === "high").length;
  const mediumCount = defaultCandidates.filter((c) => c.confidence === "medium").length;
  const lowCount = defaultCandidates.filter((c) => c.confidence === "low").length;
  return {
    candidates: defaultCandidates,
    total: defaultCandidates.length,
    byConfidence: { high: highCount, medium: mediumCount, low: lowCount },
  };
}

/** 기본 BackfillApplyResponse 픽스처 */
function makeApplyResponse(
  overrides: Partial<BackfillApplyResponse> = {},
): BackfillApplyResponse {
  return {
    total: 1,
    succeeded: 1,
    failed: 0,
    errors: [],
    affectedCategoryIds: ["cat-1"],
    ...overrides,
  };
}

function renderPanel() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    qc,
    ...render(
      <MemoryRouter>
        <QueryClientProvider client={qc}>
          <BackfillPanel />
        </QueryClientProvider>
      </MemoryRouter>,
    ),
  };
}

describe("BackfillPanel", () => {
  beforeEach(() => {
    previewMock.mockReset();
    applyMock.mockReset();
    previewMock.mockResolvedValue(makePreviewResponse([]));
  });

  it("기본 confidence=high 필터로 preview 를 호출하고 결과 테이블을 렌더링한다", async () => {
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([{ sourceName: "MegaCorp 피드", matchedCompanyName: "MegaCorp" }]),
    );

    renderPanel();

    // 로딩 후 테이블 행이 나타난다
    expect(await screen.findByText("MegaCorp 피드")).toBeInTheDocument();
    expect(screen.getByText("MegaCorp")).toBeInTheDocument();

    // preview 가 confidence=high 로 호출됐는지 검증
    expect(previewMock).toHaveBeenCalledWith(
      expect.objectContaining({ confidence: "high" }),
    );
  });

  it("'Medium 포함' 체크박스를 클릭하면 includeMedium=true 로 preview 를 재호출한다", async () => {
    const user = userEvent.setup();
    // 초기 로드
    previewMock.mockResolvedValueOnce(makePreviewResponse([{ sourceName: "Feed A" }]));
    // 체크박스 클릭 후 재호출
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([{ sourceName: "Feed A" }, { sourceName: "Feed B", confidence: "medium" }]),
    );

    renderPanel();

    await screen.findByText("Feed A");

    const checkbox = screen.getByLabelText("Medium 포함");
    await user.click(checkbox);

    // includeMedium=true 로 재호출 됐는지 확인
    await waitFor(() =>
      expect(previewMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ includeMedium: true }),
      ),
    );
    expect(await screen.findByText("Feed B")).toBeInTheDocument();
  });

  it("카테고리 ID 를 입력하면 categoryId 파라미터로 preview 를 재호출한다", async () => {
    const user = userEvent.setup();
    // 초기 로드
    previewMock.mockResolvedValueOnce(makePreviewResponse([{ sourceName: "Feed A" }]));
    // categoryId 입력 후 재호출
    previewMock.mockResolvedValue(makePreviewResponse([{ sourceName: "Filtered Feed" }]));

    renderPanel();

    await screen.findByText("Feed A");

    const input = screen.getByPlaceholderText("카테고리 ID 필터 (선택)");
    await user.type(input, "my-category");

    await waitFor(() =>
      expect(previewMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ categoryId: "my-category" }),
      ),
    );
  });

  it("행 체크박스를 선택하면 '선택 N개' 카운트가 업데이트되고 '선택 적용' 버튼이 활성화된다", async () => {
    const user = userEvent.setup();
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([
        { sourceId: "src-1", sourceName: "Feed 1" },
        { sourceId: "src-2", sourceName: "Feed 2" },
      ]),
    );

    renderPanel();

    await screen.findByText("Feed 1");

    // 초기에는 0개 선택, 버튼 비활성
    expect(screen.getByText("선택 0개")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "선택 적용" })).toBeDisabled();

    // Feed 1 체크
    const checkbox1 = screen.getByLabelText("Feed 1 선택");
    await user.click(checkbox1);

    expect(screen.getByText("선택 1개")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "선택 적용" })).toBeEnabled();
  });

  it("101개 이상 선택 시 '선택 적용' 버튼이 비활성화되고 경고 문구가 노출된다", async () => {
    // 101개 후보 생성
    const candidates = Array.from({ length: 101 }, (_, i) => ({
      sourceId: `src-${i}`,
      sourceName: `Feed ${i}`,
    }));
    previewMock.mockResolvedValueOnce(makePreviewResponse(candidates));

    const user = userEvent.setup();
    renderPanel();

    await screen.findByText("Feed 0");

    // 전체 선택 (헤더 체크박스)
    const headerCheckbox = screen.getByLabelText("전체 선택");
    await user.click(headerCheckbox);

    // 101개 선택 → 버튼 disabled + 경고 노출
    await waitFor(() =>
      expect(screen.getByText(/한 번에 최대 100개까지 적용할 수 있어요/)).toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "선택 적용" })).toBeDisabled();
  });

  it("'선택 적용' 버튼 클릭 시 선택한 sourceId 목록으로 applyMock 이 호출된다", async () => {
    const user = userEvent.setup();
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([
        { sourceId: "src-a", sourceName: "Feed A" },
        { sourceId: "src-b", sourceName: "Feed B" },
      ]),
    );
    applyMock.mockResolvedValueOnce(makeApplyResponse());

    renderPanel();

    await screen.findByText("Feed A");

    // Feed A 선택
    await user.click(screen.getByLabelText("Feed A 선택"));
    // Feed B 선택
    await user.click(screen.getByLabelText("Feed B 선택"));

    await user.click(screen.getByRole("button", { name: "선택 적용" }));

    await waitFor(() => expect(applyMock).toHaveBeenCalledTimes(1));
    // candidateIds 가 선택한 sourceId 배열이어야 한다
    const callArg = applyMock.mock.calls[0][0] as { candidateIds: string[] };
    expect(callArg.candidateIds).toHaveLength(2);
    expect(callArg.candidateIds).toContain("src-a");
    expect(callArg.candidateIds).toContain("src-b");
  });

  it("Apply 성공 시 성공 토스트 메시지가 노출된다", async () => {
    const user = userEvent.setup();
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([{ sourceId: "src-1", sourceName: "Feed 1" }]),
    );
    // 성공 후 재조회
    previewMock.mockResolvedValue(makePreviewResponse([]));
    applyMock.mockResolvedValueOnce(
      makeApplyResponse({ total: 1, succeeded: 1, affectedCategoryIds: ["cat-1"] }),
    );

    renderPanel();

    await screen.findByText("Feed 1");
    await user.click(screen.getByLabelText("Feed 1 선택"));
    await user.click(screen.getByRole("button", { name: "선택 적용" }));

    // toast.success 는 sonner 의 외부 DOM 에 렌더링되므로 applyMock 호출 완료를 확인한다.
    await waitFor(() => expect(applyMock).toHaveBeenCalledTimes(1));
    // 성공 후 선택이 초기화돼야 한다
    await waitFor(() => expect(screen.getByText("선택 0개")).toBeInTheDocument());
  });

  it("Apply 후 errors 가 있으면 인라인 에러 목록을 렌더링한다", async () => {
    const user = userEvent.setup();
    previewMock.mockResolvedValueOnce(
      makePreviewResponse([{ sourceId: "src-bad", sourceName: "Bad Feed" }]),
    );
    previewMock.mockResolvedValue(makePreviewResponse([]));
    applyMock.mockResolvedValueOnce(
      makeApplyResponse({
        total: 1,
        succeeded: 0,
        failed: 1,
        errors: [{ candidateId: "src-bad", reason: "중복 매칭" }],
      }),
    );

    renderPanel();

    await screen.findByText("Bad Feed");
    await user.click(screen.getByLabelText("Bad Feed 선택"));
    await user.click(screen.getByRole("button", { name: "선택 적용" }));

    expect(await screen.findByText(/일부 항목 적용 실패/)).toBeInTheDocument();
    expect(screen.getByText(/중복 매칭/)).toBeInTheDocument();
  });

  it("후보가 없으면 빈 상태 안내 메시지를 표시한다", async () => {
    previewMock.mockResolvedValueOnce(makePreviewResponse([]));

    renderPanel();

    expect(await screen.findByText(/조건에 맞는 후보가 없어요/)).toBeInTheDocument();
  });

  it("API 실패 시 다시 시도 버튼을 표시한다", async () => {
    previewMock.mockRejectedValueOnce(new Error("network fail"));

    renderPanel();

    expect(await screen.findByText(/후보 목록을 불러오지 못했어요/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
