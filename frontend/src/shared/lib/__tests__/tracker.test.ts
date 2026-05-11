import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// 매 테스트마다 새 tracker 인스턴스를 얻기 위해 동적 import 사용
async function loadTracker() {
  // 모듈 캐시를 지우고 새로 import하면 createTracker()가 다시 실행된다.
  vi.resetModules();
  const mod = await import("../tracker");
  return mod.tracker;
}

describe("EventTracker SDK", () => {
  let mockFetch: ReturnType<typeof vi.fn>;
  let mockStorage: Record<string, string>;

  beforeEach(() => {
    vi.useFakeTimers();

    // sessionStorage 스텁
    mockStorage = {};
    const storage = {
      getItem: vi.fn((key: string) => mockStorage[key] ?? null),
      setItem: vi.fn((key: string, val: string) => {
        mockStorage[key] = val;
      }),
      removeItem: vi.fn((key: string) => {
        delete mockStorage[key];
      }),
      clear: vi.fn(() => {
        mockStorage = {};
      }),
      length: 0,
      key: vi.fn(() => null)
    };
    vi.stubGlobal("sessionStorage", storage);

    // fetch 스텁
    mockFetch = vi.fn(() => Promise.resolve({ ok: true }));
    vi.stubGlobal("fetch", mockFetch);

    // window.location 스텁
    vi.stubGlobal("location", { pathname: "/user/clipping" });

    // document.visibilityState / addEventListener 스텁
    vi.stubGlobal("document", {
      visibilityState: "visible",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    });

    // navigator.sendBeacon 스텁
    vi.stubGlobal("navigator", { sendBeacon: vi.fn(() => true) });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  describe("init()", () => {
    it("세션 ID를 생성하여 sessionStorage에 저장한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      expect(sessionStorage.setItem).toHaveBeenCalledWith("tracker_session_id", expect.any(String));
    });

    it("기존 세션 ID가 있으면 재사용한다", async () => {
      mockStorage["tracker_session_id"] = "existing-session";
      const tracker = await loadTracker();
      tracker.init("user-abc");

      // setItem이 호출되지 않아야 한다 (기존 값 사용)
      expect(sessionStorage.setItem).not.toHaveBeenCalled();
    });

    it("visibilitychange 이벤트 리스너를 등록한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      expect(document.addEventListener).toHaveBeenCalledWith("visibilitychange", expect.any(Function));
    });
  });

  describe("track()", () => {
    it("큐에 이벤트를 추가한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("button_click", { buttonId: "cta" });

      expect(tracker.queueSize()).toBe(1);
    });

    it("init() 없이 호출해도 예외가 발생하지 않는다", async () => {
      const tracker = await loadTracker();

      expect(() => tracker.track("test_event")).not.toThrow();
      expect(tracker.queueSize()).toBe(0);
    });

    it("여러 이벤트를 순차적으로 추가한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("event_a");
      tracker.track("event_b");
      tracker.track("event_c");

      expect(tracker.queueSize()).toBe(3);
    });

    it("임계값(10)에 도달하면 자동으로 flush한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      for (let i = 0; i < 10; i++) {
        tracker.track("event", { index: i });
      }

      // flush가 호출되어 fetch가 실행된다
      await vi.advanceTimersByTimeAsync(0);
      expect(mockFetch).toHaveBeenCalled();
      expect(tracker.queueSize()).toBe(0);
    });
  });

  describe("pageView()", () => {
    it("page_view 타입 이벤트를 큐에 추가한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.pageView("/user/clipping/manage");

      expect(tracker.queueSize()).toBe(1);
    });
  });

  describe("flush()", () => {
    it("큐의 이벤트를 fetch로 전송하고 큐를 비운다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("test_event", { key: "value" });
      expect(tracker.queueSize()).toBe(1);

      await tracker.flush();

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toContain("/api/user/events");
      expect(options.method).toBe("POST");
      expect(options.credentials).toBe("include");
      expect(tracker.queueSize()).toBe(0);

      // 전송 body에 이벤트 배열이 포함되어 있다
      const body = JSON.parse(options.body);
      expect(body.events).toHaveLength(1);
      expect(body.events[0].eventType).toBe("test_event");
      expect(body.events[0].sessionId).toBeTruthy();
      expect(body.events[0].timestamp).toBeGreaterThan(0);
    });

    it("큐가 비어 있으면 fetch를 호출하지 않는다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      await tracker.flush();

      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("전송 실패 시 최대 3회 재시도 후 이벤트를 폐기한다", async () => {
      mockFetch = vi.fn(() => Promise.resolve({ ok: false }));
      vi.stubGlobal("fetch", mockFetch);

      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("fail_event");
      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      await tracker.flush();

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(tracker.queueSize()).toBe(0);
      expect(warnSpy).toHaveBeenCalled();
      warnSpy.mockRestore();
    });

    it("네트워크 오류 시 재시도한다", async () => {
      let callCount = 0;
      mockFetch = vi.fn(() => {
        callCount++;
        if (callCount < 3) return Promise.reject(new Error("network error"));
        return Promise.resolve({ ok: true });
      });
      vi.stubGlobal("fetch", mockFetch);

      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("retry_event");
      await tracker.flush();

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(tracker.queueSize()).toBe(0);
    });
  });

  describe("queueSize()", () => {
    it("큐에 있는 이벤트 수를 반환한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      expect(tracker.queueSize()).toBe(0);
      tracker.track("a");
      expect(tracker.queueSize()).toBe(1);
      tracker.track("b");
      expect(tracker.queueSize()).toBe(2);
    });
  });

  describe("주기적 flush", () => {
    it("5초마다 자동으로 flush를 실행한다", async () => {
      const tracker = await loadTracker();
      tracker.init("user-abc");

      tracker.track("periodic_event");
      expect(mockFetch).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(5_000);

      expect(mockFetch).toHaveBeenCalled();
    });
  });
});
