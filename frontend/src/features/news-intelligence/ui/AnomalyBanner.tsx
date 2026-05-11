import { useState } from "react";
import type { KeywordTrendItem } from "../../../shared/types/admin";

/* ── 타입 ── */

export interface Anomaly {
  type: "keyword_surge" | "keyword_drop" | "article_surge";
  message: string;
  keyword?: string;
}

/* ── 이상 감지 로직 ── */

const MAX_ALERTS = 5;
const SURGE_THRESHOLD = 2.0;
const DROP_THRESHOLD = -0.5;

/**
 * 키워드 트렌드와 기사량 변동률로부터 이상 항목을 감지한다.
 * - changeRate > 2.0 -> keyword_surge
 * - changeRate < -0.5 -> keyword_drop
 * - 최대 5건까지만 반환
 */
export function detectAnomalies(
  keywords: KeywordTrendItem[],
): Anomaly[] {
  const alerts: Anomaly[] = [];

  for (const kw of keywords) {
    if (alerts.length >= MAX_ALERTS) break;

    if (kw.changeRate > SURGE_THRESHOLD) {
      alerts.push({
        type: "keyword_surge",
        message: `"${kw.keyword}" \uD0A4\uC6CC\uB4DC \uBA58\uC158\uC774 \uC804\uC8FC \uB300\uBE44 ${Math.round(kw.changeRate * 100)}% \uAE09\uC99D\uD588\uC5B4\uC694`,
        keyword: kw.keyword,
      });
    } else if (kw.changeRate < DROP_THRESHOLD) {
      alerts.push({
        type: "keyword_drop",
        message: `"${kw.keyword}" \uD0A4\uC6CC\uB4DC\uAC00 \uC804\uC8FC \uB300\uBE44 ${Math.abs(Math.round(kw.changeRate * 100))}% \uAC10\uC18C\uD588\uC5B4\uC694`,
        keyword: kw.keyword,
      });
    }
  }

  return alerts;
}

/* ── 컴포넌트 ── */

interface AnomalyBannerProps {
  anomalies: Anomaly[];
  onAlertClick?: (anomaly: Anomaly) => void;
}

const VISIBLE_LIMIT = 3;

export function AnomalyBanner({ anomalies, onAlertClick }: AnomalyBannerProps) {
  const [expanded, setExpanded] = useState(false);

  if (anomalies.length === 0) return null;

  const visible = expanded ? anomalies : anomalies.slice(0, VISIBLE_LIMIT);
  const hiddenCount = anomalies.length - VISIBLE_LIMIT;

  return (
    <div className="anomaly-banner">
      {visible.map((a, i) => (
        <div
          key={i}
          className="anomaly-item"
          style={onAlertClick ? { cursor: "pointer" } : undefined}
          onClick={() => onAlertClick?.(a)}
        >
          <span className="anomaly-icon">{"\u26A0\uFE0F"}</span>
          <span>{a.message}</span>
        </div>
      ))}

      {!expanded && hiddenCount > 0 && (
        <button className="anomaly-expand" onClick={() => setExpanded(true)} aria-label={`숨겨진 이상 징후 ${hiddenCount}건 더 보기`}>
          {hiddenCount}건 더 보기
        </button>
      )}
    </div>
  );
}
