import type { ReactNode } from "react";

interface MetricCardProps {
  /** 카드 상단 레이블 (예: "총 기사", "DAU") */
  label: string;
  /** 주요 수치 값 */
  value: ReactNode;
  /** 부가 설명 텍스트 (작은 글씨) */
  subtext?: string;
  /** 증감 표시 */
  trend?: { direction: "up" | "down"; label: string };
  /** 좌측 상단 아이콘 */
  icon?: ReactNode;
  /** 강조 스타일 (좌측 보더) */
  variant?: "default" | "highlight";
  /** 최하단 부가 메타 정보 */
  meta?: string;
  /** 클릭 가능 여부 — 핸들러 전달 시 호버 효과 활성화 */
  onClick?: () => void;
}

/**
 * 공통 메트릭 카드 컴포넌트.
 *
 * 분석 대시보드 등에서 핵심 수치를 보여주는 모던 미니멀 카드.
 * 기존 DashboardMetricsCards나 뉴스 인텔리전스 MetricCards와는 독립적으로 사용된다.
 */
export function MetricCard({
  label,
  value,
  subtext,
  trend,
  icon,
  variant = "default",
  meta,
  onClick
}: MetricCardProps) {
  const classNames = ["smc", variant === "highlight" ? "smc--highlight" : "", onClick ? "smc--clickable" : ""]
    .filter(Boolean)
    .join(" ");

  return (
    <article
      className={classNames}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={
        onClick
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
    >
      {icon ? <span className="smc-icon">{icon}</span> : null}
      <span className="smc-label">{label}</span>
      <span className="smc-value">{value}</span>
      {subtext ? <span className="smc-sub">{subtext}</span> : null}
      {trend ? (
        <span className={`smc-trend smc-trend--${trend.direction}`}>
          {trend.direction === "up" ? "+" : ""}
          {trend.label}
        </span>
      ) : null}
      {meta ? <span className="smc-meta">{meta}</span> : null}
    </article>
  );
}
