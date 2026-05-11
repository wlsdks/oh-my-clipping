import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import type { UserClippingRequest } from "@/types/user";
import { formatKoreanDateTime } from "@/utils/date";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { formatUserRequestNote } from "@/shared/lib/requestLabels";

const STATUS_LABEL: Record<UserClippingRequest["status"], string> = {
  PENDING: "검토 대기",
  APPROVED: "사용 중",
  REJECTED: "반려",
  WITHDRAWN: "철회"
};

function statusVariant(status: UserClippingRequest["status"]): "default" | "secondary" | "destructive" | "outline" {
  if (status === "APPROVED") return "default";
  if (status === "REJECTED") return "destructive";
  if (status === "WITHDRAWN") return "outline";
  return "secondary";
}

type HistoryStatusFilter = "all" | "pending" | "rejected" | "withdrawn" | "completed";

const HISTORY_FILTER_CHIPS: { value: HistoryStatusFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "pending", label: "승인대기" },
  { value: "rejected", label: "반려" },
  { value: "withdrawn", label: "철회" },
  { value: "completed", label: "완료" }
];

function filterHistoryRequests(requests: UserClippingRequest[], filter: HistoryStatusFilter): UserClippingRequest[] {
  switch (filter) {
    case "all":
      return requests;
    case "pending":
      return requests.filter((r) => r.status === "PENDING");
    case "rejected":
      return requests.filter((r) => r.status === "REJECTED");
    case "withdrawn":
      return requests.filter((r) => r.status === "WITHDRAWN");
    case "completed":
      return requests.filter((r) => r.status === "APPROVED");
  }
}

export function HistoryTab({ requests }: { requests: UserClippingRequest[] }) {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<HistoryStatusFilter>("all");
  const [withdrawTargetId, setWithdrawTargetId] = useState<string | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);

  const { mutate: withdrawRequest, isPending: isWithdrawing } = useMutation({
    mutationFn: (id: string) => userService.withdrawClippingRequest(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      toast.success("신청을 철회했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "철회하지 못했어요"))
  });

  const { mutate: deleteRequest, isPending: isDeleting } = useMutation({
    mutationFn: (id: string) => userService.deleteClippingRequest(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      toast.success("신청을 삭제했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제하지 못했어요"))
  });

  const filteredRequests = filterHistoryRequests(requests, statusFilter);

  if (requests.length === 0) {
    return <EmptyState title="신청 내역이 없어요" description="구독 신청을 하면 여기에 내역이 표시돼요" />;
  }

  return (
    <div className="space-y-3">
      {/* 필터 칩 */}
      <div className="flex flex-wrap gap-2">
        {HISTORY_FILTER_CHIPS.map((chip) => (
          <button
            key={chip.value}
            type="button"
            onClick={() => setStatusFilter(chip.value)}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              statusFilter === chip.value
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80"
            }`}
          >
            {chip.label}
          </button>
        ))}
      </div>

      {/* 완료 탭 안내 문구 */}
      {statusFilter === "completed" && (
        <p className="text-xs text-muted-foreground bg-muted/50 rounded-lg px-3 py-2">
          승인 완료된 구독은 내 구독 관리에서 상세 확인할 수 있어요
        </p>
      )}

      {filteredRequests.length === 0 ? (
        <EmptyState title="해당하는 신청 내역이 없어요" description="다른 필터를 선택해 보세요" />
      ) : (
        <div className="rounded-md border">
          <div className="divide-y">
            {filteredRequests.map((req) => (
              <div key={req.id} className="p-4 flex items-start justify-between gap-3">
                <div className="space-y-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="font-medium truncate">{req.requestName}</p>
                    <Badge variant={statusVariant(req.status)}>{STATUS_LABEL[req.status]}</Badge>
                  </div>
                  <p className="text-xs text-muted-foreground truncate">{req.sourceName}</p>
                  {formatUserRequestNote(req.reviewNote) && (
                    <p className="text-xs text-muted-foreground italic">{formatUserRequestNote(req.reviewNote)}</p>
                  )}
                  <p className="text-xs text-muted-foreground">{formatKoreanDateTime(req.createdAt)}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {req.status === "PENDING" && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setWithdrawTargetId(req.id)}
                      disabled={isWithdrawing}
                    >
                      철회
                    </Button>
                  )}
                  {(req.status === "REJECTED" || req.status === "WITHDRAWN") && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      onClick={() => setDeleteTargetId(req.id)}
                      disabled={isDeleting}
                    >
                      삭제
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 철회 확인 */}
      <ConfirmModal
        open={withdrawTargetId !== null}
        onOpenChange={(open) => { if (!open) setWithdrawTargetId(null); }}
        title="신청을 철회할까요?"
        description="철회한 신청은 다시 신청할 수 있어요."
        confirmLabel="철회"
        variant="destructive"
        onConfirm={() => {
          if (withdrawTargetId) { withdrawRequest(withdrawTargetId); setWithdrawTargetId(null); }
        }}
      />

      {/* 삭제 확인 */}
      <ConfirmModal
        open={deleteTargetId !== null}
        onOpenChange={(open) => { if (!open) setDeleteTargetId(null); }}
        title="신청을 삭제할까요?"
        description="삭제한 신청은 되돌릴 수 없어요."
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          if (deleteTargetId) { deleteRequest(deleteTargetId); setDeleteTargetId(null); }
        }}
      />
    </div>
  );
}
