import { describe, it, expect } from "vitest";
import { sortCategories } from "../sorting";
import type { Category } from "@/types/category";

function makeCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: "cat-1",
    name: "테스트",
    isActive: true,
    isPublic: true,
    maxItems: 5,
    sourceCount: 2,
    subscriberCount: 5,
    errorSourceCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  } as Category;
}

describe("sortCategories", () => {
  const cats = [
    makeCategory({ id: "a", name: "가나다", sourceCount: 3, subscriberCount: 10, lastDeliveryAt: "2026-01-03" }),
    makeCategory({ id: "b", name: "라마바", sourceCount: 1, subscriberCount: 5, lastDeliveryAt: "2026-01-01" }),
    makeCategory({ id: "c", name: "아자차", sourceCount: 5, subscriberCount: 2, lastDeliveryAt: null }),
  ];

  it("이름 오름차순 정렬", () => {
    const sorted = sortCategories(cats, { field: "name", direction: "asc" });
    expect(sorted.map((c) => c.name)).toEqual(["가나다", "라마바", "아자차"]);
  });

  it("이름 내림차순 정렬", () => {
    const sorted = sortCategories(cats, { field: "name", direction: "desc" });
    expect(sorted.map((c) => c.name)).toEqual(["아자차", "라마바", "가나다"]);
  });

  it("소스 수 오름차순 정렬", () => {
    const sorted = sortCategories(cats, { field: "sourceCount", direction: "asc" });
    expect(sorted.map((c) => c.sourceCount)).toEqual([1, 3, 5]);
  });

  it("구독자 수 내림차순 정렬", () => {
    const sorted = sortCategories(cats, { field: "subscriberCount", direction: "desc" });
    expect(sorted.map((c) => c.subscriberCount)).toEqual([10, 5, 2]);
  });

  it("마지막 발송일 오름차순 정렬 — null은 맨 앞", () => {
    const sorted = sortCategories(cats, { field: "lastDeliveryAt", direction: "asc" });
    expect(sorted[0].lastDeliveryAt).toBeNull();
  });

  it("상태 심각도 내림차순 정렬", () => {
    const mixed = [
      makeCategory({ id: "x", name: "정상", isActive: true, sourceCount: 2, subscriberCount: 1, errorSourceCount: 0 }),
      makeCategory({ id: "y", name: "주의", isActive: true, sourceCount: 0, subscriberCount: 1, errorSourceCount: 0 }),
      makeCategory({ id: "z", name: "오류", isActive: true, sourceCount: 3, errorSourceCount: 3, subscriberCount: 1 }),
    ];
    const sorted = sortCategories(mixed, { field: "status", direction: "desc" });
    expect(sorted.map((c) => c.name)).toEqual(["오류", "주의", "정상"]);
  });

  it("원본 배열을 변경하지 않는다", () => {
    const original = [...cats];
    sortCategories(cats, { field: "sourceCount", direction: "desc" });
    expect(cats.map((c) => c.id)).toEqual(original.map((c) => c.id));
  });

  it("빈 배열도 에러 없이 처리한다", () => {
    const result = sortCategories([], { field: "name", direction: "asc" });
    expect(result).toEqual([]);
  });
});
