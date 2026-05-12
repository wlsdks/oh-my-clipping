import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DailyKpiTable } from "../DailyKpiTable";
import { ChannelCostTable } from "@/features/cost/ui/ChannelCostTable";
import { ArticleRankingList } from "@/features/engagement/ui/ArticleRankingList";
import type { DailyOperationalKpiRow } from "@/types/insight";
import type { LlmCostRow } from "@/types/cost";
import type { ArticleRankItem } from "@/services/analyticsService";

describe("DailyKpiTable", () => {
  const sampleRow: DailyOperationalKpiRow = {
    statDate: "2026-03-15",
    itemsCollected: 120,
    excludedCount: 10,
    itemsDuplicates: 5,
    noiseRate: 0.083,
    duplicateRate: 0.042,
    reviewLeadTimeHours: 2.5,
    llmEstimatedCostUsd: 0.0012,
    sendAttempts: 100,
    sendSuccesses: 98,
    sendSuccessRate: 0.98,
  };

  it("행 데이터를 렌더링해야 한다", () => {
    render(<DailyKpiTable rows={[sampleRow]} />);
    expect(screen.getByText("2026-03-15")).toBeInTheDocument();
    expect(screen.getByText("120")).toBeInTheDocument();
    expect(screen.getByText("8.3%")).toBeInTheDocument();
    expect(screen.getByText("4.2%")).toBeInTheDocument();
    expect(screen.getByText("2.5h")).toBeInTheDocument();
    expect(screen.getByText("98.0%")).toBeInTheDocument();
  });

  it("빈 배열이면 빈 상태를 표시해야 한다", () => {
    render(<DailyKpiTable rows={[]} />);
    expect(screen.getByText("일 단위 KPI 데이터가 없어요")).toBeInTheDocument();
  });

  it("loading이면 스켈레톤을 표시해야 한다", () => {
    const { container } = render(<DailyKpiTable rows={[]} loading />);
    expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
  });
});

describe("ChannelCostTable", () => {
  const sampleRow: LlmCostRow = {
    channelId: "ch-1",
    categoryId: "cat-1",
    categoryName: "기술",
    requestCount: 50,
    tokensIn: 10000,
    tokensOut: 5000,
    estimatedUsd: 0.015,
  };

  it("행 데이터를 렌더링해야 한다", () => {
    render(<ChannelCostTable rows={[sampleRow]} />);
    expect(screen.getByText("ch-1")).toBeInTheDocument();
    expect(screen.getByText("기술")).toBeInTheDocument();
    expect(screen.getByText("50")).toBeInTheDocument();
    expect(screen.getByText("10,000")).toBeInTheDocument();
    expect(screen.getByText("5,000")).toBeInTheDocument();
    expect(screen.getByText("$0.0150")).toBeInTheDocument();
  });

  it("빈 배열이면 빈 상태를 표시해야 한다", () => {
    render(<ChannelCostTable rows={[]} />);
    expect(screen.getByText("비용 데이터가 없어요")).toBeInTheDocument();
  });
});

describe("ArticleRankingList", () => {
  const sampleItem: ArticleRankItem = {
    rank: 1,
    summaryId: "s-1",
    title: "테스트 기사 제목",
    categoryName: "기술",
    sourceName: "테크뉴스",
    publishedAt: "2026-03-15T09:00:00",
    clicks: 42,
    impressions: 200,
    ctr: 21.0,
    bookmarks: 5,
  };

  it("기사 항목을 렌더링해야 한다", () => {
    render(<ArticleRankingList items={[sampleItem]} />);
    expect(screen.getByText("테스트 기사 제목")).toBeInTheDocument();
    expect(screen.getByText("기술")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("클릭 42")).toBeInTheDocument();
    expect(screen.getByText("클릭률 21.0%")).toBeInTheDocument();
    expect(screen.getByText("북마크 5")).toBeInTheDocument();
  });

  it("빈 배열이면 빈 상태를 표시해야 한다", () => {
    render(<ArticleRankingList items={[]} />);
    expect(screen.getByText("표시할 기사 데이터가 없어요")).toBeInTheDocument();
  });

  it("loading이면 스켈레톤을 표시해야 한다", () => {
    const { container } = render(<ArticleRankingList items={[]} loading />);
    expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
  });

  it("title이 null이면 '(제목 없음)'을 표시해야 한다", () => {
    const noTitleItem = { ...sampleItem, title: null };
    render(<ArticleRankingList items={[noTitleItem]} />);
    expect(screen.getByText("(제목 없음)")).toBeInTheDocument();
  });
});
