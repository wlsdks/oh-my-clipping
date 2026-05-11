// @vitest-environment node
import { describe, it, expect } from "vitest";
import { categoryKeys } from "../categoryKeys";

describe("categoryKeys", () => {
  it('all is a tuple with "categories"', () => {
    expect(categoryKeys.all).toEqual(["categories"]);
  });
  it('lists() returns [...all, "list"]', () => {
    expect(categoryKeys.lists()).toEqual(["categories", "list"]);
  });
  it('detail(id) returns [...all, "detail", id]', () => {
    expect(categoryKeys.detail("abc")).toEqual(["categories", "detail", "abc"]);
  });
});
