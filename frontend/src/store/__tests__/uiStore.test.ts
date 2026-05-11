import { describe, it, expect, beforeEach } from "vitest";

import { uiStore } from "@/store/uiStore";

describe("uiStore", () => {
  beforeEach(() => uiStore.getState().setTheme("light"));

  it("기본 테마는 light이어야 한다", () => {
    expect(uiStore.getState().theme).toBe("light");
  });

  it("setTheme으로 dark 테마로 변경할 수 있어야 한다", () => {
    uiStore.getState().setTheme("dark");
    expect(uiStore.getState().theme).toBe("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
  });

  it("light 테마로 변경 시 dark 클래스가 제거되어야 한다", () => {
    uiStore.getState().setTheme("dark");
    uiStore.getState().setTheme("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });
});
