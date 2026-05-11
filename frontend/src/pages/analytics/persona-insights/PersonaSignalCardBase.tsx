import type { ReactNode } from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

export interface PersonaSignalCardBaseProps {
  /** Tone drives the pill colour. Use `risk` for danger/warning, `growth` for primary. */
  tone: "risk" | "growth";
  icon: ReactNode;
  personaName: string;
  isPreset: boolean;
  /** Korean label from `RISK_LABELS` or `GROWTH_LABELS`. */
  typeLabel: string;
  tooltip: string;
  persistentWeeks: number;
  /** Main "prev → current (±Δ)" line (large, tabular-nums). */
  primaryLine: ReactNode;
  /** Optional raw-numbers / context line (muted). */
  secondaryLine?: ReactNode;
  /** Call-to-action buttons, rendered in a trailing flex row. */
  ctas: ReactNode;
  /** Optional cross-reference note (e.g. "이 페르소나는 성장 신호도 함께 가집니다"). */
  crossReference?: string;
}

/**
 * Shared minimal card primitive for persona risk / growth signals.
 *
 * Spec: §5.1 파일 구조 — `AtRiskPersonaCard` / `GrowthPersonaCard` 가 primitive 를 공유하며,
 * 시각 차이는 tone 과 icon (AlertTriangle vs TrendingUp) 만으로 구분한다 (§5.2).
 */
export function PersonaSignalCardBase(props: PersonaSignalCardBaseProps) {
  const {
    tone,
    icon,
    personaName,
    isPreset,
    typeLabel,
    tooltip,
    persistentWeeks,
    primaryLine,
    secondaryLine,
    ctas,
    crossReference,
  } = props;

  const pillClass =
    tone === "risk"
      ? "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
      : "bg-[var(--status-success-bg)] text-[var(--status-success-text)]";

  const iconWrapClass =
    tone === "risk"
      ? "text-[var(--status-danger-text)]"
      : "text-[var(--status-success-text)]";

  const persistentLabel =
    persistentWeeks === 1 ? "NEW" : `${persistentWeeks}주차`;

  const persistentTooltip =
    persistentWeeks === 1
      ? "이번 주에 처음 이 신호가 감지됐어요."
      : `최근 ${persistentWeeks}주 연속 이 신호가 지속되고 있어요.`;

  return (
    <article className="rounded-2xl border bg-card p-5 space-y-3 transition-all hover:-translate-y-px hover:shadow-sm">
      <header className="flex flex-wrap items-center gap-2">
        <span aria-hidden className={iconWrapClass}>
          {icon}
        </span>
        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger
              type="button"
              className={
                "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring " +
                pillClass
              }
              aria-label={`${typeLabel} 판정 기준 보기`}
            >
              {typeLabel}
            </TooltipTrigger>
            <TooltipContent
              side="top"
              className="max-w-xs text-xs leading-relaxed"
            >
              {tooltip}
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>

        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger
              type="button"
              className="inline-flex items-center rounded-full bg-[var(--status-neutral-bg)] px-2 py-0.5 text-[11px] font-semibold text-[var(--status-neutral-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="지속 주차 설명"
            >
              {persistentLabel}
            </TooltipTrigger>
            <TooltipContent side="top" className="max-w-xs text-xs leading-relaxed">
              {persistentTooltip}
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>

        <h3 className="text-base font-semibold text-foreground truncate">
          {personaName}
        </h3>

        <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
          {isPreset ? "템플릿" : "커스텀"}
        </span>
      </header>

      <p className="text-lg font-semibold tabular-nums text-foreground">
        {primaryLine}
      </p>

      {secondaryLine && (
        <p className="text-sm text-muted-foreground tabular-nums">
          {secondaryLine}
        </p>
      )}

      {crossReference && (
        <p className="text-xs text-muted-foreground">{crossReference}</p>
      )}

      <div className="flex flex-wrap items-center gap-2 pt-1">{ctas}</div>
    </article>
  );
}
