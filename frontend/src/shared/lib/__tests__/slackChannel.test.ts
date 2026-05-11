import { describe, expect, it } from "vitest";
import {
  normalizeSlackChannelInput,
  validateSlackChannelInput,
  isSlackDirectMessageDestination,
  formatSlackDestinationLabel,
  slackChannelNormalizationHint,
  formatSlackDestinationDescription
} from "../slackChannel";

describe("normalizeSlackChannelInput", () => {
  it("extracts channel ID from Slack archive URL", () => {
    expect(normalizeSlackChannelInput("https://myteam.slack.com/archives/C01ABCDEF99")).toBe("C01ABCDEF99");
  });

  it("extracts channel ID from URL with query param", () => {
    expect(normalizeSlackChannelInput("https://app.slack.com/client?channel=C01ABCDEF99")).toBe("C01ABCDEF99");
  });

  it("extracts channel ID from mention format <#C01ABCDEF99|general>", () => {
    expect(normalizeSlackChannelInput("<#C01ABCDEF99|general>")).toBe("C01ABCDEF99");
  });

  it("strips # prefix and normalizes", () => {
    expect(normalizeSlackChannelInput("#C01ABCDEF99")).toBe("C01ABCDEF99");
  });

  it("strips id: prefix (case-insensitive)", () => {
    expect(normalizeSlackChannelInput("id:C01ABCDEF99")).toBe("C01ABCDEF99");
    expect(normalizeSlackChannelInput("ID:c01abcdef99")).toBe("C01ABCDEF99");
  });

  it("accepts raw valid channel ID", () => {
    expect(normalizeSlackChannelInput("C01ABCDEF99")).toBe("C01ABCDEF99");
  });

  it("accepts G-prefix group ID", () => {
    expect(normalizeSlackChannelInput("G01ABCDEF99")).toBe("G01ABCDEF99");
  });

  it("returns null for empty string", () => {
    expect(normalizeSlackChannelInput("")).toBeNull();
    expect(normalizeSlackChannelInput("   ")).toBeNull();
  });

  it("returns null for invalid format", () => {
    expect(normalizeSlackChannelInput("not-a-channel")).toBeNull();
    expect(normalizeSlackChannelInput("X12345678")).toBeNull();
  });

  it("uppercases the result", () => {
    expect(normalizeSlackChannelInput("c01abcdef99")).toBe("C01ABCDEF99");
  });
});

describe("validateSlackChannelInput", () => {
  it("valid channel ID returns isValid true", () => {
    const result = validateSlackChannelInput("C01ABCDEF99");
    expect(result.isValid).toBe(true);
    expect(result.normalized).toBe("C01ABCDEF99");
    expect(result.message).toBeNull();
  });

  it("blank with allowBlank=true (default) returns isValid true", () => {
    const result = validateSlackChannelInput("");
    expect(result.isValid).toBe(true);
    expect(result.normalized).toBe("");
    expect(result.message).toBeNull();
  });

  it("blank with allowBlank=false returns isValid false with message", () => {
    const result = validateSlackChannelInput("", { allowBlank: false });
    expect(result.isValid).toBe(false);
    expect(result.message).toContain("입력하세요");
  });

  it("invalid format returns isValid false with hint message", () => {
    const result = validateSlackChannelInput("random-text");
    expect(result.isValid).toBe(false);
    expect(result.message).toContain("채널 ID 형식");
  });

  it("uses custom fieldLabel in error message", () => {
    const result = validateSlackChannelInput("", { allowBlank: false, fieldLabel: "알림 채널" });
    expect(result.message).toContain("알림 채널");
  });
});

describe("isSlackDirectMessageDestination", () => {
  it("empty/null/undefined => true (DM)", () => {
    expect(isSlackDirectMessageDestination(null)).toBe(true);
    expect(isSlackDirectMessageDestination(undefined)).toBe(true);
    expect(isSlackDirectMessageDestination("")).toBe(true);
  });

  it("D-prefix => true (DM)", () => {
    expect(isSlackDirectMessageDestination("D01ABCDEF99")).toBe(true);
  });

  it("C-prefix => false (channel)", () => {
    expect(isSlackDirectMessageDestination("C01ABCDEF99")).toBe(false);
  });
});

describe("formatSlackDestinationLabel", () => {
  it("null => 'Slack DM'", () => {
    expect(formatSlackDestinationLabel(null)).toBe("Slack DM");
  });

  it("D-prefix => 'Slack DM'", () => {
    expect(formatSlackDestinationLabel("D01ABCDEF99")).toBe("Slack DM");
  });

  it("normal ID => '#' prefix", () => {
    expect(formatSlackDestinationLabel("C01ABCDEF99")).toBe("#C01ABCDEF99");
  });

  it("already has # prefix => returned as-is", () => {
    expect(formatSlackDestinationLabel("#general")).toBe("#general");
  });

  it("respects custom blankLabel", () => {
    expect(formatSlackDestinationLabel("", { blankLabel: "DM 수신" })).toBe("DM 수신");
  });

  it("respects genericChannelLabel", () => {
    expect(formatSlackDestinationLabel("C01ABCDEF99", { genericChannelLabel: "Slack 채널" })).toBe("Slack 채널");
  });
});

describe("slackChannelNormalizationHint", () => {
  it("returns null for empty string", () => {
    expect(slackChannelNormalizationHint("")).toBeNull();
  });

  it("returns null for whitespace-only string", () => {
    expect(slackChannelNormalizationHint("   ")).toBeNull();
  });

  it("returns null for already normalized channel ID (no transformation)", () => {
    expect(slackChannelNormalizationHint("C01ABCDEF99")).toBeNull();
  });

  it("returns hint string for URL input", () => {
    const hint = slackChannelNormalizationHint("https://myteam.slack.com/archives/C01ABCDEF99");
    expect(hint).toContain("C01ABCDEF99");
    expect(hint).toContain("채널 ID");
  });

  it("returns null for lowercase channel ID (only case differs, no hint needed)", () => {
    // trimmed.toUpperCase() === normalized, so no hint is shown
    expect(slackChannelNormalizationHint("c01abcdef99")).toBeNull();
  });

  it("returns hint string for mention format (meaningful transformation)", () => {
    const hint = slackChannelNormalizationHint("<#C01ABCDEF99|general>");
    expect(hint).not.toBeNull();
    expect(hint).toContain("C01ABCDEF99");
  });

  it("returns null for invalid input (cannot normalize)", () => {
    expect(slackChannelNormalizationHint("not-a-channel")).toBeNull();
  });
});

describe("formatSlackDestinationDescription", () => {
  it("returns 'Slack DM으로 수신' for null", () => {
    expect(formatSlackDestinationDescription(null)).toBe("Slack DM으로 수신");
  });

  it("returns 'Slack DM으로 수신' for empty string", () => {
    expect(formatSlackDestinationDescription("")).toBe("Slack DM으로 수신");
  });

  it("returns DM description for D-prefix ID", () => {
    expect(formatSlackDestinationDescription("D01ABCDEF99")).toBe("Slack DM으로 수신");
  });

  it("returns channel description for channel ID", () => {
    const result = formatSlackDestinationDescription("C01ABCDEF99");
    expect(result).toContain("#C01ABCDEF99");
    expect(result).toContain("로 수신");
  });

  it("respects custom blankLabel for DM", () => {
    expect(formatSlackDestinationDescription(null, { blankLabel: "DM으로 받기" })).toBe("DM으로 받기");
  });
});

describe("normalizeSlackChannelInput — additional edge cases", () => {
  it("extracts channel ID from hash URL with channel= param", () => {
    // hash contains channel=
    const url = "https://app.slack.com/client/T12345#channel=C01ABCDEF99";
    expect(normalizeSlackChannelInput(url)).toBe("C01ABCDEF99");
  });

  it("rejects exactly 8-char ID (boundary, needs 8+ after prefix)", () => {
    // Pattern is /^[CG][A-Z0-9]{8,}$/ — "C" + 8 chars = 9 chars total => valid
    // "C" + 7 chars = 8 chars total => invalid
    expect(normalizeSlackChannelInput("C1234567")).toBeNull();
    expect(normalizeSlackChannelInput("C12345678")).toBe("C12345678");
  });

  it("whitespace-only input with allowBlank=false fails validation", () => {
    const result = validateSlackChannelInput("   ", { allowBlank: false });
    expect(result.isValid).toBe(false);
    expect(result.message).toContain("입력하세요");
  });
});
