import { useState } from "react";
import { ExternalLink, ChevronDown, ChevronUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { toFriendlyReason } from "./model/reasonMapper";
import { getWeightedActionButtons } from "./model/constants";
import type { ReviewQueueItem } from "@/types/review";

const SUGGESTION_BADGE: Record<string, { label: string; className: string }> = {
  INCLUDE: { label: "AI 추천: 보내기", className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]" },
  REVIEW: { label: "AI 추천: 확인 필요", className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]" },
  EXCLUDE: { label: "AI 추천: 건너뛰기", className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]" }
};

interface ReviewDetailContentProps {
  item: ReviewQueueItem;
  isPending: boolean;
  onAction: (summaryId: string, action: "approve" | "exclude" | "review") => void;
  /** 이 기사가 발송될 Slack 채널 표시 레이블 (예: "#경제뉴스", "DM (개인 메시지)") */
  channelLabel?: string;
}

export function ReviewDetailContent({ item, isPending, onAction, channelLabel }: ReviewDetailContentProps) {
  const [showDetail, setShowDetail] = useState(false);
  const reason = toFriendlyReason(item.statusReason);
  const badge = SUGGESTION_BADGE[item.suggestedStatus] ?? SUGGESTION_BADGE.REVIEW;
  const buttons = getWeightedActionButtons(item.currentStatus, item.suggestedStatus);

  return (
    <div className="space-y-5">
      {/* 검토 사유 */}
      <div className="rounded-lg bg-muted p-4 space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">검토 사유</h4>
        <p className="text-sm font-medium text-foreground">{reason.friendly}</p>
        {reason.detail !== reason.friendly && (
          <button
            type="button"
            onClick={() => setShowDetail(!showDetail)}
            className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            상세 보기
            {showDetail ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
          </button>
        )}
        {showDetail && (
          <p className="text-xs text-muted-foreground font-mono bg-background rounded px-2 py-1">
            {reason.detail}
          </p>
        )}
      </div>

      {/* AI 추천 뱃지 */}
      <div>
        <span className={`inline-flex items-center text-xs font-medium px-2.5 py-1 rounded-full ${badge.className}`}>
          {badge.label}
        </span>
      </div>

      {/* 요약 */}
      <div className="space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">요약</h4>
        <p className="text-sm text-foreground leading-relaxed whitespace-pre-line">
          {item.summary?.replace(/<[^>]*>/g, "") || "요약 정보가 없어요"}
        </p>
      </div>

      {/* 키워드 */}
      {item.keywords.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-medium text-muted-foreground">키워드</h4>
          <div className="flex flex-wrap gap-1.5">
            {item.keywords.slice(0, 8).map((kw) => (
              <span
                key={kw}
                className="text-xs px-2 py-0.5 rounded-full bg-muted text-muted-foreground"
              >
                #{kw}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 원문 링크 */}
      {item.sourceLink && (
        <a
          href={item.sourceLink}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-sm text-primary hover:underline"
        >
          원문 보기
          <ExternalLink size={14} />
        </a>
      )}

      {/* 액션 버튼 */}
      <div className="flex flex-col gap-2 pt-2 border-t">
        {channelLabel && (
          <p className="text-xs text-muted-foreground">
            발송 대상: <span className="font-medium text-foreground">{channelLabel}</span>
          </p>
        )}
        <div className="flex items-center gap-2">
          {buttons.map((btn) => (
            <Button
              key={btn.action}
              size="sm"
              variant={btn.variant}
              className="h-9 text-sm px-4"
              disabled={isPending}
              title={btn.title}
              onClick={() => onAction(item.summaryId, btn.action)}
            >
              {btn.label}
            </Button>
          ))}
        </div>
      </div>
    </div>
  );
}
