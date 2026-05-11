import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PipelineNode } from "../PipelineNode";
import { formatDuration } from "../pipelineUtils";

describe("PipelineNode", () => {
  it("IDLE 상태에서 라벨과 '대기 중'을 표시한다", () => {
    render(<PipelineNode step="COLLECT" status="IDLE" />);

    expect(screen.getByText("수집")).toBeInTheDocument();
    expect(screen.getByText("대기 중")).toBeInTheDocument();
    expect(screen.queryByTestId("pipeline-node-metrics")).not.toBeInTheDocument();
    expect(screen.queryByTestId("pipeline-node-duration")).not.toBeInTheDocument();
  });

  it("RUNNING 상태에서 '실행 중'을 표시한다", () => {
    render(<PipelineNode step="SUMMARIZE" status="RUNNING" />);

    expect(screen.getByText("요약")).toBeInTheDocument();
    expect(screen.getByText("실행 중")).toBeInTheDocument();
  });

  it("SUCCEEDED 상태에서 메트릭과 소요 시간을 표시한다", () => {
    render(
      <PipelineNode
        step="DIGEST"
        status="SUCCEEDED"
        metrics={{ itemsProcessed: 12, itemsSkipped: 3 }}
        durationMs={1250}
      />,
    );

    expect(screen.getByText("다이제스트")).toBeInTheDocument();
    expect(screen.getByText("완료")).toBeInTheDocument();
    expect(screen.getByTestId("pipeline-node-metrics")).toHaveTextContent("12건 처리 · 3건 건너뛰기");
    expect(screen.getByTestId("pipeline-node-duration")).toHaveTextContent("1.3초");
  });

  it("FAILED 상태에서 '실패'를 표시한다", () => {
    render(<PipelineNode step="COLLECT" status="FAILED" />);

    expect(screen.getByText("실패")).toBeInTheDocument();
  });

  it("SKIPPED 상태에서 '건너뜀'을 표시한다", () => {
    render(<PipelineNode step="SUMMARIZE" status="SKIPPED" />);

    expect(screen.getByText("건너뜀")).toBeInTheDocument();
  });
});

describe("formatDuration", () => {
  it("1초 미만은 ms 단위로 표시한다", () => {
    expect(formatDuration(300)).toBe("300ms");
    expect(formatDuration(0)).toBe("0ms");
    expect(formatDuration(999)).toBe("999ms");
  });

  it("1초 이상은 초 단위로 표시한다", () => {
    expect(formatDuration(1000)).toBe("1.0초");
    expect(formatDuration(1250)).toBe("1.3초");
    expect(formatDuration(62400)).toBe("62.4초");
  });
});
