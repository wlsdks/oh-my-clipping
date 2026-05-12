import type { HotFeedbackResult } from "@/types/insight";
import { formatKoreanDateTime } from "@/utils/date";

interface HotFeedbackListProps {
  result?: HotFeedbackResult;
  loading?: boolean;
}

export function HotFeedbackList({ result, loading }: HotFeedbackListProps) {
  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="animate-pulse rounded-lg border border-border h-16 bg-muted/30"
          />
        ))}
      </div>
    );
  }

  if (!result || result.items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-8">
        피드백 데이터가 없어요
      </p>
    );
  }

  const periodText = `${formatKoreanDateTime(result.from)} ~ ${formatKoreanDateTime(result.to)} · 후보 ${result.totalCandidates}건`;

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">{periodText}</p>
      <ul className="space-y-3">
        {result.items.map((item) => (
          <li
            key={item.summaryId}
            className="rounded-lg border border-border p-3 space-y-1"
          >
            <a
              href={item.sourceLink}
              target="_blank"
              rel="noreferrer"
              className="text-sm font-medium hover:underline line-clamp-2"
            >
              {item.title}
            </a>
            <p className="text-xs text-muted-foreground">
              좋아요 {item.likeCount} · 보통 {item.neutralCount} · 싫어요{" "}
              {item.dislikeCount} · 점수 {item.score.toFixed(2)}
            </p>
          </li>
        ))}
      </ul>
    </div>
  );
}
