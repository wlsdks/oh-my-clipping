import { describe, it, expect } from "vitest";
import { hasSnippet } from "../model/personaPromptOptions";
import type { QuickSetupForm } from "../model/quickSetupTypes";

/** 최소 QuickSetupForm 객체를 생성한다 */
function makeForm(overrides: Partial<QuickSetupForm>): QuickSetupForm {
  return {
    entries: [],
    newsRegion: "domestic",
    siteSelectionMode: "all",
    siteFilters: [],
    categoryName: "",
    categoryDescription: "",
    slackChannelId: "",
    maxItems: 5,
    includeSource: false,
    sourceName: "",
    sourceUrl: "",
    autoApproveSource: true,
    createPersona: true,
    personaName: "",
    personaDescription: "",
    personaSummaryStyle: "",
    personaTargetAudience: "",
    personaPrompt: "",
    slackDeliveryMode: "dm",
    slackChannelConfirmed: false,
    excludeKeywords: [],
    deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"],
    deliveryHour: 9,
    deliveryPreset: "WEEKDAYS",
    ...overrides
  };
}

describe("hasSnippet", () => {
  it("프롬프트에 스니펫이 포함되어 있으면 true를 반환한다", () => {
    const prompt = "첫 번째 줄\n핵심만 3줄 이내로 짧게 정리해주세요.\n세 번째 줄";
    expect(hasSnippet(prompt, "핵심만 3줄 이내로 짧게 정리해주세요.")).toBe(true);
  });

  it("프롬프트에 스니펫이 없으면 false를 반환한다", () => {
    const prompt = "첫 번째 줄\n두 번째 줄";
    expect(hasSnippet(prompt, "핵심만 3줄 이내로 짧게 정리해주세요.")).toBe(false);
  });

  it("앞뒤 공백이 있어도 매칭된다", () => {
    const prompt = "  핵심만 3줄 이내로 짧게 정리해주세요.  ";
    expect(hasSnippet(prompt, "핵심만 3줄 이내로 짧게 정리해주세요.")).toBe(true);
  });

  it("빈 프롬프트에서는 false를 반환한다", () => {
    expect(hasSnippet("", "스니펫")).toBe(false);
  });

  it("여러 줄 중 중간에 위치한 스니펫도 찾는다", () => {
    const prompt = "라인1\n라인2\n대상 스니펫\n라인4";
    expect(hasSnippet(prompt, "대상 스니펫")).toBe(true);
  });

  it("부분 일치는 매칭하지 않는다", () => {
    const prompt = "핵심만 3줄 이내로 짧게 정리해주세요. 추가 텍스트";
    expect(hasSnippet(prompt, "핵심만 3줄 이내로 짧게 정리해주세요.")).toBe(false);
  });
});

describe("makeForm helper", () => {
  it("기본 QuickSetupForm을 생성한다", () => {
    const form = makeForm({});
    expect(form.entries).toEqual([]);
    expect(form.newsRegion).toBe("domestic");
    expect(form.deliveryPreset).toBe("WEEKDAYS");
  });

  it("overrides가 기본값을 덮어쓴다", () => {
    const form = makeForm({ newsRegion: "international" });
    expect(form.newsRegion).toBe("international");
  });
});
