import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { userService } from "@/services/userService";
import { userHistoryService } from "@/services/userHistoryService";
import type { UserClippingRequest, UserSubscriptionPreference, DeliverySchedule } from "@/types/user";
import {
  requestDeliveryStatusLabel,
  requestDeliveryHint,
  requestDeliveryTone,
  compareApprovedUserRequests
} from "@/shared/lib/userRequestDelivery";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { formatSlackDestinationLabel } from "@/shared/lib/slackChannel";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { CardTitle } from "@/components/shared/CardTitle";
import { SubscriptionEditModal } from "./SubscriptionEditModal";
import { QuickSetupWizard } from "@/features/quick-setup/QuickSetupWizard";

interface EditModalState {
  request: UserClippingRequest;
  preference: UserSubscriptionPreference | null;
}

interface UnsubscribeConfirmState {
  requestId: string;
  requestName: string;
  isChannelSubscription: boolean;
}

function deliveryTimeText(schedule: DeliverySchedule): string {
  const DAY_MAP: Record<string, string> = {
    MON: "월",
    TUE: "화",
    WED: "수",
    THU: "목",
    FRI: "금",
    SAT: "토",
    SUN: "일"
  };
  const { preset, deliveryDays, deliveryHour } = schedule;
  const dayPart =
    preset === "WEEKDAYS"
      ? "평일"
      : preset === "EVERYDAY"
        ? "매일"
        : deliveryDays.map((d) => DAY_MAP[d] ?? d).join(",");
  const h = deliveryHour;
  const timePart = h < 12 ? `오전 ${h}시` : h === 12 ? "오후 12시" : `오후 ${h - 12}시`;
  return `${dayPart} ${timePart}`;
}

function detectRegionLabel(sourceUrl: string): "국내" | "해외" | null {
  if (!sourceUrl) return null;
  try {
    const lower = sourceUrl.toLowerCase();
    if (lower.includes("gl=kr") || lower.includes("hl=ko") || lower.includes("ceid=kr")) return "국내";
    if (lower.includes("gl=") || lower.includes("hl=") || lower.includes("ceid=")) return "해외";
  } catch {
    /* ignore */
  }
  return null;
}

function statusBadgeClass(tone: ReturnType<typeof requestDeliveryTone>): string {
  if (tone === "success") return "bg-[var(--status-success-bg)] text-[var(--status-success-text)]";
  if (tone === "paused") return "bg-muted text-muted-foreground";
  if (tone === "warning") return "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]";
  if (tone === "danger") return "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]";
  return "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]";
}

const MAX_SUBSCRIPTIONS = 5;

export function UserManagePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [wizardOpen, setWizardOpen] = useState(false);
  const [editModal, setEditModal] = useState<EditModalState | null>(null);
  const [loadingPrefFor, setLoadingPrefFor] = useState<string | null>(null);
  const [unsubscribeConfirm, setUnsubscribeConfirm] = useState<UnsubscribeConfirmState | null>(null);

  const yearMonth = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}`;

  const { data: requests = [], isLoading, isError, refetch } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });

  const { data: globalSchedule } = useQuery<DeliverySchedule>({
    queryKey: userKeys.deliverySchedule(),
    queryFn: () => userService.getDeliverySchedule()
  });

  const activeRequests = [...requests.filter((r) => r.status === "APPROVED")].sort(compareApprovedUserRequests);
  const pendingRequests = requests.filter((r) => r.status === "PENDING");
  const subscriptionCount = activeRequests.length + pendingRequests.length;
  const isAtLimit = subscriptionCount >= MAX_SUBSCRIPTIONS;

  const { data: monthlyStats = [] } = useQuery({
    queryKey: userHistoryKeys.monthlyStats(yearMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(yearMonth),
    enabled: activeRequests.length > 0
  });

  const { mutate: toggleActive, isPending: isToggling } = useMutation({
    mutationFn: ({ requestId, isActive }: { requestId: string; isActive: boolean }) =>
      userService.updateSubscriptionPreferences(requestId, { isActive }),
    onSuccess: (_, { isActive }) => {
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      toast.success(isActive ? "구독을 재개했어요" : "구독을 일시정지했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "변경하지 못했어요"))
  });

  const { mutate: unsubscribe, isPending: isUnsubscribing } = useMutation({
    mutationFn: (requestId: string) => userService.unsubscribeClippingRequest(requestId),
    onSuccess: () => {
      toast.success("구독이 해제됐어요");
      setUnsubscribeConfirm(null);
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "구독 해제에 실패했어요"))
  });

  async function openEditModal(request: UserClippingRequest) {
    setLoadingPrefFor(request.id);
    try {
      const pref = await userService.getSubscriptionPreferences(request.id);
      setEditModal({ request, preference: pref });
    } catch {
      setEditModal({ request, preference: null });
    } finally {
      setLoadingPrefFor(null);
    }
  }

  function handleWizardComplete() {
    setWizardOpen(false);
    qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
  }

  const timeText = globalSchedule ? deliveryTimeText(globalSchedule) : null;

  if (isLoading) {
    return (
      <div className="p-8 space-y-3 animate-pulse">
        <div className="h-4 bg-muted rounded w-1/3" />
        <div className="h-4 bg-muted rounded w-2/3" />
        <div className="h-4 bg-muted rounded w-1/2" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-8">
        <EmptyState
          title="데이터를 불러올 수 없어요"
          description="네트워크 상태를 확인하고 다시 시도해주세요"
          action={<Button variant="outline" size="sm" onClick={() => refetch()}>다시 시도</Button>}
        />
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs text-muted-foreground">Clipping</p>
          <h1 className="text-xl sm:text-2xl font-bold">내 구독 관리</h1>
          <p className="text-sm text-muted-foreground mt-1">받고 있는 뉴스 주제와 설정을 확인하고 변경할 수 있어요.</p>
        </div>
        <div className="flex items-center gap-3 flex-shrink-0 flex-wrap">
          <span className="text-xs text-muted-foreground">
            {subscriptionCount}/{MAX_SUBSCRIPTIONS}개 구독
          </span>
          <Button variant="outline" onClick={() => navigate("/user/browse")}>
            구독 가능한 주제
          </Button>
          <Button onClick={() => setWizardOpen(true)} disabled={isAtLimit}>
            {isAtLimit ? "구독 한도 도달" : "+ 새 주제"}
          </Button>
        </div>
      </div>

      {/* 활성 구독 목록 */}
      {activeRequests.length === 0 ? (
        <EmptyState
          title="활성화된 구독이 없어요"
          description="새 주제를 신청하면 관리자 승인 후 뉴스가 발송돼요"
          action={<Button onClick={() => setWizardOpen(true)}>1분 만에 시작하기</Button>}
        />
      ) : (
        <div className="space-y-3">
          {activeRequests.map((req) => {
            const isPaused = req.deliveryState === "PAUSED";
            const tone = requestDeliveryTone(req);
            const statusLabel = requestDeliveryStatusLabel(req);
            const statusHint = requestDeliveryHint(req);
            const reqStats = monthlyStats.filter((s) => s.categoryId === req.approvedCategoryId);
            const totalSent = reqStats.reduce((sum, s) => sum + s.itemsSent, 0);
            const topKeywords = [...new Set(reqStats.flatMap((s) => s.topKeywords))].slice(0, 6);
            const slackLabel = formatSlackDestinationLabel(req.slackChannelId, {
              blankLabel: "DM",
              genericChannelLabel: "채널"
            });
            const regionLabel = detectRegionLabel(req.sourceUrl);

            return (
              <div key={req.id} className="rounded-xl border bg-card p-5 space-y-3">
                {/* 제목 + 상태 뱃지 */}
                <CardTitle
                  size="sm"
                  rightSlot={
                    <span
                      className={`text-xs font-medium px-2.5 py-1 rounded-full ${statusBadgeClass(tone)}`}
                    >
                      {statusLabel}
                    </span>
                  }
                >
                  {req.requestName}
                </CardTitle>

                {/* 메타 칩스 */}
                <div className="flex flex-wrap gap-1.5">
                  <span className="text-xs bg-muted rounded-full px-2.5 py-1">{slackLabel}</span>
                  {regionLabel && <span className="text-xs bg-muted rounded-full px-2.5 py-1">{regionLabel}</span>}
                  {timeText && <span className="text-xs bg-muted rounded-full px-2.5 py-1">{timeText}</span>}
                  {totalSent > 0 && (
                    <span className="text-xs bg-primary/10 text-primary rounded-full px-2.5 py-1 font-medium">
                      {totalSent}건
                    </span>
                  )}
                </div>

                {/* 키워드 */}
                {topKeywords.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {topKeywords.map((kw) => (
                      <span key={kw} className="text-xs bg-muted rounded-full px-2 py-0.5">
                        {kw}
                      </span>
                    ))}
                  </div>
                )}

                {/* 상태 설명 */}
                {statusHint && (
                  <p
                    className={`text-xs ${tone === "warning" || tone === "danger" ? "text-destructive" : "text-muted-foreground"}`}
                  >
                    {statusHint}
                  </p>
                )}

                {/* 토글 + 변경 버튼 */}
                <div className="flex items-center gap-2 pt-1">
                  <button
                    type="button"
                    role="switch"
                    aria-checked={!isPaused}
                    aria-label={isPaused ? "구독 재개" : "일시정지"}
                    onClick={() => toggleActive({ requestId: req.id, isActive: isPaused })}
                    disabled={isToggling}
                    className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition-colors focus:outline-none disabled:opacity-50 ${
                      !isPaused ? "bg-primary" : "bg-muted"
                    }`}
                  >
                    <span
                      className={`inline-block h-4 w-4 transform rounded-full bg-background shadow transition-transform ${
                        !isPaused ? "translate-x-6" : "translate-x-1"
                      }`}
                    />
                  </button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => openEditModal(req)}
                    disabled={loadingPrefFor === req.id}
                  >
                    {loadingPrefFor === req.id ? "불러오는 중..." : "변경"}
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-muted-foreground hover:text-destructive"
                    onClick={() => setUnsubscribeConfirm({
                      requestId: req.id,
                      requestName: req.requestName,
                      isChannelSubscription: !req.slackChannelId.toUpperCase().startsWith("D") &&
                                             !req.slackChannelId.toUpperCase().startsWith("U")
                    })}
                  >
                    구독 해제
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 검토 중인 신청 섹션 */}
      {pendingRequests.length > 0 && (
        <div className="rounded-xl border border-[var(--status-warning-bg)] bg-[var(--status-warning-bg)] p-5 space-y-3">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold">검토 중인 신청</h2>
            <span className="w-5 h-5 rounded-full bg-[var(--status-warning-text)] text-white text-xs font-bold flex items-center justify-center">
              {pendingRequests.length}
            </span>
          </div>
          <div className="space-y-0 divide-y divide-[var(--status-warning-bg)]">
            {pendingRequests.map((req) => {
              const slackLabel = formatSlackDestinationLabel(req.slackChannelId, {
                blankLabel: "Slack DM",
                genericChannelLabel: "Slack 채널"
              });
              return (
                <div key={req.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold truncate">{req.requestName}</p>
                    <p className="text-xs text-muted-foreground truncate">
                      {slackLabel} · {req.sourceName}
                      {req.personaName && ` · ${req.personaName}`}
                    </p>
                  </div>
                  <span className="text-xs font-medium bg-[var(--status-warning-bg)] text-[var(--status-warning-text)] px-2.5 py-1 rounded-full flex-shrink-0">
                    검토 중
                  </span>
                </div>
              );
            })}
          </div>
          <button
            type="button"
            onClick={() => navigate("/user/history")}
            className="w-full text-center text-sm text-[var(--status-warning-text)] font-medium hover:underline pt-1"
          >
            진행 상태에서 자세히 보기 →
          </button>
        </div>
      )}

      {editModal && (
        <SubscriptionEditModal
          open={true}
          request={editModal.request}
          preference={editModal.preference}
          onClose={() => setEditModal(null)}
        />
      )}

      <QuickSetupWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onComplete={handleWizardComplete}
        isUserMode
      />

      <Dialog
        open={unsubscribeConfirm !== null}
        onOpenChange={(open) => {
          if (!open) setUnsubscribeConfirm(null);
        }}
      >
        <DialogContent className="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle>구독을 해제하시겠어요?</DialogTitle>
            <DialogDescription>
              <strong>{unsubscribeConfirm?.requestName}</strong> 구독을 해제하면 더 이상 뉴스가 발송되지 않아요.
              {unsubscribeConfirm?.isChannelSubscription
                ? " 이 채널의 모든 멤버가 클리핑을 받지 못하게 됩니다."
                : " 채널 구독의 경우 Slack에서 직접 채널을 나가셔야 해요."}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUnsubscribeConfirm(null)} disabled={isUnsubscribing}>
              취소
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (unsubscribeConfirm) unsubscribe(unsubscribeConfirm.requestId);
              }}
              disabled={isUnsubscribing}
            >
              {isUnsubscribing ? "해제 중..." : "구독 해제"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
