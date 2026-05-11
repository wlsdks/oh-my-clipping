import { describe, expect, it } from "vitest";
import {
  buildGoogleNewsRssUrl,
  parseSourceUrl,
  isValidHttpUrl,
  createQuickSetupForm,
  entryToSourceName,
  autoTopicName
} from "../quickSetupTypes";
import type { KeywordEntry } from "../quickSetupTypes";

describe("buildGoogleNewsRssUrl", () => {
  it("builds domestic URL with keyword only", () => {
    const url = buildGoogleNewsRssUrl("AI");
    expect(url).toContain("news.google.com/rss/search");
    expect(url).toContain("q=AI");
    expect(url).toContain("hl=ko&gl=KR&ceid=KR:ko");
  });

  it("builds international URL", () => {
    const url = buildGoogleNewsRssUrl("AI", undefined, "international");
    expect(url).toContain("hl=en&gl=US&ceid=US:en");
  });

  it("appends single site filter", () => {
    const url = buildGoogleNewsRssUrl("AI", ["example-business.com"]);
    const decoded = decodeURIComponent(url);
    expect(decoded).toContain("site:example-business.com");
    expect(decoded).not.toContain("OR");
  });

  it("appends multiple site filters with OR", () => {
    const url = buildGoogleNewsRssUrl("AI", ["example-business.com", "example-economy.com"]);
    const decoded = decodeURIComponent(url);
    expect(decoded).toContain("(site:example-business.com OR site:example-economy.com)");
  });

  it("encodes special characters in keyword", () => {
    const url = buildGoogleNewsRssUrl("AI & ML");
    expect(url).toContain(encodeURIComponent("AI & ML"));
  });
});

describe("parseSourceUrl", () => {
  it("detects domestic Google News RSS", () => {
    const url = buildGoogleNewsRssUrl("AI", [], "domestic");
    const result = parseSourceUrl(url);
    expect(result.newsRegion).toBe("domestic");
    expect(result.isDirectSource).toBe(false);
  });

  it("detects international Google News RSS", () => {
    const url = buildGoogleNewsRssUrl("AI", [], "international");
    const result = parseSourceUrl(url);
    expect(result.newsRegion).toBe("international");
  });

  it("extracts site filters from URL", () => {
    const url = buildGoogleNewsRssUrl("AI", ["example-business.com", "example-economy.com"]);
    const result = parseSourceUrl(url);
    expect(result.siteFilters).toEqual(["example-business.com", "example-economy.com"]);
  });

  it("detects non-Google-News URL as direct source", () => {
    const result = parseSourceUrl("https://example.com/rss.xml");
    expect(result.isDirectSource).toBe(true);
  });

  it("returns defaults for empty string", () => {
    const result = parseSourceUrl("");
    expect(result.newsRegion).toBe("domestic");
    expect(result.siteFilters).toEqual([]);
    expect(result.isDirectSource).toBe(false);
  });

  it("roundtrips with buildGoogleNewsRssUrl (multiple sites)", () => {
    const url = buildGoogleNewsRssUrl("테스트", ["zdnet.co.kr", "example-business.com"], "domestic");
    const parsed = parseSourceUrl(url);
    expect(parsed.newsRegion).toBe("domestic");
    expect(parsed.siteFilters).toContain("zdnet.co.kr");
    expect(parsed.siteFilters).toContain("example-business.com");
    expect(parsed.isDirectSource).toBe(false);
  });
});

describe("isValidHttpUrl", () => {
  it("accepts http URL", () => {
    expect(isValidHttpUrl("http://example.com")).toBe(true);
  });

  it("accepts https URL", () => {
    expect(isValidHttpUrl("https://example.com/path")).toBe(true);
  });

  it("rejects ftp URL", () => {
    expect(isValidHttpUrl("ftp://example.com")).toBe(false);
  });

  it("rejects empty string", () => {
    expect(isValidHttpUrl("")).toBe(false);
  });

  it("rejects plain text", () => {
    expect(isValidHttpUrl("not a url")).toBe(false);
  });

  it("accepts URL with port number", () => {
    expect(isValidHttpUrl("https://example.com:8080/path")).toBe(true);
  });

  it("rejects javascript: protocol", () => {
    expect(isValidHttpUrl("javascript:alert(1)")).toBe(false);
  });
});

describe("createQuickSetupForm", () => {
  it("returns empty entries array", () => {
    expect(createQuickSetupForm().entries).toEqual([]);
  });

  it("returns maxItems=3", () => {
    expect(createQuickSetupForm().maxItems).toBe(3);
  });

  it("returns deliveryPreset='WEEKDAYS'", () => {
    expect(createQuickSetupForm().deliveryPreset).toBe("WEEKDAYS");
  });

  it("returns deliveryDays as weekdays", () => {
    expect(createQuickSetupForm().deliveryDays).toEqual(["MON", "TUE", "WED", "THU", "FRI"]);
  });

  it("returns deliveryHour=8", () => {
    expect(createQuickSetupForm().deliveryHour).toBe(8);
  });

  it("returns newsRegion='domestic'", () => {
    expect(createQuickSetupForm().newsRegion).toBe("domestic");
  });

  it("returns slackDeliveryMode='channel'", () => {
    expect(createQuickSetupForm().slackDeliveryMode).toBe("channel");
  });

  it("returns createPersona=true", () => {
    expect(createQuickSetupForm().createPersona).toBe(true);
  });

  it("returns includeSource=false", () => {
    expect(createQuickSetupForm().includeSource).toBe(false);
  });

  it("returns autoApproveSource=true", () => {
    expect(createQuickSetupForm().autoApproveSource).toBe(true);
  });

  it("returns slackChannelConfirmed=false", () => {
    expect(createQuickSetupForm().slackChannelConfirmed).toBe(false);
  });

  it("returns excludeKeywords as empty array", () => {
    expect(createQuickSetupForm().excludeKeywords).toEqual([]);
  });
});

describe("entryToSourceName", () => {
  const entry: KeywordEntry = { value: "AI", type: "keyword" };

  it("no region => '키워드 뉴스'", () => {
    expect(entryToSourceName(entry)).toBe("AI 뉴스");
  });

  it("domestic => no suffix", () => {
    expect(entryToSourceName(entry, "domestic")).toBe("AI 뉴스");
  });

  it("international => '키워드 뉴스 (해외)'", () => {
    expect(entryToSourceName(entry, "international")).toBe("AI 뉴스 (해외)");
  });
});

describe("autoTopicName", () => {
  it("empty array => empty string", () => {
    expect(autoTopicName([])).toBe("");
  });

  it("single item => '키워드 뉴스'", () => {
    const entries: KeywordEntry[] = [{ value: "AI", type: "keyword" }];
    expect(autoTopicName(entries)).toBe("AI 뉴스");
  });

  it("two items => 'AI·ESG 뉴스'", () => {
    const entries: KeywordEntry[] = [
      { value: "AI", type: "keyword" },
      { value: "ESG", type: "keyword" }
    ];
    expect(autoTopicName(entries)).toBe("AI·ESG 뉴스");
  });

  it("three items => 'AI·ESG·핀테크 뉴스'", () => {
    const entries: KeywordEntry[] = [
      { value: "AI", type: "keyword" },
      { value: "ESG", type: "keyword" },
      { value: "핀테크", type: "keyword" }
    ];
    expect(autoTopicName(entries)).toBe("AI·ESG·핀테크 뉴스");
  });
});

describe("buildGoogleNewsRssUrl — additional edge cases", () => {
  it("empty keyword produces URL with empty q param", () => {
    const url = buildGoogleNewsRssUrl("", undefined, "domestic");
    // empty keyword encoded as empty string
    expect(url).toContain("q=");
    expect(url).toContain("hl=ko&gl=KR");
  });

  it("empty sites array treats as no sites (no site: filter)", () => {
    const url = buildGoogleNewsRssUrl("AI", [], "domestic");
    const decoded = decodeURIComponent(url);
    expect(decoded).not.toContain("site:");
  });
});
