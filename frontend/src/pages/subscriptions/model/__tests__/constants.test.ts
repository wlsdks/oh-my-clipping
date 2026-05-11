import { describe, it, expect } from "vitest";
import { getCategoryStatus, CHIP_FILTERS, REQUEST_STATUS_LABEL } from "../constants";
import type { Category } from "@/types/category";

function makeCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: "cat-1",
    name: "테스트 주제",
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

describe("getCategoryStatus", () => {
  it("정상: 활성 + 소스 있음 + 구독자 있음 + 오류 없음", () => {
    const result = getCategoryStatus(makeCategory());
    expect(result.type).toBe("success");
    expect(result.label).toBe("정상");
  });

  it("비활성: isActive=false", () => {
    const result = getCategoryStatus(makeCategory({ isActive: false }));
    expect(result.type).toBe("neutral");
    expect(result.label).toBe("비활성");
  });

  it("오류: 전체 소스가 오류", () => {
    const result = getCategoryStatus(makeCategory({ sourceCount: 3, errorSourceCount: 3 }));
    expect(result.type).toBe("danger");
  });

  it("주의: 소스 없음", () => {
    const result = getCategoryStatus(makeCategory({ sourceCount: 0 }));
    expect(result.type).toBe("warning");
    expect(result.desc).toContain("소스가 없");
  });

  it("주의: 구독자 없음", () => {
    const result = getCategoryStatus(makeCategory({ subscriberCount: 0 }));
    expect(result.type).toBe("warning");
    expect(result.desc).toContain("구독자가 없");
  });

  it("주의: 일부 소스 오류", () => {
    const result = getCategoryStatus(makeCategory({ sourceCount: 3, errorSourceCount: 1 }));
    expect(result.type).toBe("warning");
    expect(result.desc).toContain("1개 수집 오류");
  });
});

describe("CHIP_FILTERS", () => {
  it("9개 필터가 정의되어 있다", () => {
    expect(CHIP_FILTERS).toHaveLength(9);
  });

  it("반려/철회는 dimmed 속성이 true다", () => {
    const dimmed = CHIP_FILTERS.filter((f) => f.dimmed);
    expect(dimmed.map((f) => f.value)).toEqual(["rejected", "withdrawn"]);
  });
});

describe("REQUEST_STATUS_LABEL", () => {
  it("모든 요청 상태에 라벨이 있다", () => {
    expect(REQUEST_STATUS_LABEL.PENDING).toBe("검토 대기");
    expect(REQUEST_STATUS_LABEL.APPROVED).toBe("승인");
    expect(REQUEST_STATUS_LABEL.REJECTED).toBe("반려");
    expect(REQUEST_STATUS_LABEL.WITHDRAWN).toBe("철회");
  });
});
