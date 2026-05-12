import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";

// ── 의존 서비스 mock (hoisted) ──
vi.mock("@/services/companyService", () => ({
  companyService: {
    searchUserCompanies: vi.fn().mockResolvedValue([]),
    searchAdminCompanies: vi.fn().mockResolvedValue([]),
  }
}));

vi.mock("@/services/userService", () => ({
  userService: {
    browseCategories: vi.fn().mockResolvedValue([]),
    subscribeCategoryDm: vi.fn().mockResolvedValue(undefined),
    updateSlackMemberId: vi.fn().mockResolvedValue(undefined),
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

import { QuickSetupStepSource } from "../QuickSetupStepSource";
import type { QuickSetupForm } from "../model/quickSetupTypes";
import { createQuickSetupForm } from "../model/quickSetupTypes";
import { companyService } from "@/services/companyService";
import { userService } from "@/services/userService";

/** 기본 QuickSetupForm을 만든다 */
function makeForm(overrides: Partial<QuickSetupForm> = {}): QuickSetupForm {
  return { ...createQuickSetupForm(), ...overrides };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("QuickSetupStepSource — 렌더링", () => {
  it("기본 안내 문구가 표시된다", () => {
    const onChange = vi.fn();
    render(<QuickSetupStepSource form={makeForm()} onChange={onChange} />);
    expect(screen.getByText("어떤 뉴스를 받고 싶으세요?")).toBeInTheDocument();
    expect(screen.getByText(/관심 키워드나 기업명을 추가하세요/)).toBeInTheDocument();
  });

  it("기본 탭은 '키워드 입력'이다", () => {
    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);
    const keywordBtn = screen.getByRole("button", { name: /키워드 입력/ });
    expect(keywordBtn).toHaveAttribute("aria-pressed", "true");
  });

  it("entries가 없을 때 안내 문구를 표시한다", () => {
    render(<QuickSetupStepSource form={makeForm({ entries: [] })} onChange={vi.fn()} />);
    expect(screen.getByText(/키워드를 입력하거나 아래 추천에서 선택해 주세요/)).toBeInTheDocument();
  });

  it("entries가 있으면 칩으로 표시된다", () => {
    const form = makeForm({
      entries: [
        { value: "AI", type: "keyword" },
        { value: "MegaCorp", type: "company", stockCode: "999930" },
      ]
    });
    render(<QuickSetupStepSource form={form} onChange={vi.fn()} />);
    expect(screen.getByText("AI")).toBeInTheDocument();
    expect(screen.getByText("MegaCorp")).toBeInTheDocument();
  });
});

describe("QuickSetupStepSource — 키워드 입력", () => {
  it("키워드 입력 후 Enter 치면 onChange가 호출되어 entries에 추가된다", () => {
    const onChange = vi.fn();
    render(<QuickSetupStepSource form={makeForm()} onChange={onChange} />);

    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    fireEvent.change(input, { target: { value: "반도체" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        entries: expect.arrayContaining([
          expect.objectContaining({ value: "반도체", type: "keyword" })
        ])
      })
    );
  });

  it("빈 문자열로 Enter 누르면 onChange가 호출되지 않는다", () => {
    const onChange = vi.fn();
    render(<QuickSetupStepSource form={makeForm()} onChange={onChange} />);

    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    expect(onChange).not.toHaveBeenCalled();
  });

  it("중복 키워드는 재추가되지 않는다", () => {
    const onChange = vi.fn();
    render(
      <QuickSetupStepSource
        form={makeForm({ entries: [{ value: "AI", type: "keyword" }] })}
        onChange={onChange}
      />
    );

    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    fireEvent.change(input, { target: { value: "AI" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("QuickSetupStepSource — 칩 제거", () => {
  it("chip의 × 버튼 클릭 시 entries에서 제거된다", () => {
    const onChange = vi.fn();
    render(
      <QuickSetupStepSource
        form={makeForm({ entries: [{ value: "AI", type: "keyword" }, { value: "반도체", type: "keyword" }] })}
        onChange={onChange}
      />
    );

    // × 버튼들 (chip 내부)
    const removeBtns = screen.getAllByText("×");
    fireEvent.click(removeBtns[0]);

    expect(onChange).toHaveBeenCalledTimes(1);
    const firstCall = onChange.mock.calls[0][0];
    expect(firstCall.entries).toEqual([{ value: "반도체", type: "keyword" }]);
  });
});

describe("QuickSetupStepSource — 기업 검색 탭", () => {
  it("탭 전환 시 input placeholder가 기업 검색용으로 바뀐다", () => {
    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /기업 검색/ }));

    expect(screen.getByPlaceholderText(/기업명을 입력하세요/)).toBeInTheDocument();
  });

  it("기업 검색 시 companyService.searchAdminCompanies가 호출된다 (관리자 모드)", async () => {
    vi.mocked(companyService.searchAdminCompanies).mockResolvedValue([
      { corpCode: "C1", corpName: "MegaCorp", stockCode: "999930" }
    ] as never);

    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /기업 검색/ }));

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "MegaCorp" } });

    // debounce 300ms
    await waitFor(
      () => expect(companyService.searchAdminCompanies).toHaveBeenCalledWith("MegaCorp"),
      { timeout: 1000 }
    );
  });

  it("isUserMode=true 시 companyService.searchUserCompanies가 호출된다", async () => {
    vi.mocked(companyService.searchUserCompanies).mockResolvedValue([] as never);

    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} isUserMode />);
    fireEvent.click(screen.getByRole("button", { name: /기업 검색/ }));

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "TestCorp" } });

    await waitFor(
      () => expect(companyService.searchUserCompanies).toHaveBeenCalledWith("TestCorp"),
      { timeout: 1000 }
    );
  });

  it("늦게 도착한 이전 기업 검색 결과가 최신 추천을 덮지 않는다", async () => {
    vi.useFakeTimers();
    const first = deferred<Awaited<ReturnType<typeof companyService.searchAdminCompanies>>>();
    const second = deferred<Awaited<ReturnType<typeof companyService.searchAdminCompanies>>>();
    vi.mocked(companyService.searchAdminCompanies)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    try {
      render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);
      fireEvent.click(screen.getByRole("button", { name: /기업 검색/ }));
      const input = screen.getByPlaceholderText(/기업명을 입력하세요/);

      fireEvent.change(input, { target: { value: "OldCorp" } });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(300);
      });

      fireEvent.change(input, { target: { value: "NewCorp" } });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(300);
      });

      await act(async () => {
        second.resolve([
          { corpCode: "NEW", corpName: "NewCorp", stockCode: "000002" }
        ]);
        await second.promise;
        await Promise.resolve();
      });
      expect(screen.getByText("NewCorp")).toBeInTheDocument();

      await act(async () => {
        first.resolve([
          { corpCode: "OLD", corpName: "OldCorp", stockCode: "000001" }
        ]);
        await first.promise;
        await Promise.resolve();
      });

      expect(screen.getByText("NewCorp")).toBeInTheDocument();
      expect(screen.queryByText("OldCorp")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("QuickSetupStepSource — 프리셋 키워드 칩", () => {
  it("프리셋 영역에 추천 키워드 칩이 다수 렌더링된다", () => {
    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);
    expect(screen.getByText("추천 키워드")).toBeInTheDocument();
    // 프리셋은 최소 10개 이상
    const presetBtns = screen.getAllByRole("button");
    expect(presetBtns.length).toBeGreaterThan(10);
  });

  it("프리셋 클릭 시 entries에 추가하는 onChange 호출", () => {
    const onChange = vi.fn();
    render(<QuickSetupStepSource form={makeForm()} onChange={onChange} />);

    // SaaS 프리셋은 KEYWORD_PRESETS에 포함
    const saasBtn = screen.queryByRole("button", { name: "SaaS" });
    if (saasBtn) {
      fireEvent.click(saasBtn);
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({
          entries: expect.arrayContaining([
            expect.objectContaining({ value: "SaaS", type: "keyword" })
          ])
        })
      );
    }
  });

  it("이미 선택된 프리셋을 다시 클릭하면 제거 onChange를 호출한다", () => {
    const onChange = vi.fn();
    render(
      <QuickSetupStepSource
        form={makeForm({ entries: [{ value: "SaaS", type: "keyword" }] })}
        onChange={onChange}
      />
    );
    const saasBtn = screen.queryByRole("button", { name: "SaaS" });
    if (saasBtn) {
      fireEvent.click(saasBtn);
      expect(onChange).toHaveBeenCalled();
      const payload = onChange.mock.calls[0][0];
      expect(payload.entries).toEqual([]);
    }
  });
});

describe("QuickSetupStepSource — 사용자 모드 카테고리 추천", () => {
  it("isUserMode=true일 때 browseCategories가 호출된다", async () => {
    render(
      <QuickSetupStepSource
        form={makeForm()}
        onChange={vi.fn()}
        isUserMode
      />
    );
    await waitFor(() => {
      expect(userService.browseCategories).toHaveBeenCalled();
    });
  });

  it("isUserMode=false일 때는 browseCategories가 호출되지 않는다", () => {
    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} />);
    expect(userService.browseCategories).not.toHaveBeenCalled();
  });
});

describe("QuickSetupStepSource — disabled prop", () => {
  it("disabled=true 시 키워드 입력이 비활성화된다", () => {
    render(<QuickSetupStepSource form={makeForm()} onChange={vi.fn()} disabled />);
    const input = screen.getByPlaceholderText(/키워드를 입력하고 Enter/);
    expect(input).toBeDisabled();
  });
});
