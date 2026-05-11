import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { RuleDryRunPreview } from "../RuleDryRunPreview";
import type { RuleDryRunResult } from "@/types/categoryRule";

function makeResult(overrides: Partial<RuleDryRunResult> = {}): RuleDryRunResult {
  return {
    analyzedCount: 100,
    wouldAutoExclude: 0,
    wouldStayUnchanged: 100,
    samples: [],
    ...overrides,
  };
}

describe("RuleDryRunPreview", () => {
  it("wouldAutoExclude 가 0 이면 '적용 대상 기사 없음' 을 렌더한다", () => {
    render(
      <RuleDryRunPreview
        result={makeResult({ analyzedCount: 42, wouldAutoExclude: 0 })}
      />,
    );

    // 요약 문구 — analyzedCount 는 42, wouldAutoExclude 는 0
    const summary = screen.getByTestId("rule-dry-run-preview");
    expect(summary).toHaveTextContent("지난 30일 42건 중");
    expect(summary).toHaveTextContent("0건 자동 제외 예정");

    // 빈 상태 메시지 + 샘플 리스트 없음
    expect(screen.getByTestId("rule-dry-run-empty")).toHaveTextContent(
      "적용 대상 기사 없음",
    );
    expect(screen.queryByTestId("rule-dry-run-sample-list")).not.toBeInTheDocument();
  });

  it("wouldAutoExclude > 0 이면 건수 + 샘플 리스트를 렌더한다", () => {
    render(
      <RuleDryRunPreview
        result={makeResult({
          analyzedCount: 200,
          wouldAutoExclude: 17,
          wouldStayUnchanged: 183,
          samples: [
            {
              summaryId: "sum-a",
              title: "MegaCorp 2분기 실적 발표",
              eventType: "EARNINGS",
              score: 0.18,
              reason: "event_type_blacklist",
            },
            {
              summaryId: "sum-b",
              title: "ConglomerateCo 소규모 제품 출시",
              eventType: "OTHER",
              score: 0.05,
              reason: "zero_signal",
            },
          ],
        })}
      />,
    );

    // 상단 강조 건수
    expect(screen.getByTestId("rule-dry-run-would-exclude")).toHaveTextContent("17");
    // 전체 분석 건수
    expect(screen.getByTestId("rule-dry-run-preview")).toHaveTextContent(
      "지난 30일 200건 중",
    );

    // 샘플 카드 2개 — empty 는 안 보여야 함
    expect(screen.queryByTestId("rule-dry-run-empty")).not.toBeInTheDocument();
    const samples = screen.getAllByTestId("rule-dry-run-sample");
    expect(samples).toHaveLength(2);
    expect(samples[0]).toHaveTextContent("MegaCorp 2분기 실적 발표");
    expect(samples[1]).toHaveTextContent("ConglomerateCo 소규모 제품 출시");
  });

  it("reason 식별자를 한국어 라벨로 변환해서 보여준다", () => {
    render(
      <RuleDryRunPreview
        result={makeResult({
          analyzedCount: 10,
          wouldAutoExclude: 2,
          wouldStayUnchanged: 8,
          samples: [
            {
              summaryId: "sum-1",
              title: "샘플 A",
              eventType: "FUNDING",
              score: 0.3,
              reason: "event_type_blacklist",
            },
            {
              summaryId: "sum-2",
              title: "샘플 B",
              eventType: null,
              score: 0.1,
              reason: "zero_signal",
            },
          ],
        })}
      />,
    );

    const samples = screen.getAllByTestId("rule-dry-run-sample");
    // 첫 샘플은 event_type_blacklist → "이벤트 타입 차단"
    expect(samples[0]).toHaveTextContent("이벤트 타입 차단");
    expect(samples[0]).toHaveTextContent("중요도 0.30");
    expect(samples[0]).toHaveTextContent("FUNDING");

    // 두 번째 샘플은 zero_signal → 한국어 라벨
    expect(samples[1]).toHaveTextContent("시그널 없음");
    expect(samples[1]).toHaveTextContent("중요도 0.10");
    // eventType 이 null 이면 " · null" 같은 기술 문구가 표시되지 않아야 한다
    expect(samples[1]).not.toHaveTextContent("null");
  });
});
