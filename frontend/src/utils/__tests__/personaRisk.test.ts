import { describe, it, expect } from "vitest";
import {
  hasVariation,
  isInProgressWeek,
  dismissKey,
  pruneDismissKeys,
  sortByPersistentWeeksAsc,
} from "@/utils/personaRisk";

// ---------------------------------------------------------------------------
// hasVariation
// ---------------------------------------------------------------------------

describe("hasVariation", () => {
  it("returns true when range spans the default minDelta of 1", () => {
    expect(hasVariation([0, 1, 2, 3, 4, 5])).toBe(true);
  });

  it("returns false for an all-equal series", () => {
    expect(hasVariation([5, 5, 5])).toBe(false);
  });

  it("returns false when all values are null", () => {
    expect(hasVariation([null, null, null])).toBe(false);
  });

  it("returns false when only one non-null value is present", () => {
    expect(hasVariation([null, 7, null])).toBe(false);
  });

  it("handles negative values via max - min", () => {
    expect(hasVariation([-3, -1])).toBe(true);
    expect(hasVariation([-3, -3, -3])).toBe(false);
  });

  it("respects a higher minDelta threshold", () => {
    // Range = 3, below the threshold of 5.
    expect(hasVariation([10, 13], 5)).toBe(false);
    // Range = 6, meets the threshold of 5.
    expect(hasVariation([10, 16], 5)).toBe(true);
  });

  it("ignores null entries mixed with values", () => {
    expect(hasVariation([null, 2, null, 5])).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// isInProgressWeek
// ---------------------------------------------------------------------------

describe("isInProgressWeek", () => {
  // KST today = 2026-04-15 (Wed, ISO week 16).
  // Use a UTC time that clearly maps to the intended KST calendar date.
  const kstWed2026W16 = new Date(Date.UTC(2026, 3, 15, 6, 0, 0)); // 2026-04-15 15:00 KST

  it("returns true when weekIso matches the current ISO week in KST", () => {
    expect(isInProgressWeek("2026-W16", kstWed2026W16)).toBe(true);
  });

  it("returns false for the previous week", () => {
    expect(isInProgressWeek("2026-W15", kstWed2026W16)).toBe(false);
  });

  it("returns false for the next week", () => {
    expect(isInProgressWeek("2026-W17", kstWed2026W16)).toBe(false);
  });

  it("treats the Monday starting W16 (2026-04-13 KST) as in progress", () => {
    const monKst = new Date(Date.UTC(2026, 3, 13, 3, 0, 0)); // 12:00 KST Monday
    expect(isInProgressWeek("2026-W16", monKst)).toBe(true);
    expect(isInProgressWeek("2026-W15", monKst)).toBe(false);
  });

  it("treats the Sunday ending W16 (2026-04-19 KST) as in progress", () => {
    const sunKst = new Date(Date.UTC(2026, 3, 19, 14, 0, 0)); // 23:00 KST Sunday
    expect(isInProgressWeek("2026-W16", sunKst)).toBe(true);
    expect(isInProgressWeek("2026-W17", sunKst)).toBe(false);
  });

  it("treats the following Monday (2026-04-20 KST) as the start of W17", () => {
    const nextMonKst = new Date(Date.UTC(2026, 3, 20, 3, 0, 0));
    expect(isInProgressWeek("2026-W17", nextMonKst)).toBe(true);
    expect(isInProgressWeek("2026-W16", nextMonKst)).toBe(false);
  });

  it("returns false for a malformed weekIso string", () => {
    expect(isInProgressWeek("2026W16", kstWed2026W16)).toBe(false);
    expect(isInProgressWeek("", kstWed2026W16)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// dismissKey
// ---------------------------------------------------------------------------

describe("dismissKey", () => {
  it("joins personaId, signalType, and weekIso with colons", () => {
    expect(dismissKey("p1", "CHURN_EXCESS", "2026-W16")).toBe(
      "p1:CHURN_EXCESS:2026-W16",
    );
  });

  it("preserves arbitrary characters verbatim", () => {
    expect(dismissKey("uuid-abc", "IDLE", "2025-W01")).toBe(
      "uuid-abc:IDLE:2025-W01",
    );
  });
});

// ---------------------------------------------------------------------------
// pruneDismissKeys
// ---------------------------------------------------------------------------

describe("pruneDismissKeys", () => {
  it("keeps a key whose week equals the current week", () => {
    const keys = ["p1:CHURN_EXCESS:2026-W16"];
    expect(pruneDismissKeys(keys, "2026-W16")).toEqual(keys);
  });

  it("keeps a key exactly 4 weeks older than the current week", () => {
    const keys = ["p1:IDLE:2026-W12"];
    expect(pruneDismissKeys(keys, "2026-W16")).toEqual(keys);
  });

  it("drops a key 5 weeks older than the current week", () => {
    const keys = ["p1:IDLE:2026-W11"];
    expect(pruneDismissKeys(keys, "2026-W16")).toEqual([]);
  });

  it("handles year rollover — W51 2025 vs W01 2026 is 2 weeks older", () => {
    const keys = ["p1:IDLE:2025-W51"];
    expect(pruneDismissKeys(keys, "2026-W01")).toEqual(keys);
  });

  it("drops all keys when every entry is too old", () => {
    const keys = [
      "p1:CHURN_EXCESS:2025-W01",
      "p2:IDLE:2025-W10",
    ];
    expect(pruneDismissKeys(keys, "2026-W16")).toEqual([]);
  });

  it("keeps malformed keys untouched (best-effort)", () => {
    const keys = ["not-a-key", "onlyone:piece"];
    expect(pruneDismissKeys(keys, "2026-W16")).toEqual(keys);
  });

  it("returns a copy when currentWeekIso is malformed", () => {
    const keys = ["p1:IDLE:2025-W10"];
    const result = pruneDismissKeys(keys, "bad");
    expect(result).toEqual(keys);
    expect(result).not.toBe(keys);
  });
});

// ---------------------------------------------------------------------------
// sortByPersistentWeeksAsc
// ---------------------------------------------------------------------------

describe("sortByPersistentWeeksAsc", () => {
  type Item = { id: string; persistentWeeks: number; delta: number };

  it("sorts ascending by persistentWeeks", () => {
    const items: Item[] = [
      { id: "b", persistentWeeks: 3, delta: 0 },
      { id: "a", persistentWeeks: 1, delta: 0 },
      { id: "c", persistentWeeks: 2, delta: 0 },
    ];
    const sorted = sortByPersistentWeeksAsc(items, (x) => x.delta);
    expect(sorted.map((x) => x.id)).toEqual(["a", "c", "b"]);
  });

  it("breaks ties by larger absolute secondary value first", () => {
    const items: Item[] = [
      { id: "small", persistentWeeks: 1, delta: 5 },
      { id: "big-neg", persistentWeeks: 1, delta: -30 },
      { id: "mid", persistentWeeks: 1, delta: 12 },
    ];
    const sorted = sortByPersistentWeeksAsc(items, (x) => x.delta);
    expect(sorted.map((x) => x.id)).toEqual(["big-neg", "mid", "small"]);
  });

  it("does not mutate the input array", () => {
    const items: Item[] = [
      { id: "b", persistentWeeks: 2, delta: 1 },
      { id: "a", persistentWeeks: 1, delta: 2 },
    ];
    const copy = items.slice();
    sortByPersistentWeeksAsc(items, (x) => x.delta);
    expect(items).toEqual(copy);
  });
});
