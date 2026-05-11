import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { cn } from "@/utils/cn";
import type { RuleDryRunResult } from "@/types/categoryRule";

interface RuleDryRunPreviewProps {
  result: RuleDryRunResult;
}

/**
 * dry-run 에서 사용하는 `reason` 식별자 → 운영자에게 보여줄 한국어 라벨.
 *
 * 백엔드 `ReviewPolicyRuleEvaluator` 상수(`event_type_blacklist`/`zero_signal`) 와 1:1.
 * 알 수 없는 값은 그대로 노출해서 운영자가 감지할 수 있게 한다.
 */
const REASON_LABELS: Record<string, string> = {
  event_type_blacklist: "이벤트 타입 차단",
  zero_signal: "시그널 없음 (OTHER + NEUTRAL + 키워드 미매칭)",
};

function toReasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason;
}

/**
 * 카테고리 룰 dry-run 시뮬레이션 결과를 보여주는 미리보기 카드.
 *
 * - `analyzedCount` / `wouldAutoExclude` 요약 텍스트를 상단에 노출
 * - `wouldAutoExclude > 0` 이면 경고 톤(warning), 0 이면 성공 톤(success)
 * - `samples` 를 최대 5개까지 리스트로 노출 (서버가 이미 상한을 두지만 UI 에서도 안전하게 clamp)
 * - 건수 0 일 때는 "적용 대상 기사 없음" 을 표시해서 운영자가 아무 일도 일어나지 않을 것임을 확인
 */
export function RuleDryRunPreview({ result }: RuleDryRunPreviewProps) {
  const { analyzedCount, wouldAutoExclude, samples } = result;
  // 상한을 한 번 더 적용 — 서버가 50개 넘게 주는 경우에도 UI 가 폭주하지 않도록
  const displaySamples = samples.slice(0, 5);
  const hasImpact = wouldAutoExclude > 0;

  return (
    <section
      aria-label="룰 미리보기 결과"
      data-testid="rule-dry-run-preview"
      className={cn(
        "space-y-2 rounded-xl p-3",
        hasImpact
          ? "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
          : "bg-muted text-muted-foreground",
      )}
    >
      <header className="flex items-start gap-2">
        {hasImpact ? (
          <AlertTriangle
            className="mt-0.5 h-4 w-4 shrink-0"
            aria-hidden="true"
          />
        ) : (
          <CheckCircle2
            className="mt-0.5 h-4 w-4 shrink-0"
            aria-hidden="true"
          />
        )}
        <p className="text-sm font-medium">
          지난 30일 {analyzedCount.toLocaleString("ko-KR")}건 중{" "}
          <span
            className="tabular-nums"
            data-testid="rule-dry-run-would-exclude"
          >
            {wouldAutoExclude.toLocaleString("ko-KR")}
          </span>
          건 자동 제외 예정
        </p>
      </header>

      {!hasImpact ? (
        <p
          className="text-xs"
          data-testid="rule-dry-run-empty"
        >
          적용 대상 기사 없음
        </p>
      ) : (
        <ul
          className="space-y-1.5"
          data-testid="rule-dry-run-sample-list"
        >
          {displaySamples.map((sample) => (
            <li
              key={sample.summaryId}
              data-testid="rule-dry-run-sample"
              className="rounded-lg bg-background/60 px-2.5 py-1.5 text-xs text-foreground"
            >
              <p className="font-medium line-clamp-2">{sample.title}</p>
              <p className="mt-0.5 text-muted-foreground tabular-nums">
                {toReasonLabel(sample.reason)} · 중요도 {sample.score.toFixed(2)}
                {sample.eventType ? ` · ${sample.eventType}` : ""}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
