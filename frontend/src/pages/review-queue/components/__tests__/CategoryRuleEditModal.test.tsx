import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, beforeAll } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { Toaster } from "sonner";
import type { ReactNode } from "react";

// Radix Dialog / Switch polyfill (AGENTS.md §5.1.2)
beforeAll(() => {
  if (typeof window.ResizeObserver === "undefined") {
    window.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
  window.HTMLElement.prototype.scrollTo =
    window.HTMLElement.prototype.scrollTo || (() => {});
});

// OrganizationMultiSelect — Radix Popover 없이 테스트하기 위한 간단한 대체 컴포넌트
vi.mock("@/components/shared/OrganizationMultiSelect", () => ({
  OrganizationMultiSelect: ({
    value,
    onChange,
  }: {
    value: string[];
    onChange: (next: string[]) => void;
  }) => (
    <div data-testid="org-multi-select">
      <button
        type="button"
        data-testid="add-org-1"
        onClick={() => onChange([...value, "org-1"].filter((v, i, a) => a.indexOf(v) === i))}
      >
        add-org-1
      </button>
      <button
        type="button"
        data-testid="add-org-2"
        onClick={() => onChange([...value, "org-2"].filter((v, i, a) => a.indexOf(v) === i))}
      >
        add-org-2
      </button>
      <span data-testid="org-count">{value.length}개 선택</span>
    </div>
  ),
}));

// categoryRuleBundleService mock
const updateBundleMock = vi.fn();
vi.mock("@/services/categoryRuleBundleService", () => ({
  categoryRuleBundleService: {
    update: (...args: unknown[]) => updateBundleMock(...args),
  },
}));

// categoryRuleService — dryRun 스텁
vi.mock("@/services/categoryRuleService", () => ({
  categoryRuleService: {
    dryRun: vi.fn(),
    listAutoExcluded: vi.fn(),
    restoreFromAutoExclude: vi.fn(),
  },
}));

import { categoryRuleService } from "@/services/categoryRuleService";
import { CategoryRuleEditModal } from "../CategoryRuleEditModal";
import type { RuleDryRunResult } from "@/types/categoryRule";

/** 기본 currentBundle 팩토리 */
function makeBundle(
  overrides: Partial<{
    excludeEventTypes: string[];
    includeKeywords: string[];
    organizationIds: string[];
    accountBasedDigestEnabled: boolean;
    shadowModeEnabled: boolean;
    shadowEnabledAt: string | null;
  }> = {},
) {
  return {
    excludeEventTypes: [],
    includeKeywords: [],
    organizationIds: [],
    accountBasedDigestEnabled: false,
    shadowModeEnabled: false,
    shadowEnabledAt: null,
    ...overrides,
  };
}

function makeDryRunResult(overrides: Partial<RuleDryRunResult> = {}): RuleDryRunResult {
  return {
    analyzedCount: 120,
    wouldAutoExclude: 8,
    wouldStayUnchanged: 112,
    samples: [
      {
        summaryId: "sum-1",
        title: "샘플 실적 발표 기사",
        eventType: "EARNINGS",
        score: 0.2,
        reason: "event_type_blacklist",
      },
    ],
    ...overrides,
  };
}

function renderWithProviders(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <Toaster />
        {ui}
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("CategoryRuleEditModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    updateBundleMock.mockResolvedValue(undefined);
  });

  it("탭 2개 노출 — 감사 로그 탭은 aria-disabled", () => {
    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-1"
        categoryName="경제"
        currentBundle={makeBundle()}
        onClose={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("tab", { name: /필터 & 발송 설정/ }),
    ).toBeInTheDocument();

    const auditTab = screen.getByRole("tab", { name: /감사 로그/ });
    expect(auditTab).toHaveAttribute("aria-disabled", "true");
  });

  it("저장 시 rule-bundle PUT 1회 + body 시그니처 검증", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-1"
        categoryName="경제"
        currentBundle={makeBundle({ includeKeywords: ["AI"] })}
        onClose={onClose}
      />,
    );

    // 조직 2개 추가
    await user.click(screen.getByTestId("add-org-1"));
    await user.click(screen.getByTestId("add-org-2"));

    // Account-Based 스위치 ON (Shadow 도 자동 ON)
    await user.click(screen.getByRole("switch", { name: /Account-Based Digest/ }));

    // 저장
    await user.click(screen.getByTestId("category-rule-save-button"));

    await waitFor(() => {
      expect(updateBundleMock).toHaveBeenCalledTimes(1);
    });

    const [calledCategoryId, calledBody] = updateBundleMock.mock.calls[0] as [
      string,
      {
        excludeEventTypes: string[];
        includeKeywords: string[];
        organizationIds: string[];
        accountBasedDigestEnabled: boolean;
        shadowModeEnabled: boolean;
      },
    ];

    expect(calledCategoryId).toBe("cat-1");
    // excludeEventTypes — 체크 안 했으므로 빈 배열
    expect(calledBody.excludeEventTypes).toEqual([]);
    // includeKeywords — currentBundle 에서 초기화된 ["AI"]
    expect(calledBody.includeKeywords).toEqual(["AI"]);
    // organizationIds — 두 개 추가
    expect(calledBody.organizationIds).toEqual(["org-1", "org-2"]);
    // accountBasedDigestEnabled — ON
    expect(calledBody.accountBasedDigestEnabled).toBe(true);
    // shadowModeEnabled — Account-Based ON 이므로 자동 true
    expect(calledBody.shadowModeEnabled).toBe(true);
  });

  it("Account-Based OFF → Shadow Switch 숨김", async () => {
    const user = userEvent.setup();

    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-1"
        currentBundle={makeBundle({
          accountBasedDigestEnabled: true,
          shadowModeEnabled: true,
        })}
        onClose={vi.fn()}
      />,
    );

    // Shadow Switch 가 보여야 함
    expect(screen.getByRole("switch", { name: /Shadow Mode/ })).toBeInTheDocument();

    // Account-Based OFF
    await user.click(screen.getByRole("switch", { name: /Account-Based Digest/ }));

    // Shadow Switch 가 사라져야 함
    expect(screen.queryByRole("switch", { name: /Shadow Mode/ })).toBeNull();
  });

  it("모드 변경 toast — (1 kw, 0 org) → (1 kw, 2 org) 이면 '주제×기업 교차 필터' 토스트", async () => {
    const user = userEvent.setup();

    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-1"
        currentBundle={makeBundle({ includeKeywords: ["AI"], organizationIds: [] })}
        onClose={vi.fn()}
      />,
    );

    // 조직 2개 추가 — beforeMode: TOPIC_ONLY(1,0), afterMode: CROSSFILTER(1,2)
    await user.click(screen.getByTestId("add-org-1"));
    await user.click(screen.getByTestId("add-org-2"));

    await user.click(screen.getByTestId("category-rule-save-button"));

    await waitFor(() => {
      expect(
        screen.getByText(/주제×기업 교차 필터/),
      ).toBeInTheDocument();
    });
  });

  it("미리보기 버튼 클릭 시 dryRun 이 호출되고 결과가 렌더된다", async () => {
    const user = userEvent.setup();
    vi.mocked(categoryRuleService.dryRun).mockResolvedValue(
      makeDryRunResult({ wouldAutoExclude: 17, analyzedCount: 200 }),
    );

    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-42"
        currentBundle={makeBundle({ excludeEventTypes: ["EARNINGS"] })}
        onClose={vi.fn()}
      />,
    );

    // 초기 — 미리보기 결과 없음
    expect(screen.queryByTestId("rule-dry-run-preview")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("category-rule-preview-button"));

    await waitFor(() => {
      expect(categoryRuleService.dryRun).toHaveBeenCalledTimes(1);
    });
    expect(categoryRuleService.dryRun).toHaveBeenCalledWith("cat-42", {
      excludeEventTypes: ["EARNINGS"],
    });

    await waitFor(() => {
      expect(screen.getByTestId("rule-dry-run-preview")).toBeInTheDocument();
    });
    expect(screen.getByTestId("rule-dry-run-would-exclude")).toHaveTextContent("17");
  });

  it("저장 실패 시 toast.error 가 뜨고 모달은 닫히지 않는다", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    updateBundleMock.mockRejectedValue(new Error("서버 500"));

    renderWithProviders(
      <CategoryRuleEditModal
        open
        categoryId="cat-1"
        currentBundle={makeBundle({ excludeEventTypes: ["FUNDING"] })}
        onClose={onClose}
      />,
    );

    await user.click(screen.getByTestId("category-rule-save-button"));

    await waitFor(() => {
      expect(updateBundleMock).toHaveBeenCalledTimes(1);
    });

    // 에러 토스트 노출
    await waitFor(() => {
      expect(screen.getByText(/저장하지 못했어요/)).toBeInTheDocument();
    });

    // 모달 유지 — onClose 미호출
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("category-rule-edit-modal")).toBeInTheDocument();
  });
});
