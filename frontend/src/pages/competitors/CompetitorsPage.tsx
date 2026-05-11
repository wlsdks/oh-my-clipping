import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Link } from "react-router-dom";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useTabSync } from "@/hooks/useTabSync";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { runtimeService } from "@/services/runtimeService";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";
import { CompetitorListTab } from "./CompetitorListTab";
import { CompetitorAnalysisTab } from "./CompetitorAnalysisTab";
import { CompetitorWeeklySettingsCard } from "./CompetitorWeeklySettingsCard";

const VALID_TABS = ["list", "analysis"] as const;

export function CompetitorsPage() {
  const [activeTab, setActiveTab] = useTabSync("list", VALID_TABS);
  const qc = useQueryClient();

  const { data: settings } = useQuery({
    queryKey: runtimeKeys.configs(),
    queryFn: () => runtimeService.getSettings(),
  });

  const { mutate: updateSettings, isPending: isSaving } = useMutation({
    mutationFn: (data: RuntimeSettingsUpdateRequest) => runtimeService.updateSettings(data),
    onSuccess: (updated) => {
      qc.setQueryData(runtimeKeys.configs(), updated);
      toast.success("설정을 저장했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "저장하지 못했어요")),
  });

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 크로스-링크 안내 배너 */}
      <div className="bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border rounded-xl p-3 text-sm">
        <strong>경쟁사</strong>는 뉴스 수집 대상 기업.{" "}
        <strong>구독 타겟 기업(고객사·파트너)</strong>은{" "}
        <Link to="/admin/organizations" className="underline hover:opacity-80">
          관심 기업
        </Link>
        에서 관리합니다.
      </div>

      <div>
        <h1 className="text-2xl font-bold">경쟁사 관리</h1>
        <p className="text-sm text-muted-foreground mt-1">
          경쟁사 뉴스 수집을 설정하고 분석 현황을 확인하세요
        </p>
      </div>

      {settings && (
        <CompetitorWeeklySettingsCard
          settings={settings}
          isSaving={isSaving}
          onSave={updateSettings}
        />
      )}

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as typeof activeTab)}>
        <TabsList>
          <TabsTrigger value="list">경쟁사 목록</TabsTrigger>
          <TabsTrigger value="analysis">분석 현황</TabsTrigger>
        </TabsList>

        <TabsContent value="list" className="mt-4">
          <CompetitorListTab />
        </TabsContent>

        <TabsContent value="analysis" className="mt-4">
          <CompetitorAnalysisTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
