import type { TopArticleItem, KeywordTrendItem, SovResponse } from "../../../shared/types/admin";

/* ── 헬퍼 함수 ── */

/** 전/후 기간 대비 변화율(%)을 반환한다. previous가 0이면 null. */
export function calcChangePercent(current: number, previous: number): number | null {
  if (previous === 0) return null;
  return Math.round(((current - previous) / previous) * 100);
}

/** sentiment가 null인 기사를 제외하고 긍/중/부 카운트 및 비율을 집계한다. */
export function aggregateSentiment(articles: TopArticleItem[]) {
  let positive = 0;
  let neutral = 0;
  let negative = 0;

  for (const a of articles) {
    if (a.sentiment === "POSITIVE") positive++;
    else if (a.sentiment === "NEUTRAL") neutral++;
    else if (a.sentiment === "NEGATIVE") negative++;
    // null은 무시
  }

  const total = positive + neutral + negative;
  return {
    positive,
    neutral,
    negative,
    total,
    positiveRate: total > 0 ? (positive / total) * 100 : 0,
    neutralRate: total > 0 ? (neutral / total) * 100 : 0,
    negativeRate: total > 0 ? (negative / total) * 100 : 0,
  };
}

/** articles를 days 기간의 중간 지점 기준으로 prev/curr로 분리한다. */
export function splitPeriod(
  articles: TopArticleItem[],
  days: number,
): { prev: TopArticleItem[]; curr: TopArticleItem[] } {
  const now = Date.now();
  const msPerDay = 86_400_000;
  // 중간 지점: 전체 기간의 절반
  const midpoint = now - (days / 2) * msPerDay;

  const prev: TopArticleItem[] = [];
  const curr: TopArticleItem[] = [];

  for (const a of articles) {
    const t = new Date(a.createdAt).getTime();
    if (t < midpoint) {
      prev.push(a);
    } else {
      curr.push(a);
    }
  }

  return { prev, curr };
}

/* ── 서브 컴포넌트 ── */

function ChangeIndicator({ value }: { value: number | null }) {
  if (value === null) {
    return <span className="metric-card-change muted">&mdash;</span>;
  }
  if (Math.abs(value) < 5) {
    return <span className="metric-card-change muted">비슷</span>;
  }
  if (value > 0) {
    return <span className="metric-card-change up">&#9650; {value}%</span>;
  }
  return <span className="metric-card-change down">&#9660; {Math.abs(value)}%</span>;
}

function SkeletonCards() {
  return (
    <>
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="panel metric-card">
          <div className="skeleton w-3/5 h-3.5 rounded-md" />
          <div className="skeleton w-2/5 h-6 rounded-md mt-1" />
          <div className="skeleton w-1/2 h-3.5 rounded-md mt-1" />
        </div>
      ))}
    </>
  );
}

/* ── 메인 컴포넌트 ── */

interface MetricCardsProps {
  articles: TopArticleItem[];
  keywords: KeywordTrendItem[];
  sovData: SovResponse | null;
  days: number;
  loading?: boolean;
}

export function MetricCards({ articles, keywords, sovData, days, loading }: MetricCardsProps) {
  if (loading) {
    return (
      <div className="panel-grid panel-grid-4">
        <SkeletonCards />
      </div>
    );
  }

  // 기간 분할로 전/후 비교
  const { prev, curr } = splitPeriod(articles, days);
  const articleChange = calcChangePercent(curr.length, prev.length);

  // 논조 비율
  const sentiment = aggregateSentiment(articles);

  // 핵심 키워드 상위 2개
  const topKeywords = keywords
    .slice()
    .sort((a, b) => b.totalCount - a.totalCount)
    .slice(0, 2);

  return (
    <div className="panel-grid panel-grid-4">
      {/* 1. 총 기사 */}
      <div className="panel metric-card">
        <span className="metric-card-label">총 기사</span>
        <span className="metric-card-value">{curr.length}</span>
        <ChangeIndicator value={articleChange} />
      </div>

      {/* 2. 핵심 키워드 */}
      <div className="panel metric-card">
        <span className="metric-card-label">핵심 키워드</span>
        {topKeywords.length > 0 ? (
          <div className="flex flex-col gap-0.5">
            {topKeywords.map((kw) => (
              <div key={kw.keyword} className="flex items-center gap-1.5">
                <span className="font-semibold text-[15px]">{kw.keyword}</span>
                <span
                  className={`metric-card-change ${kw.changeRate > 0 ? "up" : kw.changeRate < 0 ? "down" : "muted"}`}
                >
                  {kw.changeRate > 0 ? "\u25B2" : kw.changeRate < 0 ? "\u25BC" : ""}
                  {Math.abs(Math.round(kw.changeRate * 100))}%
                </span>
              </div>
            ))}
          </div>
        ) : (
          <span className="metric-card-value text-[15px] text-muted-foreground">
            &mdash;
          </span>
        )}
      </div>

      {/* 3. 논조 비율 */}
      <div className="panel metric-card">
        <span className="metric-card-label">논조 비율</span>
        <span className="metric-card-value">
          {sentiment.total > 0 ? `${Math.round(sentiment.positiveRate)}%` : "\u2014"}
        </span>
        {sentiment.total > 0 && (
          <>
            <span className="metric-card-change muted">
              긍 {sentiment.positive} \u00B7 중 {sentiment.neutral} \u00B7 부 {sentiment.negative}
            </span>
            <div className="sentiment-mini-bar">
              {sentiment.positiveRate > 0 && (
                <div className="bg-[var(--status-success-text)]" style={{ flex: sentiment.positiveRate }} />
              )}
              {sentiment.neutralRate > 0 && (
                <div className="bg-muted-foreground" style={{ flex: sentiment.neutralRate }} />
              )}
              {sentiment.negativeRate > 0 && (
                <div className="bg-destructive" style={{ flex: sentiment.negativeRate }} />
              )}
            </div>
          </>
        )}
      </div>

      {/* 4. 경쟁사 멘션 */}
      <div className="panel metric-card">
        <span className="metric-card-label">경쟁사 멘션</span>
        <span className="metric-card-value">{sovData ? sovData.totalArticles : "\u2014"}</span>
        {sovData && sovData.shares.length > 0 && (
          <span className="metric-card-change muted">
            {sovData.shares
              .slice(0, 3)
              .map((s) => s.name)
              .join(" \u00B7 ")}
          </span>
        )}
      </div>
    </div>
  );
}
