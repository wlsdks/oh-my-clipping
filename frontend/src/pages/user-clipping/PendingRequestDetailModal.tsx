import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { formatKoreanDateTime } from "@/utils/date";
import { TruncatedText } from "@/components/shared/TruncatedText";
import type { UserClippingRequest } from "@/types/user";

function hourLabel(h: number): string {
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

function channelLabel(channelId: string): string {
  if (!channelId) return "미설정";
  if (channelId.startsWith("D") || channelId.toUpperCase() === "DM") return "Slack DM (개인 메시지)";
  return `#${channelId}`;
}

interface Props {
  open: boolean;
  request: UserClippingRequest | null;
  deliveryHour?: number | null;
  onClose: () => void;
}

export function PendingRequestDetailModal({ open, request, deliveryHour, onClose }: Props) {
  if (!request) return null;

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-[480px] max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>구독 신청 상세</DialogTitle>
          <DialogDescription className="sr-only">구독 신청 상세 정보</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* 상태 */}
          <div className="flex items-center gap-2">
            <Badge variant="secondary">검토 대기</Badge>
            <span className="text-xs text-muted-foreground">{formatKoreanDateTime(request.createdAt)} 신청</span>
          </div>

          {/* 주제명 */}
          <div className="space-y-1">
            <p className="text-xs text-muted-foreground">주제</p>
            <TruncatedText as="p" lines={2} className="text-sm font-medium">
              {request.requestName}
            </TruncatedText>
          </div>

          {/* 소스 */}
          <div className="space-y-1">
            <p className="text-xs text-muted-foreground">뉴스 소스</p>
            <p className="text-sm">{request.sourceName}</p>
          </div>

          {/* 요약 스타일 */}
          <div className="space-y-1">
            <p className="text-xs text-muted-foreground">요약 스타일</p>
            <p className="text-sm">{request.personaName}</p>
            {request.summaryStyle && <p className="text-xs text-muted-foreground">{request.summaryStyle}</p>}
          </div>

          {/* 대상 독자 */}
          {request.targetAudience && (
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">대상 독자</p>
              <p className="text-sm">{request.targetAudience}</p>
            </div>
          )}

          {/* 발송 채널 */}
          <div className="space-y-1">
            <p className="text-xs text-muted-foreground">발송 채널</p>
            <p className="text-sm">{channelLabel(request.slackChannelId)}</p>
          </div>

          {/* 발송 시간 */}
          {deliveryHour != null && (
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">발송 시간</p>
              <p className="text-sm">{hourLabel(deliveryHour)}</p>
              <p className="text-xs text-muted-foreground">
                채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
              </p>
            </div>
          )}

          {/* 안내 */}
          <div className="rounded-lg bg-muted/30 p-3">
            <p className="text-xs text-muted-foreground">
              관리자가 신청을 검토하고 있어요. 승인되면 설정한 시간에 뉴스 발송이 시작됩니다.
            </p>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
