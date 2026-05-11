import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userService } from "@/services/userService";
import { userClippingKeys } from "@/queries/userClippingKeys";
import { organizationKeys } from "@/queries/organizationKeys";
import { localizeReason } from "@/utils/localizeReason";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { SubmitWithEntriesRequest } from "@/types/adminDto";
import type { QuickSetupForm } from "./model/quickSetupTypes";

/**
 * 위자드 통합 submit — 단일 POST /api/user/requests/with-entries 호출.
 * partial / rejected 응답은 toast 로 안내, submitted 시 queries 무효화.
 */
export function useQuickSetupSubmit() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (form: QuickSetupForm) => {
      const body: SubmitWithEntriesRequest = {
        categoryName: form.categoryName,
        entries: form.entries.map((e) => ({
          value: e.value,
          type: e.type,
          stockCode: e.type === "company" ? e.stockCode : undefined,
        })),
        description: form.categoryDescription || undefined,
        // 발송 스케줄은 `user_delivery_schedules` 에 저장되어야 스케줄러가 찾을 수 있다.
        // 프리셋과 시각은 위자드에서 항상 선택되므로 함께 전달한다. (#487 회귀 수정)
        deliveryPreset: form.deliveryPreset,
        deliveryDays: form.deliveryDays,
        deliveryHour: form.deliveryHour,
      };
      return userService.createRequestWithEntries(body);
    },
    onSuccess: (res) => {
      if (res.status === "rejected") {
        const visible = res.errors
          .slice(0, 3)
          .map((e) => `${e.value}: ${localizeReason(e.reason)}`)
          .join("\n");
        toast.error("요청이 반려되었어요", {
          description: visible || "입력값을 확인해주세요",
        });
        return;
      }
      if (res.status === "partial") {
        const visible = res.errors
          .slice(0, 3)
          .map((e) => `${e.value}: ${localizeReason(e.reason)}`)
          .join("\n");
        const extra =
          res.errors.length > 3 ? `\n외 ${res.errors.length - 3}건` : "";
        toast.warning(`${res.errors.length}개 항목이 제외되었어요`, {
          description: `${visible}${extra}`,
        });
      }
      queryClient.invalidateQueries({ queryKey: userClippingKeys.all });
      queryClient.invalidateQueries({ queryKey: organizationKeys.all });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "요청 전송에 실패했어요"));
    },
  });

  return {
    submit: mutation.mutateAsync,
    isPending: mutation.isPending,
  };
}
