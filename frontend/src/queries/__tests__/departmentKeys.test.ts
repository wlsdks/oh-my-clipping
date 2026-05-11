// @vitest-environment node
import { describe, it, expect } from "vitest";
import { departmentKeys } from "../departmentKeys";

describe("departmentKeys", () => {
  it('all is a tuple with "departments"', () => {
    expect(departmentKeys.all).toEqual(["departments"]);
  });

  it('tree() returns [...all, "tree"]', () => {
    expect(departmentKeys.tree()).toEqual(["departments", "tree"]);
  });

  it('adminTree() returns [...all, "admin-tree"]', () => {
    expect(departmentKeys.adminTree()).toEqual(["departments", "admin-tree"]);
  });

  it("tree() 와 adminTree() 는 서로 다른 캐시 키를 반환한다", () => {
    expect(departmentKeys.tree()).not.toEqual(departmentKeys.adminTree());
  });
});
