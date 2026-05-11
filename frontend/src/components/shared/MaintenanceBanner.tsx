import { useQuery } from "@tanstack/react-query";
import { maintenanceService } from "@/services/maintenanceService";

const maintenanceKeys = {
  status: ["maintenance", "status"] as const
};

export function MaintenanceBanner() {
  const { data } = useQuery({
    queryKey: maintenanceKeys.status,
    queryFn: () => maintenanceService.getStatus(),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    staleTime: 30_000,
    retry: 1
  });

  if (!data?.active) return null;

  return (
    <div className="bg-muted text-muted-foreground dark:bg-muted dark:text-muted-foreground text-center text-sm py-2 px-4 shrink-0 border-b border-border">
      {data.message || "서비스 점검이 예정되어 있어요"}
    </div>
  );
}
