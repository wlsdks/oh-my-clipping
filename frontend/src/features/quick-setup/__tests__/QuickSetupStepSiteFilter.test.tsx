import { render, screen, fireEvent, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QuickSetupStepSiteFilter } from "../QuickSetupStepSiteFilter";
import { createQuickSetupForm } from "../model/quickSetupTypes";
import { userService } from "@/services/userService";

vi.mock("@/services/sourceService", () => ({
  sourceService: {
    validateUrl: vi.fn().mockResolvedValue({ valid: true, reason: "" }),
  },
}));

vi.mock("@/services/userService", () => ({
  userService: {
    searchKnownSources: vi.fn(),
    validateSetupSourceUrl: vi.fn().mockResolvedValue({ valid: true, reason: "" }),
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

describe("QuickSetupStepSiteFilter", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("늦게 도착한 이전 사이트 검색 결과가 최신 검색 결과를 덮지 않는다", async () => {
    const first = deferred<Awaited<ReturnType<typeof userService.searchKnownSources>>>();
    const second = deferred<Awaited<ReturnType<typeof userService.searchKnownSources>>>();
    vi.mocked(userService.searchKnownSources)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    render(
      <QuickSetupStepSiteFilter
        form={{ ...createQuickSetupForm(), siteSelectionMode: "specific" }}
        onChange={vi.fn()}
      />
    );
    const input = screen.getByPlaceholderText(/언론사 이름으로 검색/);

    fireEvent.change(input, { target: { value: "old" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });

    fireEvent.change(input, { target: { value: "new" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });

    await act(async () => {
      second.resolve([
        { name: "New Source", domain: "new.example", region: "GLOBAL", aliases: [] },
      ]);
      await second.promise;
      await Promise.resolve();
    });
    expect(screen.getByText("New Source")).toBeInTheDocument();

    await act(async () => {
      first.resolve([
        { name: "Old Source", domain: "old.example", region: "GLOBAL", aliases: [] },
      ]);
      await first.promise;
      await Promise.resolve();
    });

    expect(screen.getByText("New Source")).toBeInTheDocument();
    expect(screen.queryByText("Old Source")).not.toBeInTheDocument();
  });
});
