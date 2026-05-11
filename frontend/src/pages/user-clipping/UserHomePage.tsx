import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { userClippingKeys } from "@/queries/userClippingKeys";
import { userService } from "@/services/userService";
import { userHistoryService } from "@/services/userHistoryService";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import type { UserMonthlyStatRow } from "@/types/insight";
import type { BriefingItem, CompetitorSnapshotItem } from "@/types/newsReport";
import { Button } from "@/components/ui/button";
import { QuickSetupWizard } from "@/features/quick-setup/QuickSetupWizard";
import { formatKoreanDate } from "@/shared/lib/dateTime";
import { formatSlackDestinationLabel } from "@/shared/lib/slackChannel";
import { getPeriodDays } from "@/utils/periodOptions";

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function formatCompetitorDate(iso: string): string {
  try {
    const d = new Date(iso);
    return `${d.getMonth() + 1}/${d.getDate()}`;
  } catch {
    return iso.slice(5, 10);
  }
}

const ONBOARDING_STEPS = [
  { step: 1, label: "키워드 입력", desc: "관심 있는 뉴스 주제를 알려주세요" },
  { step: 2, label: "요약 방식 선택", desc: "읽기 편한 스타일을 골라주세요" },
  { step: 3, label: "자동 발송", desc: "매일 Slack으로 요약이 도착해요" }
];

export function UserHomePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [wizardOpen, setWizardOpen] = useState(false);
  const [selectedBriefingCategory, setSelectedBriefingCategory] = useState<string>("all");

  const yearMonth = currentYearMonth();

  const { data: requests = [], isLoading } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });

  const activeRequests = requests.filter((r) => r.status === "APPROVED");
  const pendingRequests = requests.filter((r) => r.status === "PENDING");
  const rejectedRequests = requests.filter((r) => r.status === "REJECTED");
  const isAtLimit = activeRequests.length + pendingRequests.length >= 5;

  const { data: stats = [] } = useQuery<UserMonthlyStatRow[]>({
    queryKey: userHistoryKeys.monthlyStats(yearMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(yearMonth),
    enabled: activeRequests.length > 0
  });

  const { data: briefingData } = useQuery({
    queryKey: userClippingKeys.briefingToday(),
    queryFn: () => userIntelligenceService.getBriefing(),
    enabled: activeRequests.length > 0
  });

  const { data: competitorSnapshotData } = useQuery({
    queryKey: userClippingKeys.competitorSnapshot({ days: getPeriodDays("this-week"), limit: 3 }),
    queryFn: () => userIntelligenceService.getCompetitorSnapshot({ days: getPeriodDays("this-week"), limit: 3 }),
    enabled: activeRequests.length > 0
  });

  const competitorItems: CompetitorSnapshotItem[] = competitorSnapshotData?.items ?? [];
  const briefings: BriefingItem[] = briefingData?.briefings ?? [];

  // 오늘 발행 건수
  const today = new Date().toISOString().slice(0, 10);
  const todayTotal = stats.filter((r) => r.statDate === today).reduce((s, r) => s + r.itemsSent, 0);

  const totalSent = stats.reduce((sum, s) => sum + s.itemsSent, 0);
  const totalCollected = stats.reduce((sum, s) => sum + s.itemsCollected, 0);

  // 브리핑 카테고리 필터
  const briefingCategories = Array.from(new Map(briefings.map((b) => [b.categoryId, b.categoryName])));
  const filteredBriefings =
    selectedBriefingCategory === "all" ? briefings : briefings.filter((b) => b.categoryId === selectedBriefingCategory);

  function handleWizardComplete() {
    setWizardOpen(false);
    qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
  }

  if (isLoading) {
    return (
      <div className="p-8 space-y-3 animate-pulse">
        <div className="h-4 bg-muted rounded w-1/3" />
        <div className="h-4 bg-muted rounded w-2/3" />
        <div className="h-4 bg-muted rounded w-1/2" />
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs text-muted-foreground">Clipping</p>
          <h1 className="text-xl sm:text-2xl font-bold">홈</h1>
          <p className="text-sm text-muted-foreground mt-1">내 뉴스 현황을 한눈에 확인하세요</p>
        </div>
        {requests.length > 0 && (
          <Button onClick={() => isAtLimit ? toast("구독 한도(5개)에 도달했어요. 기존 구독을 해제한 후 다시 시도해 주세요.") : setWizardOpen(true)}>
            + 새 주제
          </Button>
        )}
      </div>

      {/* 빈 상태 — 환영 히어로 */}
      {requests.length === 0 && (
        <div className="rounded-2xl border bg-card p-8 space-y-6">
          <div className="space-y-2">
            <h2 className="text-xl font-bold">구독할 토픽을 추가해 보세요</h2>
            <p className="text-muted-foreground">
              키워드를 입력하면 관련 뉴스를 자동으로 수집하고 요약해서 Slack으로 보내드려요.
            </p>
          </div>
          <ol className="flex flex-col gap-4 sm:flex-row sm:gap-8">
            {ONBOARDING_STEPS.map(({ step, label, desc }) => (
              <li key={step} className="flex items-start gap-3">
                <span className="flex-shrink-0 w-6 h-6 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-xs font-bold">
                  {step}
                </span>
                <div>
                  <p className="text-sm font-semibold">{label}</p>
                  <p className="text-xs text-muted-foreground">{desc}</p>
                </div>
              </li>
            ))}
          </ol>
          <Button size="lg" onClick={() => setWizardOpen(true)}>
            시작하기
          </Button>
        </div>
      )}

      {/* 활성 구독이 있는 경우 대시보드 */}
      {requests.length > 0 && (
        <>
          {/* 오늘 인사 */}
          <p className="text-xl font-bold">
            {todayTotal > 0 ? (
              <>
                오늘 뉴스 <span className="text-primary">{todayTotal}건</span>이 도착했어요
              </>
            ) : totalSent > 0 ? (
              <>
                이번 달 뉴스 <span className="text-primary">{totalSent}건</span>을 받았어요
              </>
            ) : (
              "구독을 설정하면 매일 뉴스가 도착해요"
            )}
          </p>

          {/* 이번 달 요약 — 가로 카드 */}
          {activeRequests.length > 0 && (
            <div className="rounded-xl border bg-card">
              <div className="grid grid-cols-3 divide-x">
                <div className="p-3 sm:p-5 text-center">
                  <p className="text-lg sm:text-2xl font-bold">{totalSent.toLocaleString()}</p>
                  <p className="text-[10px] sm:text-xs text-muted-foreground mt-1">이번 달 받은 뉴스</p>
                </div>
                <div className="p-3 sm:p-5 text-center">
                  <p className="text-lg sm:text-2xl font-bold">{totalCollected.toLocaleString()}</p>
                  <p className="text-[10px] sm:text-xs text-muted-foreground mt-1">AI가 수집한 기사</p>
                </div>
                <div className="p-3 sm:p-5 text-center">
                  <p className="text-lg sm:text-2xl font-bold">{activeRequests.length}</p>
                  <p className="text-[10px] sm:text-xs text-muted-foreground mt-1">구독 중인 주제</p>
                </div>
              </div>
            </div>
          )}

          {/* 오늘의 뉴스 브리핑 */}
          {briefings.length > 0 && (
            <div className="rounded-xl border bg-card p-5 space-y-4">
              <h2 className="text-sm font-semibold">오늘의 뉴스 브리핑</h2>

              {/* 카테고리 필터 탭 */}
              <div className="flex gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={() => setSelectedBriefingCategory("all")}
                  className={`px-3.5 py-1.5 rounded-full text-sm transition-colors ${
                    selectedBriefingCategory === "all"
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-muted/80"
                  }`}
                >
                  전체
                </button>
                {briefingCategories.map(([id, name]) => (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setSelectedBriefingCategory(id)}
                    className={`px-3.5 py-1.5 rounded-full text-sm transition-colors ${
                      selectedBriefingCategory === id
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground hover:bg-muted/80"
                    }`}
                  >
                    {name}
                  </button>
                ))}
              </div>

              {/* 전체 탭: compact 카테고리 리스트 */}
              {selectedBriefingCategory === "all" && (
                <div className="space-y-1.5">
                  {filteredBriefings.map((item) => (
                    <button
                      key={`${item.categoryId}-${item.summaryDate}`}
                      type="button"
                      onClick={() => setSelectedBriefingCategory(item.categoryId)}
                      className="w-full flex items-center gap-3 bg-muted/50 hover:bg-muted rounded-lg px-4 py-3 text-left transition-colors"
                    >
                      <strong className="text-sm font-semibold flex-shrink-0">{item.categoryName}</strong>
                      <span className="text-xs font-semibold text-primary flex-shrink-0">{item.totalItems}건</span>
                      <div className="flex-1 flex flex-wrap gap-1 min-w-0">
                        {item.topicKeywords.slice(0, 4).map((kw) => (
                          <span
                            key={kw}
                            className="text-xs bg-background rounded-full px-2 py-0.5 text-muted-foreground"
                          >
                            {kw}
                          </span>
                        ))}
                      </div>
                      <span className="text-xs text-muted-foreground flex-shrink-0">→</span>
                    </button>
                  ))}
                </div>
              )}

              {/* 개별 카테고리 탭: 풀 브리핑 */}
              {selectedBriefingCategory !== "all" && (
                <div className="space-y-3">
                  {filteredBriefings.map((item) => (
                    <div
                      key={`${item.categoryId}-${item.summaryDate}`}
                      className="bg-muted/50 rounded-xl p-4 space-y-2"
                    >
                      <div className="flex items-center gap-2">
                        <strong className="text-sm">{item.categoryName}</strong>
                        <span className="text-xs text-muted-foreground">{item.totalItems}건</span>
                      </div>
                      <p className="text-sm leading-relaxed text-foreground/80">{item.overallSummary}</p>
                      {item.topicKeywords.length > 0 && (
                        <div className="flex flex-wrap gap-1">
                          {item.topicKeywords.map((kw) => (
                            <span
                              key={kw}
                              className="text-xs bg-background rounded-full px-2 py-0.5 text-muted-foreground"
                            >
                              {kw}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* 경쟁사 동향 */}
          {competitorItems.length > 0 && (
            <div className="rounded-xl border bg-card p-5 space-y-3">
              <h2 className="text-sm font-semibold">경쟁사 동향</h2>
              <div className="space-y-1.5">
                {competitorItems.map((item) => (
                  <a
                    key={item.summaryId}
                    href={item.sourceLink}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2.5 bg-muted/50 hover:bg-muted rounded-lg px-3 py-2.5 transition-colors no-underline text-foreground"
                  >
                    <span className="text-xs text-muted-foreground w-8 flex-shrink-0">
                      {formatCompetitorDate(item.createdAt)}
                    </span>
                    <span className="text-xs font-semibold bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] rounded-full px-2 py-0.5 flex-shrink-0">
                      {item.competitorName}
                    </span>
                    <span className="text-sm flex-1 truncate">{item.title}</span>
                  </a>
                ))}
              </div>
              <Link
                to="/user/articles"
                className="block text-center text-sm text-primary font-medium bg-primary/5 hover:bg-primary/10 rounded-lg py-2.5 transition-colors"
              >
                경쟁사 뉴스에서 더 보기 →
              </Link>
            </div>
          )}

          {/* 검토 중 / 반려 알림 */}
          {(pendingRequests.length > 0 || rejectedRequests.length > 0) && (
            <div className="space-y-2">
              {pendingRequests.map((req) => (
                <div key={req.id} className="flex items-center gap-3 rounded-xl border bg-card px-4 py-3">
                  <span className="w-2 h-2 rounded-full bg-[var(--status-warning-text)] flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold truncate">{req.requestName}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatSlackDestinationLabel(req.slackChannelId, {
                        blankLabel: "Slack DM",
                        genericChannelLabel: "Slack 채널"
                      })}{" "}
                      · {formatKoreanDate(req.createdAt)} 신청
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => navigate("/user/history")}
                    className="text-xs text-primary font-medium flex-shrink-0 hover:underline"
                  >
                    상세
                  </button>
                </div>
              ))}
              {rejectedRequests.map((req) => (
                <div key={req.id} className="flex items-center gap-3 rounded-xl border bg-card px-4 py-3">
                  <span className="w-2 h-2 rounded-full bg-[var(--status-danger-text)] flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold truncate">{req.requestName}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatSlackDestinationLabel(req.slackChannelId, {
                        blankLabel: "Slack DM",
                        genericChannelLabel: "Slack 채널"
                      })}{" "}
                      · {formatKoreanDate(req.createdAt)} 신청
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => navigate("/user/history")}
                    className="text-xs text-primary font-medium flex-shrink-0 hover:underline"
                  >
                    사유 보기
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* 구독 0건: 공개 주제 즉시 구독 유도 */}
          {activeRequests.length === 0 && (
            <div className="rounded-xl border border-primary/20 bg-primary/5 p-6 text-center space-y-3">
              <h2 className="text-lg font-semibold">아직 구독 중인 주제가 없어요</h2>
              <p className="text-sm text-muted-foreground">
                관리자가 준비한 주제를 바로 구독하거나, 직접 만들어 보세요.
              </p>
              <div className="flex justify-center gap-3">
                <Button onClick={() => navigate("/user/browse")}>구독 가능한 주제 보기</Button>
                <Button variant="outline" onClick={() => setWizardOpen(true)}>직접 만들기</Button>
              </div>
            </div>
          )}
        </>
      )}

      <QuickSetupWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onComplete={handleWizardComplete}
        isUserMode
      />
    </div>
  );
}
