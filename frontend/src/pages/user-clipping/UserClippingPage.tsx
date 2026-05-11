import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DeliveryScheduleSettings } from "./DeliveryScheduleSettings";
import { UserNewsIntelligenceTab } from "./UserNewsIntelligenceTab";
import { QuickSetupWizard } from "@/features/quick-setup/QuickSetupWizard";
import { SubscriptionsTab } from "./SubscriptionsTab";
import { HistoryTab } from "./HistoryTab";
import { NewsHistoryTab } from "./NewsHistoryTab";
import { CompetitorNewsTab } from "./CompetitorNewsTab";
import { ReportTab } from "./ReportTab";

type NewsHistorySubtab = "my-news" | "competitor-news";

function NewsHistoryContainer({ userCategories }: { userCategories: { id: string; name: string }[] }) {
  const [subtab, setSubtab] = useState<NewsHistorySubtab>("my-news");

  return (
    <div className="space-y-4">
      <div className="flex border-b">
        <button
          type="button"
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
            subtab === "my-news"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
          onClick={() => setSubtab("my-news")}
        >
          구독 뉴스
        </button>
        <button
          type="button"
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
            subtab === "competitor-news"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
          onClick={() => setSubtab("competitor-news")}
        >
          경쟁사 뉴스
        </button>
      </div>
      {subtab === "my-news" && <NewsHistoryTab userCategories={userCategories} />}
      {subtab === "competitor-news" && <CompetitorNewsTab />}
    </div>
  );
}

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function getDefaultTab(pathname: string): string {
  if (pathname.includes("/articles")) return "news-history";
  if (pathname.includes("/news-report")) return "report";
  if (pathname.includes("/history")) return "history";
  return "subscriptions";
}

export function UserClippingPage() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [wizardOpen, setWizardOpen] = useState(() => pathname.includes("/request"));

  const { data: requests = [], isLoading } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });

  if (isLoading) {
    return <div className="p-8 text-center text-sm text-muted-foreground">불러오는 중...</div>;
  }

  const activeRequests = requests.filter((r) => r.status === "APPROVED");
  const historyRequests = [...requests].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
  const approvedCategoryIds = activeRequests.map((r) => r.approvedCategoryId).filter((id): id is string => Boolean(id));
  const approvedCategories = activeRequests
    .filter((r) => r.approvedCategoryId && r.approvedCategoryName)
    .map((r) => ({ id: r.approvedCategoryId!, name: r.approvedCategoryName! }));

  function handleWizardClose() {
    setWizardOpen(false);
    if (pathname.includes("/request")) {
      navigate("/user", { replace: true });
    }
  }

  function handleWizardComplete() {
    setWizardOpen(false);
    qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
    navigate("/user", { replace: true });
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <div>
        <h1 className="text-2xl font-bold">내 클리핑</h1>
        <p className="text-sm text-muted-foreground mt-1">구독 중인 채널과 발송 설정을 관리하세요</p>
      </div>

      <Tabs defaultValue={getDefaultTab(pathname)}>
        <TabsList>
          <TabsTrigger value="subscriptions">내 구독 ({activeRequests.length})</TabsTrigger>
          <TabsTrigger value="schedule">발송 설정</TabsTrigger>
          <TabsTrigger value="news-history">뉴스 수신 이력</TabsTrigger>
          <TabsTrigger value="intelligence">키워드 분석</TabsTrigger>
          <TabsTrigger value="report">리포트</TabsTrigger>
          <TabsTrigger value="history">신청 내역 ({requests.length})</TabsTrigger>
        </TabsList>

        <TabsContent value="subscriptions" className="mt-4">
          <SubscriptionsTab requests={requests} onOpenWizard={() => setWizardOpen(true)} />
        </TabsContent>

        <TabsContent value="schedule" className="mt-4 max-w-lg">
          <DeliveryScheduleSettings />
        </TabsContent>

        <TabsContent value="news-history" className="mt-4">
          <NewsHistoryContainer userCategories={approvedCategories} />
        </TabsContent>

        <TabsContent value="intelligence" className="mt-4">
          <UserNewsIntelligenceTab yearMonth={currentYearMonth()} approvedCategoryIds={approvedCategoryIds} />
        </TabsContent>

        <TabsContent value="report" className="mt-4">
          <ReportTab approvedCategoryIds={approvedCategoryIds} />
        </TabsContent>

        <TabsContent value="history" className="mt-4">
          <HistoryTab requests={historyRequests} />
        </TabsContent>
      </Tabs>

      <QuickSetupWizard open={wizardOpen} onClose={handleWizardClose} onComplete={handleWizardComplete} isUserMode />
    </div>
  );
}
