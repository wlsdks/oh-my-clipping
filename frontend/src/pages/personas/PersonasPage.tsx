import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useTabSync } from "@/hooks/useTabSync";
import { PresetManagementTab } from "./PresetManagementTab";
import { StyleStatsTab } from "./StyleStatsTab";

const VALID_TABS = ["preset", "stats"] as const;

export function PersonasPage() {
  const [activeTab, setActiveTab] = useTabSync("preset", VALID_TABS);

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <div>
        <h1 className="text-2xl font-bold">요약 스타일</h1>
        <p className="text-sm text-muted-foreground mt-1">AI가 뉴스를 요약하는 말투와 형식을 설정하세요</p>
      </div>

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as typeof activeTab)}>
        <TabsList>
          <TabsTrigger value="preset">템플릿 관리</TabsTrigger>
          <TabsTrigger value="stats">사용 통계</TabsTrigger>
        </TabsList>

        <TabsContent value="preset" className="mt-4">
          <PresetManagementTab />
        </TabsContent>

        <TabsContent value="stats" className="mt-4">
          <StyleStatsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
