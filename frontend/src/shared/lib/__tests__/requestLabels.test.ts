import { describe, expect, it } from "vitest";
import {
  buildUserRequestNameCountMap,
  formatUserRequestDisambiguation,
  formatUserRequestNote,
  formatUserRequestName
} from "../requestLabels";

describe("buildUserRequestNameCountMap", () => {
  it("counts duplicate names", () => {
    const requests = [{ requestName: "AI 뉴스" }, { requestName: "AI 뉴스" }, { requestName: "ESG 뉴스" }];
    const counts = buildUserRequestNameCountMap(requests);
    expect(counts["AI 뉴스"]).toBe(2);
    expect(counts["ESG 뉴스"]).toBe(1);
  });

  it("trims whitespace in names", () => {
    const requests = [{ requestName: " AI 뉴스 " }, { requestName: "AI 뉴스" }];
    const counts = buildUserRequestNameCountMap(requests);
    expect(counts["AI 뉴스"]).toBe(2);
  });
});

describe("formatUserRequestDisambiguation", () => {
  it("returns null when name is unique (count <= 1)", () => {
    const result = formatUserRequestDisambiguation(
      { requestName: "AI 뉴스", slackChannelId: "C01ABC", sourceName: "구글", createdAt: "2024-01-01" },
      { "AI 뉴스": 1 }
    );
    expect(result).toBeNull();
  });

  it("returns destination + sourceName when duplicated", () => {
    const result = formatUserRequestDisambiguation(
      { requestName: "AI 뉴스", slackChannelId: "C01ABC", sourceName: "구글 뉴스", createdAt: "2024-01-01" },
      { "AI 뉴스": 2 }
    );
    expect(result).toContain("Slack 채널");
    expect(result).toContain("구글 뉴스");
  });

  it("falls back to createdAt when sourceName is empty", () => {
    const result = formatUserRequestDisambiguation(
      { requestName: "AI 뉴스", slackChannelId: "", sourceName: "", createdAt: "2024-06-15T03:30:00Z" },
      { "AI 뉴스": 2 }
    );
    expect(result).toContain("Slack DM");
    expect(result).toMatch(/2024/);
  });

  it("returns null when nameCounts is undefined", () => {
    const result = formatUserRequestDisambiguation({
      requestName: "AI 뉴스",
      slackChannelId: "",
      sourceName: "",
      createdAt: "2024-01-01"
    });
    expect(result).toBeNull();
  });
});

describe("formatUserRequestNote", () => {
  it("strips [baseRequestId=...] metadata tag", () => {
    const result = formatUserRequestNote("사용자 메모 [baseRequestId=abc-123]");
    expect(result).toBe("사용자 메모");
  });

  it("returns null for empty note after stripping", () => {
    expect(formatUserRequestNote("[baseRequestId=abc-123]")).toBeNull();
  });

  it("returns null for null/undefined input", () => {
    expect(formatUserRequestNote(null)).toBeNull();
    expect(formatUserRequestNote(undefined)).toBeNull();
  });

  it("preserves note without metadata tags", () => {
    expect(formatUserRequestNote("일반 메모")).toBe("일반 메모");
  });

  it("strips multiple [baseRequestId=...] tags", () => {
    const result = formatUserRequestNote("[baseRequestId=abc] 메모 [baseRequestId=def]");
    expect(result).toBe("메모");
  });

  it("preserves other bracket tags like [위자드] and [설정 변경]", () => {
    const result = formatUserRequestNote("메모 [위자드] 내용 [설정 변경]");
    expect(result).toContain("[위자드]");
    expect(result).toContain("[설정 변경]");
  });

  it("[baseRequestId=...] in middle of text is removed", () => {
    const result = formatUserRequestNote("앞 텍스트 [baseRequestId=xyz-999] 뒷 텍스트");
    expect(result).toBe("앞 텍스트  뒷 텍스트");
  });
});

describe("buildUserRequestNameCountMap — additional edge cases", () => {
  it("returns empty object for empty array", () => {
    expect(buildUserRequestNameCountMap([])).toEqual({});
  });

  it("returns count of 1 for single item", () => {
    const counts = buildUserRequestNameCountMap([{ requestName: "단일 뉴스" }]);
    expect(counts["단일 뉴스"]).toBe(1);
  });
});

describe("formatUserRequestName", () => {
  it("trims whitespace from request name", () => {
    expect(formatUserRequestName({ requestName: "  AI 뉴스  " })).toBe("AI 뉴스");
  });

  it("returns empty string for whitespace-only name", () => {
    expect(formatUserRequestName({ requestName: "   " })).toBe("");
  });
});
