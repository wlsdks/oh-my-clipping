import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { personaAnalyticsKeys } from "@/queries/personaAnalyticsKeys";

interface BackfillButtonProps {
  weeks?: number;
}

/**
 * 과거 주간 스냅샷을 소급 집계하는 버튼.
 *
 * 완료 후 persona-analytics 쿼리 전체를 무효화하여
 * WeeklyTrendsChart 를 포함한 모든 섹션이 갱신된다.
 */
export function BackfillButton({ weeks = 12 }: BackfillButtonProps) {
  const qc = useQueryClient();

  const mutation = useMutation({
    mutationFn: (w: number) => personaAnalyticsService.runBackfill(w),
    onSuccess: (result) => {
      toast.success(
        `과거 데이터 집계 완료 — ${result.weeksProcessed}주, ${result.snapshotRowsCreated}건`,
      );
      qc.invalidateQueries({ queryKey: personaAnalyticsKeys.all });
    },
    onError: (err) =>
      toast.error(userFriendlyMessage(err, "과거 데이터 집계에 실패했어요. 잠시 후 다시 시도해 주세요")),
  });

  return (
    <Button
      variant="outline"
      size="sm"
      disabled={mutation.isPending}
      onClick={() => mutation.mutate(weeks)}
    >
      {mutation.isPending ? "집계 중…" : `지난 ${weeks}주 데이터 집계`}
    </Button>
  );
}
