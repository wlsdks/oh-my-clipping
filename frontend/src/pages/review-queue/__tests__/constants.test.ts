import { describe, it, expect } from "vitest";
import { getImportanceLevel, getActionButtons, getWeightedActionButtons } from "../model/constants";

describe("getImportanceLevel", () => {
  it("0.7+ → high", () => {
    expect(getImportanceLevel(0.7)).toBe("high");
    expect(getImportanceLevel(1.0)).toBe("high");
  });

  it("0.4–0.69 → medium", () => {
    expect(getImportanceLevel(0.4)).toBe("medium");
    expect(getImportanceLevel(0.69)).toBe("medium");
  });

  it("0–0.39 → low", () => {
    expect(getImportanceLevel(0.0)).toBe("low");
    expect(getImportanceLevel(0.39)).toBe("low");
  });
});

describe("getActionButtons", () => {
  it("REVIEW → [건너뛰기, 보내기]", () => {
    const buttons = getActionButtons("REVIEW");
    expect(buttons.map((b) => b.label)).toEqual(["건너뛰기", "보내기"]);
    expect(buttons.map((b) => b.action)).toEqual(["exclude", "approve"]);
  });

  it("INCLUDE → [재검토, 건너뛰기]", () => {
    const buttons = getActionButtons("INCLUDE");
    expect(buttons.map((b) => b.label)).toEqual(["재검토", "건너뛰기"]);
    expect(buttons.map((b) => b.action)).toEqual(["review", "exclude"]);
  });

  it("EXCLUDE → [재검토, 보내기]", () => {
    const buttons = getActionButtons("EXCLUDE");
    expect(buttons.map((b) => b.label)).toEqual(["재검토", "보내기"]);
    expect(buttons.map((b) => b.action)).toEqual(["review", "approve"]);
  });
});

describe("getWeightedActionButtons", () => {
  it("REVIEW status + INCLUDE suggested → 보내기 is default, 건너뛰기 is ghost", () => {
    const buttons = getWeightedActionButtons("REVIEW", "INCLUDE");
    const send = buttons.find((b) => b.action === "approve")!;
    const skip = buttons.find((b) => b.action === "exclude")!;
    expect(send.variant).toBe("default");
    expect(skip.variant).toBe("ghost");
  });

  it("REVIEW status + EXCLUDE suggested → 건너뛰기 is default, 보내기 is ghost", () => {
    const buttons = getWeightedActionButtons("REVIEW", "EXCLUDE");
    const send = buttons.find((b) => b.action === "approve")!;
    const skip = buttons.find((b) => b.action === "exclude")!;
    expect(skip.variant).toBe("default");
    expect(send.variant).toBe("ghost");
  });

  it("REVIEW status + REVIEW suggested → original variants (outline/default)", () => {
    const buttons = getWeightedActionButtons("REVIEW", "REVIEW");
    const skip = buttons.find((b) => b.action === "exclude")!;
    const send = buttons.find((b) => b.action === "approve")!;
    expect(skip.variant).toBe("outline");
    expect(send.variant).toBe("default");
  });

  it("INCLUDE status → uses base getActionButtons (no AI weighting)", () => {
    const buttons = getWeightedActionButtons("INCLUDE", "INCLUDE");
    expect(buttons.map((b) => b.action)).toEqual(["review", "exclude"]);
  });

  it("EXCLUDE status → uses base getActionButtons (no AI weighting)", () => {
    const buttons = getWeightedActionButtons("EXCLUDE", "INCLUDE");
    expect(buttons.map((b) => b.action)).toEqual(["review", "approve"]);
  });
});
