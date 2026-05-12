import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { DigestDiffListResponse, DigestDiffEntry } from "@/services/digestDiffService";

// ── 서비스 mock ──────────────────────────────────────────────────────────────
vi.mock("@/services/digestDiffService", () => ({
  digestDiffService: {
    list: vi.fn(),
  },
}));

import { digestDiffService } from "@/services/digestDiffService";
import { DigestDiffPage } from "../DigestDiffPage";

/* ── 헬퍼 ── */

function makeEntry(overrides: Partial<DigestDiffEntry> = {}): DigestDiffEntry {
  return {
    id: "ddl-1",
    categoryId: "cat-abc",
    digestDate: "2026-04-20",
    legacySummary: "Legacy summary text",
    newSummary: "New summary text",
    newMode: "DUAL_SECTION",
    sectionsCount: 2,
    articlesCount: 5,
    crossMatchCount: 1,
    createdAt: "2026-04-20T02:30:00Z",
    ...overrides,
  };
}

function makeListResponse(
  overrides: Partial<DigestDiffListResponse> = {},
): DigestDiffListResponse {
  return {
    content: [makeEntry()],
    totalElements: 1,
    page: 0,
    size: 50,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <DigestDiffPage />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

/* ── 테스트 ── */

describe("DigestDiffPage — 초기 상태", () => {
  it("카테고리 ID 를 입력하지 않으면 안내 메시지가 표시되고 API 를 호출하지 않는다", async () => {
    renderPage();

    // 안내 문구 렌더 확인
    expect(
      screen.getByText(/카테고리 ID 를 입력하면 diff 기록을 조회할 수 있습니다/),
    ).toBeInTheDocument();

    // API 가 호출되지 않아야 한다
    expect(vi.mocked(digestDiffService.list)).not.toHaveBeenCalled();
  });

  it("헤더에 '발송 모드 diff' 제목이 표시된다", () => {
    renderPage();
    expect(screen.getByRole("heading", { name: "발송 모드 diff" })).toBeInTheDocument();
  });

  it("카테고리 ID 입력 필드와 날짜 필드가 렌더된다", () => {
    renderPage();
    expect(screen.getByLabelText("카테고리 ID")).toBeInTheDocument();
    expect(screen.getByLabelText("시작일")).toBeInTheDocument();
    expect(screen.getByLabelText("종료일")).toBeInTheDocument();
  });
});

describe("DigestDiffPage — 데이터 조회", () => {
  it("카테고리 ID 입력 후 데이터가 테이블에 렌더된다", async () => {
    vi.mocked(digestDiffService.list).mockResolvedValue(makeListResponse());

    renderPage();

    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-abc");

    // 테이블 행이 렌더되면 digestDate 표시 확인
    expect(await screen.findByText("2026-04-20")).toBeInTheDocument();
    // 모드 뱃지 확인
    expect(screen.getByText("DUAL_SECTION")).toBeInTheDocument();
    // 섹션 수 확인
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("빈 응답이 오면 empty state 메시지가 표시된다", async () => {
    vi.mocked(digestDiffService.list).mockResolvedValue(
      makeListResponse({ content: [], totalElements: 0 }),
    );

    renderPage();

    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-empty");

    expect(
      await screen.findByText("이 기간에 기록된 diff 가 없습니다"),
    ).toBeInTheDocument();
  });

  it("로딩 중에는 animate-pulse 스켈레톤 요소가 렌더된다", async () => {
    // Promise 를 resolve 하지 않아 로딩 상태 유지
    vi.mocked(digestDiffService.list).mockReturnValue(new Promise(() => {}));

    const { container } = renderPage();

    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-loading");

    await waitFor(() => {
      expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
    });
  });
});

describe("DigestDiffPage — 페이지네이션", () => {
  it("총 결과가 PAGE_SIZE 를 초과하면 이전/다음 버튼이 표시된다", async () => {
    // 총 100건, PAGE_SIZE=50 → 2페이지
    vi.mocked(digestDiffService.list).mockResolvedValue(
      makeListResponse({
        content: Array.from({ length: 50 }, (_, i) => makeEntry({ id: `ddl-${i}`, digestDate: "2026-04-20" })),
        totalElements: 100,
        page: 0,
        size: 50,
      }),
    );

    renderPage();
    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-paged");

    // 다음 버튼이 나타날 때까지 대기
    const nextBtn = await screen.findByRole("button", { name: "다음 페이지" });
    expect(nextBtn).toBeInTheDocument();
    expect(nextBtn).not.toBeDisabled();

    // 이전 버튼은 첫 페이지에서 비활성화
    const prevBtn = screen.getByRole("button", { name: "이전 페이지" });
    expect(prevBtn).toBeDisabled();
  });

  it("다음 버튼 클릭 시 page 가 1 로 증가하여 API 를 재호출한다", async () => {
    vi.mocked(digestDiffService.list).mockResolvedValue(
      makeListResponse({
        totalElements: 100,
        page: 0,
        size: 50,
      }),
    );

    renderPage();
    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-page2");

    const nextBtn = await screen.findByRole("button", { name: "다음 페이지" });
    await userEvent.click(nextBtn);

    await waitFor(() => {
      const calls = vi.mocked(digestDiffService.list).mock.calls;
      // page=1 로 호출된 건이 있어야 한다
      const pageOneCalls = calls.filter((args) => args[0].page === 1);
      expect(pageOneCalls.length).toBeGreaterThan(0);
    });
  });
});

describe("DigestDiffPage — 행 클릭 시 diff 펼치기", () => {
  it("행 클릭 시 legacy summary 와 new summary 가 표시된다", async () => {
    vi.mocked(digestDiffService.list).mockResolvedValue(makeListResponse());

    renderPage();
    const input = screen.getByLabelText("카테고리 ID");
    await userEvent.type(input, "cat-abc");

    // 날짜 셀이 표시될 때까지 대기
    const dateCell = await screen.findByText("2026-04-20");
    // 해당 행 클릭 (closest tr)
    const row = dateCell.closest("tr");
    if (row) await userEvent.click(row);

    // legacy / new 레이블이 나타나야 한다
    expect(screen.getByText(/Legacy 요약/i)).toBeInTheDocument();
    expect(screen.getByText(/New 요약/i)).toBeInTheDocument();
    // 실제 내용
    expect(screen.getByText("Legacy summary text")).toBeInTheDocument();
    expect(screen.getByText("New summary text")).toBeInTheDocument();
  });
});
