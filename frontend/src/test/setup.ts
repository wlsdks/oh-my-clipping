import "@testing-library/jest-dom";

class TestResizeObserver implements ResizeObserver {
  private readonly callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
  }

  observe(target: Element) {
    this.callback(
      [
        {
          target,
          contentRect: {
            x: 0,
            y: 0,
            width: 600,
            height: 240,
            top: 0,
            right: 600,
            bottom: 240,
            left: 0,
            toJSON: () => ({}),
          },
          borderBoxSize: [],
          contentBoxSize: [],
          devicePixelContentBoxSize: [],
        },
      ],
      this,
    );
  }

  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === "undefined") {
  Object.defineProperty(globalThis, "ResizeObserver", {
    value: TestResizeObserver,
    writable: true
  });
}

if (typeof Element !== "undefined" && typeof Element.prototype.hasPointerCapture !== "function") {
  Object.defineProperty(Element.prototype, "hasPointerCapture", {
    value: () => false,
    writable: true
  });
}

if (typeof Element !== "undefined" && typeof Element.prototype.setPointerCapture !== "function") {
  Object.defineProperty(Element.prototype, "setPointerCapture", {
    value: () => {},
    writable: true
  });
}

if (typeof Element !== "undefined" && typeof Element.prototype.releasePointerCapture !== "function") {
  Object.defineProperty(Element.prototype, "releasePointerCapture", {
    value: () => {},
    writable: true
  });
}

if (typeof Element !== "undefined" && typeof Element.prototype.scrollIntoView !== "function") {
  Object.defineProperty(Element.prototype, "scrollIntoView", {
    value: () => {},
    writable: true
  });
}

if (typeof Element !== "undefined" && typeof Element.prototype.scrollTo !== "function") {
  Object.defineProperty(Element.prototype, "scrollTo", {
    value: () => {},
    writable: true
  });
}

if (typeof HTMLElement !== "undefined") {
  Object.defineProperties(HTMLElement.prototype, {
    clientWidth: {
      configurable: true,
      get() {
        return 600;
      },
    },
    clientHeight: {
      configurable: true,
      get() {
        return 240;
      },
    },
    offsetWidth: {
      configurable: true,
      get() {
        return 600;
      },
    },
    offsetHeight: {
      configurable: true,
      get() {
        return 240;
      },
    },
  });
}

if (typeof HTMLElement !== "undefined" && typeof HTMLElement.prototype.scrollTo !== "function") {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", {
    value: () => {},
    writable: true
  });
}

if (typeof globalThis.scrollTo === "function") {
  Object.defineProperty(globalThis, "scrollTo", {
    value: () => {},
    writable: true
  });
}

/**
 * jsdom v26+ 의 localStorage 구현이 zustand persist 미들웨어가 기대하는
 * Storage 인터페이스(setItem/getItem/removeItem)를 함수로 제공하지 않는 경우가 있다.
 * 테스트 환경에서 안정적으로 동작하도록 완전한 in-memory Storage를 주입한다.
 */
if (typeof globalThis.localStorage === "undefined" || typeof globalThis.localStorage.setItem !== "function") {
  const store = new Map<string, string>();
  const storage: Storage = {
    get length() {
      return store.size;
    },
    clear() {
      store.clear();
    },
    getItem(key: string) {
      return store.get(key) ?? null;
    },
    key(index: number) {
      return [...store.keys()][index] ?? null;
    },
    removeItem(key: string) {
      store.delete(key);
    },
    setItem(key: string, value: string) {
      store.set(key, value);
    }
  };
  Object.defineProperty(globalThis, "localStorage", { value: storage, writable: true });
  Object.defineProperty(globalThis, "sessionStorage", { value: storage, writable: true });
}
