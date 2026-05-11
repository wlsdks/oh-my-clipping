import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApprovalTab } from "./ApprovalTab";
import { MembersTab } from "./MembersTab";

type TabValue = "approval" | "members";

const TAB_DESCRIPTIONS: Record<TabValue, string> = {
  approval: "가입 신청된 사용자 계정을 승인하거나 반려해요",
  members: "승인된 회원의 활동 현황을 관리해요.",
};

export function AdminUserAccountsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("tab") as TabValue) || "approval";
  const personaIdFilter = searchParams.get("personaId") ?? undefined;

  const { data: pendingAccounts = [] } = useQuery({
    queryKey: userKeys.accounts({ status: "PENDING", badge: true }),
    queryFn: () => userService.listAdminUserAccounts("PENDING"),
  });
  const pendingCount = pendingAccounts.length;

  function handleTabChange(value: string) {
    const tab = value as TabValue;
    if (tab === "approval") {
      searchParams.delete("tab");
    } else {
      searchParams.set("tab", tab);
    }
    setSearchParams(searchParams, { replace: true });
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <h1 className="text-2xl font-bold">회원 관리</h1>
      <p className="text-sm text-muted-foreground">{TAB_DESCRIPTIONS[activeTab]}</p>

      <Tabs value={activeTab} onValueChange={handleTabChange}>
        <TabsList>
          <TabsTrigger value="approval">
            가입 승인
            {pendingCount > 0 && (
              <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-destructive px-1.5 py-0.5 text-[10px] font-medium text-destructive-foreground">
                {pendingCount}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="members">회원 현황</TabsTrigger>
        </TabsList>
        <TabsContent value="approval" className="mt-4">
          <ApprovalTab />
        </TabsContent>
        <TabsContent value="members" className="mt-4">
          <MembersTab personaIdFilter={personaIdFilter} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
