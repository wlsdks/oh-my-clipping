import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { SIDEBAR_GROUPS_KEY, readGroupState } from "../sidebarGroupState";

describe("readGroupState", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it("저장된 값이 없으면 빈 객체를 반환한다", () => {
    expect(readGroupState()).toEqual({});
  });

  it("저장된 JSON 오브젝트를 파싱해서 반환한다", () => {
    localStorage.setItem(SIDEBAR_GROUPS_KEY, JSON.stringify({ news: true, ops: false }));
    expect(readGroupState()).toEqual({ news: true, ops: false });
  });

  it("JSON이 깨져있으면 빈 객체를 반환한다", () => {
    localStorage.setItem(SIDEBAR_GROUPS_KEY, "not-valid-json{{{");
    expect(readGroupState()).toEqual({});
  });
});
