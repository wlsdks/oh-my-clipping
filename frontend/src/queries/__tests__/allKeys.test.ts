// @vitest-environment node
import { describe, it, expect } from "vitest";
import { sourceKeys } from "../sourceKeys";
import { personaKeys } from "../personaKeys";
import { ruleKeys } from "../ruleKeys";
import { dashboardKeys } from "../dashboardKeys";
import { reviewKeys } from "../reviewKeys";
import { analyticsKeys } from "../analyticsKeys";
import { pipelineKeys } from "../pipelineKeys";
import { runtimeKeys } from "../runtimeKeys";
import { userKeys } from "../userKeys";
import { newsReportKeys } from "../newsReportKeys";
import { visualCardKeys } from "../visualCardKeys";
import { costKeys } from "../costKeys";

describe("sourceKeys", () => {
  it('all is ["sources"]', () => expect(sourceKeys.all).toEqual(["sources"]));
  it("lists()", () => expect(sourceKeys.lists()).toEqual(["sources", "list"]));
  it("detail(id)", () => expect(sourceKeys.detail("x")).toEqual(["sources", "detail", "x"]));
  it("compliance(id)", () => expect(sourceKeys.compliance("x")).toEqual(["sources", "compliance", "x"]));
  it("listsByCategoryId(id)", () =>
    expect(sourceKeys.listsByCategoryId("c")).toEqual(["sources", "list", { categoryId: "c" }]));
});

describe("personaKeys", () => {
  it('all is ["personas"]', () => expect(personaKeys.all).toEqual(["personas"]));
  it("lists()", () => expect(personaKeys.lists()).toEqual(["personas", "list"]));
  it("detail(id)", () => expect(personaKeys.detail("x")).toEqual(["personas", "detail", "x"]));
  it("userLists()", () => expect(personaKeys.userLists()).toEqual(["personas", "user-list"]));
});

describe("ruleKeys", () => {
  it('all is ["rules"]', () => expect(ruleKeys.all).toEqual(["rules"]));
  it("lists()", () => expect(ruleKeys.lists()).toEqual(["rules", "list"]));
  it("detail(id)", () => expect(ruleKeys.detail("x")).toEqual(["rules", "detail", "x"]));
  it("stats()", () => expect(ruleKeys.stats()).toEqual(["rules", "stats", 7]));
  it("stats(14)", () => expect(ruleKeys.stats(14)).toEqual(["rules", "stats", 14]));
  it("excludedItems(id)", () => expect(ruleKeys.excludedItems("x")).toEqual(["rules", "excluded", "x"]));
});

describe("dashboardKeys", () => {
  it('all is ["dashboard"]', () => expect(dashboardKeys.all).toEqual(["dashboard"]));
  it("stats()", () => expect(dashboardKeys.stats()).toEqual(["dashboard", "stats"]));
  it("articles() no params", () => expect(dashboardKeys.articles()).toEqual(["dashboard", "articles"]));
  it("articles(params)", () =>
    expect(dashboardKeys.articles({ days: 7 })).toEqual(["dashboard", "articles", { days: 7 }]));
});

describe("reviewKeys", () => {
  it('all is ["review"]', () => expect(reviewKeys.all).toEqual(["review"]));
  it("queue() no params", () => expect(reviewKeys.queue()).toEqual(["review", "queue"]));
  it("queue(params)", () =>
    expect(reviewKeys.queue({ status: "pending" })).toEqual(["review", "queue", { status: "pending" }]));
  it("detail(id)", () => expect(reviewKeys.detail("x")).toEqual(["review", "detail", "x"]));
});

describe("analyticsKeys", () => {
  it('all is ["analytics"]', () => expect(analyticsKeys.all).toEqual(["analytics"]));
  it("dau(7)", () => expect(analyticsKeys.dau(7)).toEqual(["analytics", "dau", 7]));
  it("dauRange(from, to)", () =>
    expect(analyticsKeys.dauRange("2026-01-01", "2026-01-31")).toEqual(["analytics", "dau", "2026-01-01", "2026-01-31"]));
  it("funnel(30)", () => expect(analyticsKeys.funnel(30)).toEqual(["analytics", "funnel", 30]));
  it("userRequestStats()", () => expect(analyticsKeys.userRequestStats()).toEqual(["analytics", "userRequestStats"]));

  // merged from insightKeys
  it("monthlyStats(yearMonth)", () =>
    expect(analyticsKeys.monthlyStats("2026-03")).toEqual(["analytics", "monthlyStats", "2026-03", undefined]));
  it("monthlyStats(yearMonth, categoryId)", () =>
    expect(analyticsKeys.monthlyStats("2026-03", "c1")).toEqual(["analytics", "monthlyStats", "2026-03", "c1"]));
  it("dailyKpi() no params", () =>
    expect(analyticsKeys.dailyKpi()).toEqual(["analytics", "dailyKpi", undefined]));
  it("dailyKpi(params)", () =>
    expect(analyticsKeys.dailyKpi({ from: "2026-01-01" })).toEqual(["analytics", "dailyKpi", { from: "2026-01-01" }]));
  it("hotFeedback() no params", () =>
    expect(analyticsKeys.hotFeedback()).toEqual(["analytics", "hotFeedback", undefined]));
  it("hotFeedback(params)", () =>
    expect(analyticsKeys.hotFeedback({ days: 7 })).toEqual(["analytics", "hotFeedback", { days: 7 }]));
  it("qualitySummary() default", () =>
    expect(analyticsKeys.qualitySummary()).toEqual(["analytics", "qualitySummary", undefined]));
  it("qualitySummary(30)", () =>
    expect(analyticsKeys.qualitySummary(30)).toEqual(["analytics", "qualitySummary", 30]));

  // merged from costKeys
  it("costSummary() default", () =>
    expect(analyticsKeys.costSummary()).toEqual(["analytics", "costSummary", undefined]));
  it("costSummary(7)", () =>
    expect(analyticsKeys.costSummary(7)).toEqual(["analytics", "costSummary", 7]));
});

describe("pipelineKeys", () => {
  it('all is ["pipeline"]', () => expect(pipelineKeys.all).toEqual(["pipeline"]));
  it("runs()", () => expect(pipelineKeys.runs()).toEqual(["pipeline", "runs"]));
  it("runsList() no params", () =>
    expect(pipelineKeys.runsList()).toEqual(["pipeline", "runs", "list", undefined]));
  it("runsList(params)", () =>
    expect(pipelineKeys.runsList({ status: "running" })).toEqual(["pipeline", "runs", "list", { status: "running" }]));
  it("runDetail(id)", () => expect(pipelineKeys.runDetail("x")).toEqual(["pipeline", "runs", "detail", "x"]));
  it("latest(categoryId)", () => expect(pipelineKeys.latest("c1")).toEqual(["pipeline", "latest", "c1"]));
});

describe("runtimeKeys", () => {
  it('all is ["runtime"]', () => expect(runtimeKeys.all).toEqual(["runtime"]));
  it("health()", () => expect(runtimeKeys.health()).toEqual(["runtime", "health"]));
  it("locks()", () => expect(runtimeKeys.locks()).toEqual(["runtime", "locks"]));
  it("configs()", () => expect(runtimeKeys.configs()).toEqual(["runtime", "configs"]));
  it("logs() no params", () => expect(runtimeKeys.logs()).toEqual(["runtime", "logs"]));
  it("logs(params)", () =>
    expect(runtimeKeys.logs({ level: "error" })).toEqual(["runtime", "logs", { level: "error" }]));
});

describe("userKeys", () => {
  it('all is ["users"]', () => expect(userKeys.all).toEqual(["users"]));
  it("accounts() no params", () => expect(userKeys.accounts()).toEqual(["users", "accounts"]));
  it("accounts(params)", () =>
    expect(userKeys.accounts({ status: "pending" })).toEqual(["users", "accounts", { status: "pending" }]));
  it("requests() no params", () => expect(userKeys.requests()).toEqual(["users", "requests"]));
  it("clippingRequests()", () => expect(userKeys.clippingRequests()).toEqual(["users", "clipping-requests"]));
  it("subscriptionPreferences(id)", () =>
    expect(userKeys.subscriptionPreferences("r1")).toEqual(["users", "subscription-preferences", "r1"]));
  it("deliverySchedule()", () => expect(userKeys.deliverySchedule()).toEqual(["users", "delivery-schedule"]));
});

describe("costKeys", () => {
  it('all is ["costs"]', () => expect(costKeys.all).toEqual(["costs"]));
  it("overview(params)", () =>
    expect(costKeys.overview({ from: "2026-01-01", to: "2026-01-31" })).toEqual([
      "costs",
      "overview",
      { from: "2026-01-01", to: "2026-01-31" }
    ]));
  it("budget()", () => expect(costKeys.budget()).toEqual(["costs", "budget"]));
});

describe("newsReportKeys", () => {
  it('all is ["news-report"]', () => expect(newsReportKeys.all).toEqual(["news-report"]));
  it("briefing() no params", () => expect(newsReportKeys.briefing()).toEqual(["news-report", "briefing"]));
  it("briefing(params)", () =>
    expect(newsReportKeys.briefing({ categoryId: "c" })).toEqual(["news-report", "briefing", { categoryId: "c" }]));
  it("keywordTrend() no params", () => expect(newsReportKeys.keywordTrend()).toEqual(["news-report", "keyword-trend"]));
  it("keywordTrend(params)", () =>
    expect(newsReportKeys.keywordTrend({ categoryId: "c" })).toEqual([
      "news-report",
      "keyword-trend",
      { categoryId: "c" }
    ]));
  it("competitorSnapshot() no params", () =>
    expect(newsReportKeys.competitorSnapshot()).toEqual(["news-report", "competitor-snapshot"]));
  it("competitorTimeline() no params", () =>
    expect(newsReportKeys.competitorTimeline()).toEqual(["news-report", "competitor-timeline"]));
  it("competitorSov() no params", () =>
    expect(newsReportKeys.competitorSov()).toEqual(["news-report", "competitor-sov"]));
  it("topArticles() no params", () => expect(newsReportKeys.topArticles()).toEqual(["news-report", "top-articles"]));
  it("competitors()", () => expect(newsReportKeys.competitors()).toEqual(["news-report", "competitors"]));
  it("reportSettings()", () => expect(newsReportKeys.reportSettings()).toEqual(["news-report", "report-settings"]));
  it("reportReleases() no params", () =>
    expect(newsReportKeys.reportReleases()).toEqual(["news-report", "report-releases"]));
  it("reportReleases(params)", () =>
    expect(newsReportKeys.reportReleases({ categoryId: "c" })).toEqual([
      "news-report",
      "report-releases",
      { categoryId: "c" }
    ]));
});

describe("visualCardKeys", () => {
  it('all is ["visual-cards"]', () => expect(visualCardKeys.all).toEqual(["visual-cards"]));
  it("lists() no params", () => expect(visualCardKeys.lists()).toEqual(["visual-cards", "list"]));
  it("lists(params)", () =>
    expect(visualCardKeys.lists({ categoryId: "c" })).toEqual(["visual-cards", "list", { categoryId: "c" }]));
  it("detail(id)", () => expect(visualCardKeys.detail("x")).toEqual(["visual-cards", "detail", "x"]));
  it("reportReleases() no params", () =>
    expect(visualCardKeys.reportReleases()).toEqual(["visual-cards", "report-releases"]));
  it("reportReleases(params)", () =>
    expect(visualCardKeys.reportReleases({ categoryId: "c" })).toEqual([
      "visual-cards",
      "report-releases",
      { categoryId: "c" }
    ]));
});

