import { describe, expect, it } from "vitest";
import { stripGeneratedPersonaSuffix, buildPersonaLabelMap, formatPersonaDisplayName } from "../personaLabels";
import type { Persona } from "../../types/admin";

function makePersona(overrides: Partial<Persona> & { id: string; name: string }): Persona {
  return {
    systemPrompt: "",
    maxItems: 5,
    language: "ko",
    isActive: true,
    currentVersion: 1,
    previewTitle: null,
    previewSource: null,
    previewBody: null,
    tone: null,
    lengthPref: null,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    ...overrides
  };
}

describe("stripGeneratedPersonaSuffix", () => {
  it("removes 8-digit hex suffix", () => {
    expect(stripGeneratedPersonaSuffix("기본 스타일-a1b2c3d4")).toBe("기본 스타일");
  });

  it("leaves name without suffix unchanged", () => {
    expect(stripGeneratedPersonaSuffix("기본 스타일")).toBe("기본 스타일");
  });

  it("returns empty string for null/undefined/empty", () => {
    expect(stripGeneratedPersonaSuffix(null)).toBe("");
    expect(stripGeneratedPersonaSuffix(undefined)).toBe("");
    expect(stripGeneratedPersonaSuffix("")).toBe("");
  });

  it("does NOT strip 7-char suffix (too short)", () => {
    // pattern requires exactly 8 hex chars: -[0-9a-f]{8}$
    expect(stripGeneratedPersonaSuffix("기본 스타일-a1b2c3d")).toBe("기본 스타일-a1b2c3d");
  });

  it("does NOT strip 9-char suffix (too long)", () => {
    expect(stripGeneratedPersonaSuffix("기본 스타일-a1b2c3d4e")).toBe("기본 스타일-a1b2c3d4e");
  });

  it("strips uppercase hex suffix (case-insensitive)", () => {
    expect(stripGeneratedPersonaSuffix("기본 스타일-A1B2C3D4")).toBe("기본 스타일");
  });

  it("returns empty string when name is only the generated suffix", () => {
    // name is "-a1b2c3d4", stripping gives "" then trim gives ""
    expect(stripGeneratedPersonaSuffix("-a1b2c3d4")).toBe("");
  });
});

describe("buildPersonaLabelMap", () => {
  it("returns empty object for empty array", () => {
    expect(buildPersonaLabelMap([])).toEqual({});
  });

  it("unique display names have suffix stripped", () => {
    const personas = [
      makePersona({ id: "p1", name: "요약 스타일-a1b2c3d4" }),
      makePersona({ id: "p2", name: "분석 스타일-b2c3d4e5" })
    ];
    const map = buildPersonaLabelMap(personas);
    expect(map["p1"]).toBe("요약 스타일");
    expect(map["p2"]).toBe("분석 스타일");
  });

  it("duplicate display names get disambiguated by createdAt", () => {
    const personas = [
      makePersona({ id: "p1", name: "요약 스타일-a1b2c3d4", createdAt: "2024-01-01T00:00:00Z" }),
      makePersona({ id: "p2", name: "요약 스타일-b2c3d4e5", createdAt: "2024-06-01T00:00:00Z" })
    ];
    const map = buildPersonaLabelMap(personas);
    // Both should contain the base name plus a time suffix
    expect(map["p1"]).toContain("요약 스타일");
    expect(map["p2"]).toContain("요약 스타일");
    expect(map["p1"]).toContain("·");
    expect(map["p2"]).toContain("·");
    // They should be different from each other
    expect(map["p1"]).not.toBe(map["p2"]);
  });
});

describe("formatPersonaDisplayName", () => {
  it("no duplicates: returns stripped name", () => {
    const persona = makePersona({ id: "p1", name: "요약 스타일-a1b2c3d4" });
    expect(formatPersonaDisplayName(persona)).toBe("요약 스타일");
  });

  it("duplicates: returns 'displayName · createdAt'", () => {
    const persona = makePersona({ id: "p1", name: "요약 스타일-a1b2c3d4", createdAt: "2024-06-15T03:30:00Z" });
    const nameCounts = { "요약 스타일": 2 };
    const result = formatPersonaDisplayName(persona, nameCounts);
    expect(result).toContain("요약 스타일");
    expect(result).toContain("·");
    expect(result).toContain("2024");
  });

  it("empty display name after strip falls back to original name", () => {
    // suffix-only name: strip leaves empty => fallback to persona.name
    const persona = makePersona({ id: "p1", name: "-a1b2c3d4" });
    const result = formatPersonaDisplayName(persona);
    expect(result).toBe("-a1b2c3d4");
  });
});
