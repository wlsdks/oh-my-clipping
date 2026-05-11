import { ExternalLink, AlertTriangle } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import { relativeTime } from "@/utils/date";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";
import { healthBadge, isComplianceExpired, reliabilityScoreColor } from "./sourceRowUtils";
import { SourceRowActions } from "./SourceRowActions";

type Mode = "active" | "archived";

export interface SourceTableRowProps {
  source: Source;
  category: Category | undefined;
  isSelected: boolean;
  mode: Mode;
  articleCount?: number;
  onToggleSelect: (id: string) => void;
  onEdit: (source: Source) => void;
  onVerify: (id: string) => void;
  onCompliance: (source: Source) => void;
  onArchive: (id: string) => void;
  onRestore: (id: string) => void;
  onDelete: (id: string) => void;
  onSourceClick?: (sourceId: string) => void;
  /** 가상 스크롤 모드 — 절대 포지셔닝용 style 을 받는다 */
  virtualStyle?: React.CSSProperties;
}

/**
 * 소스 테이블의 단일 행 `<tr>`. 가상/일반 스크롤 두 모드에서 공용.
 * 가상 스크롤 모드에서는 `virtualStyle` 을 통해 `position:absolute` 로 배치된다.
 *
 * 디자인 원칙 §8.3-9: 소스명/카테고리명 등 식별자는 말줄임 금지.
 *  - 소스명: 말줄임 없이 전체 노출
 *  - URL 보조 라인만 `truncate` 로 한 줄 유지 (식별자가 아닌 메타)
 *  - 카테고리 칩: 원본 UI 를 유지하되 `title` 속성으로 풀네임 접근 보장
 */
export function SourceTableRow({
  source,
  category,
  isSelected,
  mode,
  articleCount,
  onToggleSelect,
  onEdit,
  onVerify,
  onCompliance,
  onArchive,
  onRestore,
  onDelete,
  onSourceClick,
  virtualStyle
}: SourceTableRowProps) {
  const badge = healthBadge(source);
  const scoreColor = reliabilityScoreColor(source.reliabilityScore);
  const complianceExpired = isComplianceExpired(source.termsReviewedAt);

  return (
    <tr
      className={`border-b last:border-b-0 hover:bg-accent/20 ${isSelected ? "bg-primary/5" : ""}`}
      style={virtualStyle}
    >
      <td className="p-3">
        <Checkbox
          checked={isSelected}
          onCheckedChange={() => onToggleSelect(source.id)}
          aria-label={`${source.name} 선택`}
        />
      </td>
      <td className="p-3">
        <div className="flex items-center gap-1.5">
          {onSourceClick ? (
            <button
              type="button"
              className="font-medium hover:underline cursor-pointer text-left"
              onClick={() => onSourceClick(source.id)}
            >
              {source.name}
            </button>
          ) : (
            <span className="font-medium">{source.name}</span>
          )}
          <a
            href={source.url}
            target="_blank"
            rel="noopener noreferrer"
            className="shrink-0 text-muted-foreground hover:text-foreground"
            aria-label="원문 열기"
          >
            <ExternalLink size={10} />
          </a>
          {complianceExpired && (
            <span
              className="inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[10px] font-medium bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
              title="저작권 검토가 필요합니다"
            >
              <AlertTriangle size={10} />
              검토 필요
            </span>
          )}
        </div>
        <p className="text-xs text-muted-foreground truncate max-w-[300px]">{source.url}</p>
      </td>
      <td className="p-3">
        {category && (
          <span
            className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground truncate inline-block max-w-[160px]"
            title={category.name}
          >
            {category.name}
          </span>
        )}
      </td>
      <td className="p-3 text-right text-xs tabular-nums">
        {articleCount ? <span>{articleCount}</span> : <span className="text-muted-foreground">&mdash;</span>}
      </td>
      {mode === "active" && (
        <td className="p-3 text-center">
          <div className="flex flex-col items-center gap-0.5">
            <div className="flex items-center gap-1.5">
              <span className={`inline-flex px-1.5 py-0.5 rounded-full text-[10px] font-medium ${badge.className}`}>
                {badge.label}
              </span>
              <span className={`text-[10px] font-medium tabular-nums ${scoreColor}`}>{source.reliabilityScore}</span>
            </div>
            {source.crawlFailCount > 0 && (
              <span className="text-[10px] text-muted-foreground">{source.crawlFailCount}회 실패</span>
            )}
          </div>
        </td>
      )}
      <td className="p-3 text-right text-xs text-muted-foreground">
        {source.lastSuccessAt ? relativeTime(source.lastSuccessAt) : "—"}
      </td>
      <td className="p-3">
        <SourceRowActions
          mode={mode}
          source={source}
          onEdit={onEdit}
          onVerify={onVerify}
          onCompliance={onCompliance}
          onArchive={onArchive}
          onRestore={onRestore}
          onDelete={onDelete}
        />
      </td>
    </tr>
  );
}
