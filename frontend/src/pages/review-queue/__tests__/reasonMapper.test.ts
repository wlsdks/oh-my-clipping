import { describe, it, expect } from "vitest";
import { toFriendlyReason } from "../model/reasonMapper";

describe("toFriendlyReason", () => {
  it("제외 키워드 일치", () => {
    const result = toFriendlyReason("제외 키워드 일치: 광고");
    expect(result.friendly).toBe("'광고' 키워드가 제외 목록에 있어요");
    expect(result.detail).toBe("제외 키워드 일치: 광고");
  });

  it("포함 키워드 일치", () => {
    const result = toFriendlyReason("포함 키워드 일치: 반도체");
    expect(result.friendly).toBe("'반도체' 키워드가 포함 목록에 있어요");
    expect(result.detail).toBe("포함 키워드 일치: 반도체");
  });

  it("콘텐츠 안전성 자동 제외", () => {
    const result = toFriendlyReason("콘텐츠 안전성 자동 제외 (importance=0.03)");
    expect(result.friendly).toBe("중요도가 매우 낮아 자동으로 제외됐어요");
    expect(result.detail).toBe("콘텐츠 안전성 자동 제외 (importance=0.03)");
  });

  it("중요도 포함 임계치 이상", () => {
    const result = toFriendlyReason("중요도 0.72가 포함 임계치 0.60 이상");
    expect(result.friendly).toBe("중요도가 높아서 자동으로 포함됐어요");
    expect(result.detail).toBe("중요도 0.72가 포함 임계치 0.60 이상");
  });

  it("중요도 검토 임계치 이상", () => {
    const result = toFriendlyReason("중요도 0.45가 검토 임계치 0.30 이상");
    expect(result.friendly).toBe("중요도가 애매해서 직접 확인이 필요해요");
    expect(result.detail).toBe("중요도 0.45가 검토 임계치 0.30 이상");
  });

  it("자동 분류 확신 부족", () => {
    const result = toFriendlyReason("자동 분류 확신 부족 항목은 검토함으로 전환");
    expect(result.friendly).toBe("AI가 판단하기 어려워서 직접 확인이 필요해요");
    expect(result.detail).toBe("자동 분류 확신 부족 항목은 검토함으로 전환");
  });

  it("검토 임계치 미달 자동 제외", () => {
    const result = toFriendlyReason("검토 임계치 미달 항목 자동 제외");
    expect(result.friendly).toBe("중요도가 낮아 자동으로 제외됐어요");
    expect(result.detail).toBe("검토 임계치 미달 항목 자동 제외");
  });

  it("기본 정책 자동 포함", () => {
    const result = toFriendlyReason("기본 정책에 따라 자동 포함");
    expect(result.friendly).toBe("기본 정책에 따라 자동으로 포함됐어요");
    expect(result.detail).toBe("기본 정책에 따라 자동 포함");
  });

  it("unknown reason falls back to raw text", () => {
    const result = toFriendlyReason("새로운 알 수 없는 이유");
    expect(result.friendly).toBe("새로운 알 수 없는 이유");
    expect(result.detail).toBe("새로운 알 수 없는 이유");
  });

  it("empty string", () => {
    const result = toFriendlyReason("");
    expect(result.friendly).toBe("분류 사유 없음");
    expect(result.detail).toBe("");
  });
});
