import { render, screen, fireEvent, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SourceDiscoverInput } from "../SourceDiscoverInput";
import { sourceService } from "@/services/sourceService";

vi.mock("@/services/sourceService", () => ({
  sourceService: {
    discoverSource: vi.fn(),
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

describe("SourceDiscoverInput", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("늦게 도착한 이전 검색 결과가 최신 검색 결과를 덮지 않는다", async () => {
    const first = deferred<Awaited<ReturnType<typeof sourceService.discoverSource>>>();
    const second = deferred<Awaited<ReturnType<typeof sourceService.discoverSource>>>();
    vi.mocked(sourceService.discoverSource)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    render(<SourceDiscoverInput onSelect={vi.fn()} />);
    const input = screen.getByPlaceholderText(/Example Daily/);

    fireEvent.change(input, { target: { value: "old" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });

    fireEvent.change(input, { target: { value: "new" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });

    await act(async () => {
      second.resolve({
        knownMatch: { name: "New Source", rssUrl: "https://new.example/rss", region: "GLOBAL" },
        discoveredFeeds: [],
      });
      await second.promise;
      await Promise.resolve();
    });
    expect(screen.getByText("New Source")).toBeInTheDocument();

    await act(async () => {
      first.resolve({
        knownMatch: { name: "Old Source", rssUrl: "https://old.example/rss", region: "GLOBAL" },
        discoveredFeeds: [],
      });
      await first.promise;
      await Promise.resolve();
    });

    expect(screen.getByText("New Source")).toBeInTheDocument();
    expect(screen.queryByText("Old Source")).not.toBeInTheDocument();
  });
});
