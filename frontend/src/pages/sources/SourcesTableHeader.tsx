import { ArrowUp, ArrowDown, ArrowUpDown } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import type { SourceSortField, SourceSortState } from "./model/sorting";

type Mode = "active" | "archived";

/**
 * 정렬 상태에 따라 up/down/양방향 아이콘을 렌더한다.
 * 현재 정렬 필드가 아니면 muted 의 양방향 아이콘을 돌려준다.
 */
function SortIcon({ field, current }: { field: SourceSortField; current: SourceSortState }) {
  if (current.field !== field) {
    return <ArrowUpDown className="ml-1 inline h-3.5 w-3.5 text-muted-foreground/50" />;
  }
  return current.direction === "asc" ? (
    <ArrowUp className="ml-1 inline h-3.5 w-3.5" />
  ) : (
    <ArrowDown className="ml-1 inline h-3.5 w-3.5" />
  );
}

interface SourcesTableHeaderProps {
  mode: Mode;
  allSelected: boolean;
  someSelected: boolean;
  onToggleAll: () => void;
  sort: SourceSortState;
  onSort: (field: SourceSortField) => void;
  headerBtnClass: string;
}

/**
 * 일반 / 가상 스크롤 두 모드에서 공유하는 테이블 헤더 행.
 * 가상 스크롤 모드에서는 header-only `<table>` 안의 thead 에 재사용된다.
 *
 * a11y: 각 정렬 버튼은 현재 방향을 `aria-sort` 로 노출하여 스크린리더가
 * 정렬 상태를 읽을 수 있게 한다.
 */
export function SourcesTableHeader({
  mode,
  allSelected,
  someSelected,
  onToggleAll,
  sort,
  onSort,
  headerBtnClass
}: SourcesTableHeaderProps) {
  // aria-sort 는 현재 정렬 중인 컬럼에만 세팅한다
  function ariaSortFor(field: SourceSortField): "ascending" | "descending" | "none" {
    if (sort.field !== field) return "none";
    return sort.direction === "asc" ? "ascending" : "descending";
  }

  return (
    <tr>
      <th className="p-3 w-10" scope="col">
        <Checkbox
          checked={allSelected ? true : someSelected ? "indeterminate" : false}
          onCheckedChange={onToggleAll}
          aria-label="전체 선택"
        />
      </th>
      <th className="text-left p-3 font-medium" scope="col" aria-sort={ariaSortFor("name")}>
        <button type="button" className={headerBtnClass} onClick={() => onSort("name")} aria-label="소스명으로 정렬">
          소스명
          <SortIcon field="name" current={sort} />
        </button>
      </th>
      <th className="text-left p-3 font-medium w-40" scope="col" aria-sort={ariaSortFor("category")}>
        <button type="button" className={headerBtnClass} onClick={() => onSort("category")} aria-label="주제로 정렬">
          주제
          <SortIcon field="category" current={sort} />
        </button>
      </th>
      <th className="text-right p-3 font-medium w-16" scope="col">
        <span className="text-muted-foreground">7일</span>
      </th>
      {mode === "active" && (
        <th className="text-center p-3 font-medium w-20" scope="col" aria-sort={ariaSortFor("reliability")}>
          <button
            type="button"
            className={headerBtnClass}
            onClick={() => onSort("reliability")}
            aria-label="수집 상태로 정렬"
          >
            상태
            <SortIcon field="reliability" current={sort} />
          </button>
        </th>
      )}
      <th className="text-right p-3 font-medium w-24" scope="col" aria-sort={ariaSortFor("updatedAt")}>
        <button
          type="button"
          className={headerBtnClass}
          onClick={() => onSort("updatedAt")}
          aria-label="마지막 수집 시각으로 정렬"
        >
          마지막 수집
          <SortIcon field="updatedAt" current={sort} />
        </button>
      </th>
      <th className="p-3 w-10" scope="col" aria-label="행 액션" />
    </tr>
  );
}
