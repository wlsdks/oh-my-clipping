import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import type { UserClippingRequest } from "@/types/user";
import { formatKoreanDate } from "@/shared/lib/dateTime";
import { formatSlackDestinationLabel } from "@/shared/lib/slackChannel";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";

type HistoryStatusFilter = "all" | "pending" | "rejected" | "withdrawn" | "completed";

const HISTORY_FILTER_CHIPS: { value: HistoryStatusFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "pending", label: "승인대기" },
  { value: "rejected", label: "반려" },
  { value: "withdrawn", label: "철회" },
  { value: "completed", label: "완료" }
];

function filterRequests(requests: UserClippingRequest[], filter: HistoryStatusFilter): UserClippingRequest[] {
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

function countByFilter(requests: UserClippingRequest[], filter: HistoryStatusFilter): number {
  return filterRequests(requests, filter).length;
}

function statusDotColor(status: UserClippingRequest["status"]): string {
  if (status === "PENDING") return "bg-[var(--status-warning-text)]";
  if (status === "REJECTED") return "bg-[var(--status-danger-text)]";
  if (status === "APPROVED") return "bg-[var(--status-success-text)]";
  return "bg-muted-foreground";
}

function statusBadge(status: UserClippingRequest["status"]) {
  const styles: Record<UserClippingRequest["status"], string> = {
    PENDING: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]",
    APPROVED: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
    REJECTED: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]",
    WITHDRAWN: "bg-muted text-muted-foreground"
  };
  const labels: Record<UserClippingRequest["status"], string> = {
    PENDING: "검토 대기",
    APPROVED: "사용 중",
    REJECTED: "반려",
    WITHDRAWN: "철회됨"
  };
  return (
    <span className={`text-xs font-medium px-2.5 py-1 rounded-full flex-shrink-0 ${styles[status]}`}>
      {labels[status]}
    </span>
  );
}

function sanitizeReviewNote(note: string | null | undefined): string | null {
  if (!note) return null;
  const cleaned = note.replace(/\[[^\]]*\]/g, "").trim();
  return cleaned || null;
}

const DAY_LABELS: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일"
};

function formatDays(days: string[] | null): string {
  if (!days || days.length === 0) return "미설정";
  if (days.length === 7) return "매일";
  if (days.length === 5 && !days.includes("SAT") && !days.includes("SUN")) return "평일";
  return days.map((d) => DAY_LABELS[d] ?? d).join(", ");
}

function formatHour(h: number | null): string {
  if (h === null) return "미설정";
  if (h === 0) return "자정";
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

function RequestDetailDialog({
  request,
  onClose,
  onWithdraw,
  onDelete,
  isWithdrawing,
  isDeleting
}: {
  request: UserClippingRequest | null;
  onClose: () => void;
  onWithdraw: (id: string) => void;
  onDelete: (id: string) => void;
  isWithdrawing: boolean;
  isDeleting: boolean;
}) {
  const [confirmAction, setConfirmAction] = useState<"withdraw" | "delete" | null>(null);
  const { data: pref } = useQuery({
    queryKey: userKeys.subscriptionPreferences(request?.id ?? ""),
    queryFn: () => userService.getSubscriptionPreferences(request!.id),
    enabled: request !== null && request.status === "APPROVED"
  });

  const { data: globalSchedule } = useQuery({
    queryKey: userKeys.deliverySchedule(),
    queryFn: () => userService.getDeliverySchedule(),
    enabled: request !== null && request.status !== "APPROVED"
  });

  if (!request) return null;

  const sl = formatSlackDestinationLabel(request.slackChannelId, {
    blankLabel: "Slack DM",
    genericChannelLabel: "Slack 채널"
  });
  const rn = sanitizeReviewNote(request.reviewNote);

  return (
    <>
      <Dialog
        open={request !== null}
        onOpenChange={(open) => {
          if (!open) onClose();
        }}
      >
        <DialogContent className="max-w-md max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="pr-6">{request.requestName}</DialogTitle>
            <DialogDescription className="sr-only">신청 상세 내용을 확인합니다</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 text-sm">
            <div className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${statusDotColor(request.status)}`} />
              {statusBadge(request.status)}
              <span className="text-xs text-muted-foreground">{formatKoreanDate(request.createdAt)} 신청</span>
            </div>

            <div className="grid grid-cols-[5rem_1fr] gap-y-2.5 text-muted-foreground">
              <span className="font-medium text-foreground">소스</span>
              <span>{request.sourceName}</span>
              <span className="font-medium text-foreground">발송</span>
              <span>{sl}</span>
              <span className="font-medium text-foreground">요약 스타일</span>
              <span>{request.personaName}</span>
              {request.summaryStyle && (
                <>
                  <span className="font-medium text-foreground">요약 형식</span>
                  <span className="text-xs">{request.summaryStyle}</span>
                </>
              )}
              {request.targetAudience && (
                <>
                  <span className="font-medium text-foreground">대상 독자</span>
                  <span className="text-xs">{request.targetAudience}</span>
                </>
              )}
              {!pref && globalSchedule && (
                <>
                  <span className="font-medium text-foreground">발송 요일</span>
                  <span>{formatDays(globalSchedule.deliveryDays)}</span>
                  <span className="font-medium text-foreground">발송 시간</span>
                  <span>{formatHour(globalSchedule.deliveryHour)}</span>
                </>
              )}
            </div>
            {!pref && globalSchedule && (
              <p className="text-xs text-muted-foreground">
                채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
              </p>
            )}

            {/* APPROVED일 때 구독 설정 상세 */}
            {pref && (
              <div className="rounded-lg border bg-muted/30 p-3 space-y-2">
                <p className="text-xs font-semibold text-foreground">구독 설정</p>
                <div className="grid grid-cols-[5rem_1fr] gap-y-1.5 text-xs text-muted-foreground">
                  <span className="font-medium text-foreground">최대 기사</span>
                  <span>{pref.maxItems}건</span>
                  <span className="font-medium text-foreground">발송 요일</span>
                  <span>{formatDays(pref.deliveryDays)}</span>
                  <span className="font-medium text-foreground">발송 시간</span>
                  <span>{formatHour(pref.deliveryHour)}</span>
                  {pref.excludeKeywords.length > 0 && (
                    <>
                      <span className="font-medium text-foreground">제외 키워드</span>
                      <div className="flex flex-wrap gap-1">
                        {pref.excludeKeywords.map((kw) => (
                          <span
                            key={kw}
                            className="px-2 py-0.5 rounded-full bg-primary/10 text-primary text-[11px] font-medium"
                          >
                            {kw}
                          </span>
                        ))}
                      </div>
                    </>
                  )}
                </div>
                <p className="text-[11px] text-muted-foreground pt-1">
                  채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
                </p>
              </div>
            )}

            {request.status === "REJECTED" && rn && (
              <div className="p-3 rounded-lg bg-[var(--status-danger-bg)] text-[var(--status-danger-text)] text-sm">
                반려 사유: {rn}
              </div>
            )}

            <div className="flex gap-2 pt-1">
              {request.status === "PENDING" && (
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive border-destructive/30 hover:bg-destructive/10"
                  disabled={isWithdrawing}
                  onClick={() => setConfirmAction("withdraw")}
                >
                  철회하기
                </Button>
              )}
              {(request.status === "REJECTED" || request.status === "WITHDRAWN") && (
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive border-destructive/30 hover:bg-destructive/10"
                  disabled={isDeleting}
                  onClick={() => setConfirmAction("delete")}
                >
                  삭제하기
                </Button>
              )}
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 철회 확인 */}
      <ConfirmModal
        open={confirmAction === "withdraw"}
        onOpenChange={(open) => {
          if (!open) setConfirmAction(null);
        }}
        title="신청을 철회할까요?"
        description="철회한 신청은 다시 신청할 수 있어요."
        confirmLabel="철회"
        variant="destructive"
        onConfirm={() => {
          onWithdraw(request.id);
          setConfirmAction(null);
        }}
      />

      {/* 삭제 확인 */}
      <ConfirmModal
        open={confirmAction === "delete"}
        onOpenChange={(open) => {
          if (!open) setConfirmAction(null);
        }}
        title="신청을 삭제할까요?"
        description="삭제한 신청은 되돌릴 수 없어요."
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          onDelete(request.id);
          setConfirmAction(null);
        }}
      />
    </>
  );
}

export function UserStatusPage() {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<HistoryStatusFilter>("all");
  const [selectedReq, setSelectedReq] = useState<UserClippingRequest | null>(null);

  const { data: rawRequests = [], isLoading } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });

  const requests = [...rawRequests].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

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

  if (isLoading) {
    return (
      <div className="p-8 text-center text-sm text-muted-foreground" role="status" aria-live="polite">
        불러오는 중...
      </div>
    );
  }

  const filteredRequests = filterRequests(requests, statusFilter);

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">Clipping</p>
        <h1 className="text-2xl font-bold">진행 상태</h1>
        <p className="text-sm text-muted-foreground mt-1">신청한 주제가 어떤 상태인지 확인할 수 있어요.</p>
      </div>

      {requests.length === 0 ? (
        <EmptyState title="신청 내역이 없어요" description="구독 신청을 하면 여기에 내역이 표시돼요" />
      ) : (
        <div className="space-y-4">
          {/* 필터 칩 (카운트 포함) */}
          <div className="flex flex-wrap gap-2">
            {HISTORY_FILTER_CHIPS.map((chip) => {
              const count = countByFilter(requests, chip.value);
              return (
                <button
                  key={chip.value}
                  type="button"
                  onClick={() => setStatusFilter(chip.value)}
                  className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
                    statusFilter === chip.value
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-muted/80"
                  }`}
                >
                  {chip.label} {count}
                </button>
              );
            })}
          </div>

          {/* 완료 탭 안내 */}
          {statusFilter === "completed" && (
            <p className="text-xs text-muted-foreground bg-muted/50 rounded-lg px-3 py-2">
              승인 완료된 구독은 내 구독 관리에서 상세 확인할 수 있어요
            </p>
          )}

          {filteredRequests.length === 0 && statusFilter === "all" && countByFilter(requests, "completed") > 0 ? (
            <div className="rounded-xl border bg-muted/50 p-6 text-center space-y-3">
              <p className="text-sm font-medium">모든 구독이 활성 상태예요</p>
              <p className="text-xs text-muted-foreground">승인 완료된 구독은 아래 '완료' 탭에서 확인할 수 있어요</p>
              <button
                type="button"
                onClick={() => setStatusFilter("completed")}
                className="text-xs text-primary font-medium hover:underline"
              >
                완료 탭으로 이동 →
              </button>
            </div>
          ) : filteredRequests.length === 0 ? (
            <EmptyState title="해당하는 신청 내역이 없어요" description="다른 필터를 선택해 보세요" />
          ) : (
            <div className="space-y-2">
              {filteredRequests.map((req) => {
                const slackLabel = formatSlackDestinationLabel(req.slackChannelId, {
                  blankLabel: "Slack DM",
                  genericChannelLabel: "Slack 채널"
                });
                const reviewNote = sanitizeReviewNote(req.reviewNote);

                return (
                  <button
                    key={req.id}
                    type="button"
                    className="w-full rounded-xl border bg-card px-4 py-3.5 text-left transition-shadow hover:shadow-sm"
                    onClick={() => setSelectedReq(req)}
                  >
                    <div className="flex items-center gap-3">
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${statusDotColor(req.status)}`} />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold truncate">{req.requestName}</p>
                        <p className="text-xs text-muted-foreground">
                          {slackLabel} · {formatKoreanDate(req.createdAt)} 신청
                        </p>
                      </div>
                      {statusBadge(req.status)}
                    </div>
                    {req.status === "REJECTED" && reviewNote && (
                      <p className="text-xs text-[var(--status-danger-text)] pl-5 mt-1.5">반려: {reviewNote}</p>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* 상세 팝업 */}
      <RequestDetailDialog
        request={selectedReq}
        onClose={() => setSelectedReq(null)}
        onWithdraw={(id) => {
          withdrawRequest(id);
          setSelectedReq(null);
        }}
        onDelete={(id) => {
          deleteRequest(id);
          setSelectedReq(null);
        }}
        isWithdrawing={isWithdrawing}
        isDeleting={isDeleting}
      />
    </div>
  );
}
