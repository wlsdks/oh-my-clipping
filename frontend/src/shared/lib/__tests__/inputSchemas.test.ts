import { describe, expect, it } from "vitest";
import { adminInputSchemas, slackChannelIdPattern, userInputSchemas } from "../inputSchemas";

describe("adminInputSchemas", () => {
  describe("personaName", () => {
    it("정상 입력은 통과한다", () => {
      const result = adminInputSchemas.personaName.safeParse("경영진 브리핑");
      expect(result.success).toBe(true);
    });

    it("빈 문자열은 거부한다", () => {
      const result = adminInputSchemas.personaName.safeParse("");
      expect(result.success).toBe(false);
    });

    it("공백만 있는 입력은 trim 후 거부한다", () => {
      const result = adminInputSchemas.personaName.safeParse("   ");
      expect(result.success).toBe(false);
    });

    it("200자 경계값은 허용한다", () => {
      const input = "a".repeat(200);
      const result = adminInputSchemas.personaName.safeParse(input);
      expect(result.success).toBe(true);
    });

    it("201자는 거부한다", () => {
      const input = "a".repeat(201);
      const result = adminInputSchemas.personaName.safeParse(input);
      expect(result.success).toBe(false);
    });

    it("앞뒤 공백을 trim한다", () => {
      const result = adminInputSchemas.personaName.safeParse("  이름  ");
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe("이름");
      }
    });
  });

  describe("personaSystemPrompt", () => {
    it("5000자 경계값은 허용한다", () => {
      const result = adminInputSchemas.personaSystemPrompt.safeParse("a".repeat(5000));
      expect(result.success).toBe(true);
    });

    it("5001자는 거부한다", () => {
      const result = adminInputSchemas.personaSystemPrompt.safeParse("a".repeat(5001));
      expect(result.success).toBe(false);
    });

    it("빈 문자열은 필수 검증에 실패한다", () => {
      const result = adminInputSchemas.personaSystemPrompt.safeParse("");
      expect(result.success).toBe(false);
    });
  });

  describe("competitorName", () => {
    it("100자 경계값은 허용한다", () => {
      const result = adminInputSchemas.competitorName.safeParse("a".repeat(100));
      expect(result.success).toBe(true);
    });

    it("101자는 거부한다", () => {
      const result = adminInputSchemas.competitorName.safeParse("a".repeat(101));
      expect(result.success).toBe(false);
    });
  });

  describe("categoryName", () => {
    it("200자 경계값은 허용한다", () => {
      const result = adminInputSchemas.categoryName.safeParse("a".repeat(200));
      expect(result.success).toBe(true);
    });

    it("201자는 거부한다", () => {
      const result = adminInputSchemas.categoryName.safeParse("a".repeat(201));
      expect(result.success).toBe(false);
    });
  });

  describe("sourceUrl", () => {
    it("유효한 URL은 통과한다", () => {
      const result = adminInputSchemas.sourceUrl.safeParse("https://example.com/rss");
      expect(result.success).toBe(true);
    });

    it("프로토콜 없는 문자열은 거부한다", () => {
      const result = adminInputSchemas.sourceUrl.safeParse("example.com");
      expect(result.success).toBe(false);
    });

    it("빈 문자열은 거부한다", () => {
      const result = adminInputSchemas.sourceUrl.safeParse("");
      expect(result.success).toBe(false);
    });
  });

  describe("channelId", () => {
    it("C 접두어 채널 ID를 허용한다", () => {
      const result = adminInputSchemas.channelId.safeParse("C01ABCDEF12");
      expect(result.success).toBe(true);
    });

    it("D 접두어 DM 채널 ID를 허용한다", () => {
      const result = adminInputSchemas.channelId.safeParse("D01ABCDEF12");
      expect(result.success).toBe(true);
    });

    it("소문자는 거부한다", () => {
      const result = adminInputSchemas.channelId.safeParse("c01abcdef12");
      expect(result.success).toBe(false);
    });

    it("undefined는 허용한다 (optional)", () => {
      const result = adminInputSchemas.channelId.safeParse(undefined);
      expect(result.success).toBe(true);
    });
  });
});

describe("userInputSchemas", () => {
  describe("requestName", () => {
    it("120자 경계값은 허용한다 (DB VARCHAR 120)", () => {
      const result = userInputSchemas.requestName.safeParse("a".repeat(120));
      expect(result.success).toBe(true);
    });

    it("121자는 거부한다", () => {
      const result = userInputSchemas.requestName.safeParse("a".repeat(121));
      expect(result.success).toBe(false);
    });
  });

  describe("requestNote", () => {
    it("빈 문자열은 optional이라 통과한다", () => {
      const result = userInputSchemas.requestNote.safeParse("");
      expect(result.success).toBe(true);
    });

    it("undefined는 통과한다", () => {
      const result = userInputSchemas.requestNote.safeParse(undefined);
      expect(result.success).toBe(true);
    });

    it("1000자 경계값은 허용한다", () => {
      const result = userInputSchemas.requestNote.safeParse("a".repeat(1000));
      expect(result.success).toBe(true);
    });

    it("1001자는 거부한다", () => {
      const result = userInputSchemas.requestNote.safeParse("a".repeat(1001));
      expect(result.success).toBe(false);
    });
  });

  describe("personaPrompt", () => {
    it("빈 문자열은 거부한다", () => {
      const result = userInputSchemas.personaPrompt.safeParse("");
      expect(result.success).toBe(false);
    });

    it("5000자는 허용한다", () => {
      const result = userInputSchemas.personaPrompt.safeParse("a".repeat(5000));
      expect(result.success).toBe(true);
    });
  });
});

describe("slackChannelIdPattern", () => {
  it("C/G/D/U 접두어 + 9자 이상을 매칭한다", () => {
    expect(slackChannelIdPattern.test("C01ABCDEF12")).toBe(true);
    expect(slackChannelIdPattern.test("G01ABCDEF12")).toBe(true);
    expect(slackChannelIdPattern.test("D01ABCDEF12")).toBe(true);
    expect(slackChannelIdPattern.test("U01ABCDEF12")).toBe(true);
  });

  it("다른 접두어는 거부한다", () => {
    expect(slackChannelIdPattern.test("A01ABCDEF12")).toBe(false);
  });

  it("8자 이하는 거부한다 (최소 9자)", () => {
    expect(slackChannelIdPattern.test("C01ABCDE")).toBe(false);
  });
});
