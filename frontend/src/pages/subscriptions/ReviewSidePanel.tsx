import { useState } from "react";
import { ExternalLink, AlertTriangle, Lock } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/utils/cn";
import { relativeTime, formatKoreanDateTime } from "@/utils/date";
import { formatUserRequestNote } from "@/shared/lib/requestLabels";
import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import { categoryService } from "@/services/categoryService";
import { categoryKeys } from "@/queries/categoryKeys";
import { REQUEST_STATUS_LABEL } from "./model/constants";
import type { UserClippingRequest } from "@/types/user";
import { LegalReviewModal, type LegalReviewFormData } from "@/features/legal-review/LegalReviewModal";
import type { ApproveClippingRequestData } from "@/services/userService";

interface ReviewSidePanelProps {
  item: UserClippingRequest;
  onApprove: (id: string, data: ApproveClippingRequestData) => void;
  onReject: (id: string, note: string) => void;
  isWorking: boolean;
}

function statusBadgeVariant(status: UserClippingRequest["status"]) {
  switch (status) {
    case "PENDING":
      return "info";
    case "REJECTED":
      return "destructive";
    case "WITHDRAWN":
      return "secondary";
    default:
      return "default";
  }
}

const DELIVERY_STATE_LABEL: Record<string, string> = {
  PENDING_REVIEW: "검토 대기",
  REJECTED: "반려",
  WITHDRAWN: "철회",
  VERIFYING_SOURCE: "소스 확인 중",
  ACTIVE: "정상 운영",
  PAUSED: "일시정지",
  ACTION_REQUIRED: "조치 필요",
};

export function ReviewSidePanel({ item, onApprove, onReject, isWorking }: ReviewSidePanelProps) {
  const [reviewNote, setReviewNote] = useState("");
  const [legalReviewOpen, setLegalReviewOpen] = useState(false);
  const [overrideChannelId, setOverrideChannelId] = useState<string>(item.slackChannelId ?? "");
  const { formatChannel, channelMap } = useSlackChannelMap();

  const categoriesQuery = useQuery({
    queryKey: categoryKeys.list({ size: 500 }),
    queryFn: () => categoryService.getPage(new URLSearchParams({ size: "500" })),
    select: (page) => page.content,
    staleTime: 60_000,
  });

  const activeCategories = categoriesQuery.data ?? [];
  const allChannelEntries = Array.from(channelMap.entries()).map(([id, info]) => ({ id, ...info }));

  // 현재 선택된 채널이 이미 다른 구독에서 사용 중인지 확인한다.
  const occupyingCategory = activeCategories.find(
    (cat) => cat.slackChannelId === overrideChannelId && cat.isActive,
  );
  const channelOccupied = !!occupyingCategory;

  // 원래 신청 채널과 다른 채널을 선택한 경우에만 override로 전달한다.
  const effectiveOverride =
    overrideChannelId && overrideChannelId !== item.slackChannelId
      ? overrideChannelId
      : undefined;

  function handleLegalReviewConfirm(data: LegalReviewFormData) {
    onApprove(item.id, { ...data, overrideSlackChannelId: effectiveOverride });
    setLegalReviewOpen(false);
  }

  const cleanedNote = formatUserRequestNote(item.requestNote);
  const cleanedReviewNote = formatUserRequestNote(item.reviewNote);

  const isPending = item.status === "PENDING";
  const isRejected = item.status === "REJECTED";
  const isWithdrawn = item.status === "WITHDRAWN";

  // 사용자 요청은 빈 slackChannelId="" 가 "DM 으로 발송" 의도이므로 dmIfBlank=true 로 변환.
  const channelLabel = formatChannel(item.slackChannelId, { dmIfBlank: true });
  const channelEntry = channelMap.get(item.slackChannelId);
  const channelIsPrivate = channelEntry?.isPrivate ?? false;
  const showRawChannelId =
    channelLabel !== "-" &&
    channelLabel !== "Slack DM" &&
    channelLabel !== "DM (개인 메시지)" &&
    channelLabel !== item.slackChannelId;
  // DM 발송(D로 시작 OR 빈 값)은 채널 재지정 UI 불필요
  const trimmedChannelId = (item.slackChannelId ?? "").trim();
  const isDmChannel = trimmedChannelId.length === 0 || trimmedChannelId.toUpperCase().startsWith("D");

  return (
    <div className="flex flex-col gap-5">
      {/* ── 헤더 ── */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2 flex-wrap">
          <Badge variant={statusBadgeVariant(item.status)}>
            {REQUEST_STATUS_LABEL[item.status] ?? "알 수 없음"}
          </Badge>
          {item.deliveryState && item.deliveryState !== "PENDING_REVIEW" && (
            <Badge variant="outline">{DELIVERY_STATE_LABEL[item.deliveryState] ?? item.deliveryState}</Badge>
          )}
        </div>
        <h3 className="text-lg font-semibold text-foreground leading-snug">{item.requestName}</h3>
        <p className="text-sm text-muted-foreground">
          {relativeTime(item.createdAt)} 신청 · {formatKoreanDateTime(item.createdAt)}
        </p>
      </div>

      {/* 요청 메모 */}
      {cleanedNote && (
        <div className="rounded-lg bg-primary/5 border-l-3 border-primary px-4 py-3">
          <p className="text-xs font-medium text-muted-foreground mb-1">요청 메모</p>
          <p className="text-sm text-foreground whitespace-pre-wrap">{cleanedNote}</p>
        </div>
      )}

      {/* ── 소스 정보 ── */}
      <InfoSection title="소스 정보">
        <InfoRow label="이름" value={item.sourceName} />
        <InfoRow label="URL" value={item.sourceUrl} isUrl />
        <InfoRow
          label="수집 상태"
          value={
            item.collectingReady
              ? `준비 완료 (${item.readySourceCount}/${item.totalSourceCount})`
              : `확인 중 (${item.readySourceCount}/${item.totalSourceCount})`
          }
        />
        {item.representativeSourceVerificationStatus && (
          <InfoRow label="검증" value={item.representativeSourceVerificationStatus} />
        )}
      </InfoSection>

      {/* ── 발송 설정 ── */}
      <InfoSection title="발송 설정">
        <InfoRow
          label="발송 대상"
          value={channelLabel}
          extra={
            showRawChannelId && !isDmChannel ? (
              <span className="text-xs text-muted-foreground font-mono">{item.slackChannelId}</span>
            ) : null
          }
          tag={channelIsPrivate ? "비공개" : undefined}
        />
        {item.summaryStyle && <InfoRow label="스타일" value={item.summaryStyle} />}
        {item.targetAudience && <InfoRow label="독자" value={item.targetAudience} />}
      </InfoSection>

      {/* ── 요약 페르소나 ── */}
      <InfoSection title="요약 페르소나">
        <InfoRow label="이름" value={item.personaName || "-"} />
        {item.personaPrompt && (
          <div className="px-3 py-2.5 flex flex-col gap-1.5">
            <span className="text-xs text-muted-foreground">프롬프트</span>
            <p className="text-sm text-foreground whitespace-pre-wrap bg-background rounded-md px-3 py-2 border border-border">
              {item.personaPrompt}
            </p>
          </div>
        )}
      </InfoSection>

      {/* ── 반려 사유 ── */}
      {isRejected && cleanedReviewNote && (
        <div className="rounded-lg bg-destructive/5 border-l-3 border-destructive px-4 py-3">
          <p className="text-xs font-medium text-muted-foreground mb-1">반려 사유</p>
          <p className="text-sm text-foreground whitespace-pre-wrap">{cleanedReviewNote}</p>
          {item.reviewedAt && (
            <p className="text-xs text-muted-foreground mt-2">{formatKoreanDateTime(item.reviewedAt)}</p>
          )}
        </div>
      )}

      {/* ── 저작권 상태 (승인 완료 시) ── */}
      {item.status === "APPROVED" && (
        <InfoSection title="저작권 상태">
          <InfoRow label="정책" value="인용/요약만 허용" />
          <InfoRow label="원문 링크" value="포함" />
        </InfoSection>
      )}

      {/* ── 철회 안내 ── */}
      {isWithdrawn && (
        <div className="rounded-lg bg-muted px-4 py-3">
          <p className="text-sm text-muted-foreground">사용자가 직접 철회한 요청이에요.</p>
        </div>
      )}

      {/* ── 심사 영역 ── */}
      {isPending && (
        <div className="flex flex-col gap-3 pt-2 border-t">
          {/* DM 채널이 아닌 경우에만 채널 재지정 UI 표시 */}
          {!isDmChannel && (
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-foreground">
                승인 채널
              </label>
              <Select
                value={overrideChannelId}
                onValueChange={setOverrideChannelId}
                disabled={isWorking}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="채널 선택" />
                </SelectTrigger>
                <SelectContent>
                  {allChannelEntries.length === 0 && (
                    <SelectItem value={item.slackChannelId ?? ""} disabled>
                      {formatChannel(item.slackChannelId)} (채널 목록 로딩 중)
                    </SelectItem>
                  )}
                  {allChannelEntries.map((ch) => (
                    <SelectItem key={ch.id} value={ch.id}>
                      #{ch.name}
                      {ch.isPrivate ? <Lock className="inline h-3 w-3 ml-1" /> : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {channelOccupied && (
                <div className="flex items-start gap-1.5 text-xs text-[var(--status-warning-text)]">
                  <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                  <span>
                    이 채널은 이미 <strong>{occupyingCategory?.name}</strong> 구독에서 사용 중입니다.
                    승인하면 같은 채널로 발송돼요.
                  </span>
                </div>
              )}
            </div>
          )}

          <label className="text-sm font-medium text-foreground">
            심사 메모 <span className="text-muted-foreground font-normal">(반려 시 필수)</span>
          </label>
          <textarea
            className={cn(
              "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm",
              "placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring",
              "resize-none min-h-[80px]",
            )}
            placeholder="승인 시 선택, 반려 시 사유를 입력하세요..."
            value={reviewNote}
            onChange={(e) => setReviewNote(e.target.value)}
            disabled={isWorking}
          />
          <div className="flex gap-2">
            <Button
              variant="destructive"
              size="sm"
              className="flex-1"
              disabled={isWorking || reviewNote.trim().length === 0}
              onClick={() => onReject(item.id, reviewNote.trim())}
            >
              반려
            </Button>
            <Button
              size="sm"
              className="flex-1"
              disabled={isWorking}
              onClick={() => setLegalReviewOpen(true)}
            >
              승인
            </Button>
          </div>
        </div>
      )}

      {/* 법적 검토 모달 */}
      <LegalReviewModal
        mode="approve-request"
        open={legalReviewOpen}
        onClose={() => setLegalReviewOpen(false)}
        request={{
          id: item.id,
          sourceName: item.sourceName,
          sourceUrl: item.sourceUrl,
          requesterEmail: item.requesterUserId ?? "사용자",
          createdAt: item.createdAt,
        }}
        onConfirm={handleLegalReviewConfirm}
        isPending={isWorking}
      />
    </div>
  );
}

// ── 하위 컴포넌트 ──

function InfoSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <h4 className="text-sm font-medium text-foreground">{title}</h4>
      <div className="rounded-lg border border-border bg-muted/30 divide-y divide-border">{children}</div>
    </div>
  );
}

interface InfoRowProps {
  label: string;
  value: string;
  isUrl?: boolean;
  extra?: React.ReactNode;
  tag?: string;
}

function InfoRow({ label, value, isUrl, extra, tag }: InfoRowProps) {
  return (
    <div className="flex items-start gap-3 px-3 py-2.5">
      <span className="text-xs text-muted-foreground w-14 shrink-0 pt-0.5">{label}</span>
      <div className="flex-1 min-w-0 flex flex-col gap-1">
        <div className="flex items-center gap-2 flex-wrap">
          {isUrl ? (
            <a
              href={value}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-primary hover:underline break-all inline-flex items-center gap-1"
            >
              {value}
              <ExternalLink className="h-3 w-3 shrink-0" aria-hidden="true" />
            </a>
          ) : (
            <span className="text-sm text-foreground break-all">{value}</span>
          )}
          {tag && (
            <span className="text-xs text-muted-foreground border border-border rounded-full px-2 py-0.5">
              {tag}
            </span>
          )}
        </div>
        {extra}
      </div>
    </div>
  );
}
