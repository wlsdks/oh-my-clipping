import { useRef, useState } from "react";
import { ChevronRight, RefreshCw, Archive } from "lucide-react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Button } from "@/components/ui/button";
import { SourcesTableFilters } from "./SourcesTableFilters";
import { SourcesTableHeader } from "./SourcesTableHeader";
import { SourceTableRow } from "./SourceTableRow";
import { sortSourcesBy, nextSourceSort, type SourceSortField, type SourceSortState } from "./model/sorting";
import type { Source, SourceComplianceStatus } from "@/types/source";
import type { Category } from "@/types/category";

type Mode = "active" | "archived";

interface CollapsibleSourcesTableProps {
  mode: Mode;
  sources: Source[];
  categories: Category[];
  articleCounts?: Record<string, number>;
  defaultOpen?: boolean;
  // 서버 사이드 필터 (부모에서 관리)
  query: string;
  onQueryChange: (value: string) => void;
  categoryId: string;
  onCategoryChange: (value: string) => void;
  complianceStatus?: "" | SourceComplianceStatus;
  onComplianceChange?: (value: "" | SourceComplianceStatus) => void;
  // 벌크 선택
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  onToggleSelectAll: (sources: Source[]) => void;
  onBulkVerify: () => void;
  onBulkArchive: () => void;
  // 개별 액션
  onEdit: (source: Source) => void;
  onVerify: (id: string) => void;
  onCompliance: (source: Source) => void;
  onArchive: (id: string) => void;
  onRestore: (id: string) => void;
  onDelete: (id: string) => void;
  onSourceClick?: (sourceId: string) => void;
}

const LABELS: Record<Mode, string> = {
  active: "정상 소스",
  archived: "보관함"
};

/** 행 높이가 이 값을 넘으면 가상 스크롤을 활성화한다. 300+ 소스 대응. */
const VIRTUAL_SCROLL_THRESHOLD = 80;

/** 가상 스크롤 컨테이너의 고정 높이(px) */
const VIRTUAL_SCROLL_HEIGHT = 600;

/** 기본 행 추정 높이(px) — 뱃지/2줄 URL 포함 */
const VIRTUAL_ROW_ESTIMATE_PX = 72;

const HEADER_BTN_CLASS =
  "inline-flex items-center gap-0.5 select-none cursor-pointer hover:text-foreground transition-colors";

/**
 * 소스 목록을 접히는 섹션 형태로 렌더하는 최상위 테이블 컴포넌트.
 *
 * 구조:
 *  - Trigger: 펼침 토글
 *  - Filters: 검색/카테고리/지역/준수상태 필터 바
 *  - Bulk bar: 선택 개수 > 0 일 때만 노출 (연결 확인/보관)
 *  - Body: 데이터 개수에 따라 일반 테이블 또는 가상 스크롤 테이블로 분기
 *
 * 서브 컴포넌트로 분리된 책임:
 *  - `SourcesTableHeader` — 정렬 가능한 헤더 행
 *  - `SourceTableRow` — 단일 행 (가상/일반 공용)
 *  - `SourceRowActions` — 행 우측 드롭다운 액션
 *  - `sourceRowUtils` — 건강 뱃지 / 신뢰도 색상 / 저작권 만료 판별
 */
export function CollapsibleSourcesTable({
  mode,
  sources,
  categories,
  articleCounts,
  defaultOpen = false,
  query,
  onQueryChange,
  categoryId,
  onCategoryChange,
  complianceStatus,
  onComplianceChange,
  selectedIds,
  onToggleSelect,
  onToggleSelectAll,
  onBulkVerify,
  onBulkArchive,
  onEdit,
  onVerify,
  onCompliance,
  onArchive,
  onRestore,
  onDelete,
  onSourceClick
}: CollapsibleSourcesTableProps) {
  const [open, setOpen] = useState(defaultOpen);
  const [region, setRegion] = useState("");
  const [sort, setSort] = useState<SourceSortState>({
    field: "name",
    direction: "asc"
  });
  const virtualScrollRef = useRef<HTMLDivElement>(null);

  const categoryMap = new Map(categories.map((c) => [c.id, c]));

  // 클라이언트 사이드 region 필터 (서버에 region 파라미터가 없으므로)
  const regionFiltered = region ? sources.filter((s) => s.sourceRegion === region) : sources;

  // 정렬 적용
  const sorted = sortSourcesBy(regionFiltered, sort, categoryMap);

  // 가상 스크롤은 THRESHOLD 초과 시에만 활성화한다 (소규모는 오버헤드만 늘어남).
  const useVirtualScroll = sorted.length > VIRTUAL_SCROLL_THRESHOLD;

  const rowVirtualizer = useVirtualizer({
    count: useVirtualScroll ? sorted.length : 0,
    getScrollElement: () => virtualScrollRef.current,
    estimateSize: () => VIRTUAL_ROW_ESTIMATE_PX,
    overscan: 8
  });

  const allSelected = sorted.length > 0 && sorted.every((s) => selectedIds.has(s.id));
  const someSelected = sorted.some((s) => selectedIds.has(s.id)) && !allSelected;
  const selectedCount = sorted.filter((s) => selectedIds.has(s.id)).length;

  function handleSort(field: SourceSortField) {
    setSort(nextSourceSort(sort, field));
  }

  // 행 렌더 공통 props — 가상/일반 분기에서 재사용
  function renderRow(source: Source, virtualStyle?: React.CSSProperties) {
    const category = categoryMap.get(source.categoryId);
    const isSelected = selectedIds.has(source.id);
    return (
      <SourceTableRow
        key={source.id}
        source={source}
        category={category}
        isSelected={isSelected}
        mode={mode}
        articleCount={articleCounts?.[source.id]}
        onToggleSelect={onToggleSelect}
        onEdit={onEdit}
        onVerify={onVerify}
        onCompliance={onCompliance}
        onArchive={onArchive}
        onRestore={onRestore}
        onDelete={onDelete}
        onSourceClick={onSourceClick}
        virtualStyle={virtualStyle}
      />
    );
  }

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="w-full flex items-center gap-2 py-2 px-3 rounded-lg hover:bg-accent/30 transition-colors"
        >
          <ChevronRight size={16} className={`text-muted-foreground transition-transform ${open ? "rotate-90" : ""}`} />
          <span className="text-sm font-medium">
            {LABELS[mode]} {sources.length}개
          </span>
        </button>
      </CollapsibleTrigger>

      <CollapsibleContent className="mt-3 space-y-3">
        <SourcesTableFilters
          query={query}
          onQueryChange={onQueryChange}
          categoryId={categoryId}
          onCategoryChange={onCategoryChange}
          region={region}
          onRegionChange={setRegion}
          complianceStatus={complianceStatus}
          onComplianceChange={onComplianceChange}
          categories={categories}
          filteredCount={sorted.length}
          totalCount={sources.length}
        />

        {/* 벌크 액션 바 */}
        {selectedCount > 0 && (
          <div className="sticky top-0 z-10 flex items-center gap-3 rounded-lg border bg-card px-4 py-2.5 shadow-sm">
            <span className="text-sm font-medium">{selectedCount}개 선택</span>
            <div className="flex gap-2 ml-auto">
              <Button variant="outline" size="sm" className="h-8 text-xs" onClick={onBulkVerify}>
                <RefreshCw size={12} className="mr-1" />
                연결 확인
              </Button>
              {mode === "active" && (
                <Button variant="outline" size="sm" className="h-8 text-xs" onClick={onBulkArchive}>
                  <Archive size={12} className="mr-1" />
                  보관
                </Button>
              )}
            </div>
          </div>
        )}

        {sorted.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">조건에 맞는 소스가 없어요</p>
        ) : useVirtualScroll ? (
          <div className="rounded-xl border bg-card overflow-hidden">
            <table className="w-full text-sm" role="table" aria-label={`${LABELS[mode]} 목록 헤더`}>
              <thead className="border-b bg-muted/30">
                <SourcesTableHeader
                  mode={mode}
                  allSelected={allSelected}
                  someSelected={someSelected}
                  onToggleAll={() => onToggleSelectAll(sorted)}
                  sort={sort}
                  onSort={handleSort}
                  headerBtnClass={HEADER_BTN_CLASS}
                />
              </thead>
            </table>
            <div
              ref={virtualScrollRef}
              className="overflow-auto"
              style={{ height: `${VIRTUAL_SCROLL_HEIGHT}px` }}
              role="region"
              aria-label={`${LABELS[mode]} 가상 스크롤`}
            >
              <table
                className="w-full text-sm"
                style={{ tableLayout: "fixed" }}
                role="table"
                aria-label={`${LABELS[mode]} 목록`}
              >
                <tbody
                  style={{
                    display: "block",
                    position: "relative",
                    height: `${rowVirtualizer.getTotalSize()}px`
                  }}
                >
                  {rowVirtualizer.getVirtualItems().map((virtualItem) =>
                    renderRow(sorted[virtualItem.index], {
                      position: "absolute",
                      top: 0,
                      left: 0,
                      width: "100%",
                      display: "table",
                      transform: `translateY(${virtualItem.start}px)`
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className="rounded-xl border bg-card overflow-hidden">
            <table className="w-full text-sm" role="table" aria-label={`${LABELS[mode]} 목록`}>
              <thead className="border-b bg-muted/30">
                <SourcesTableHeader
                  mode={mode}
                  allSelected={allSelected}
                  someSelected={someSelected}
                  onToggleAll={() => onToggleSelectAll(sorted)}
                  sort={sort}
                  onSort={handleSort}
                  headerBtnClass={HEADER_BTN_CLASS}
                />
              </thead>
              <tbody>{sorted.map((source) => renderRow(source))}</tbody>
            </table>
          </div>
        )}
      </CollapsibleContent>
    </Collapsible>
  );
}
