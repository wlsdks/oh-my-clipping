import { Search } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Category } from "@/types/category";
import type { SourceComplianceStatus } from "@/types/source";

interface SourcesTableFiltersProps {
  query: string;
  onQueryChange: (value: string) => void;
  categoryId: string;
  onCategoryChange: (value: string) => void;
  region: string;
  onRegionChange: (value: string) => void;
  /** 저작권 필터 — 전체("") 또는 SourceComplianceStatus */
  complianceStatus?: "" | SourceComplianceStatus;
  onComplianceChange?: (value: "" | SourceComplianceStatus) => void;
  categories: Category[];
  filteredCount: number;
  totalCount: number;
}

const ALL_CATEGORIES = "__all__";
const ALL_REGIONS = "__all__";
const ALL_COMPLIANCE = "__all__";

export function SourcesTableFilters({
  query,
  onQueryChange,
  categoryId,
  onCategoryChange,
  region,
  onRegionChange,
  complianceStatus,
  onComplianceChange,
  categories,
  filteredCount,
  totalCount,
}: SourcesTableFiltersProps) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <div className="relative flex-1 min-w-[200px]">
        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          placeholder="소스명, URL 검색"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          className="w-full rounded-lg border border-border bg-card py-2 pl-9 pr-3 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none"
          aria-label="소스 검색"
        />
      </div>

      <Select
        value={categoryId || ALL_CATEGORIES}
        onValueChange={(v) => onCategoryChange(v === ALL_CATEGORIES ? "" : v)}
      >
        <SelectTrigger className="w-[140px] h-9 text-xs">
          <SelectValue placeholder="주제" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL_CATEGORIES}>전체 주제</SelectItem>
          {categories.map((cat) => (
            <SelectItem key={cat.id} value={cat.id}>
              {cat.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        value={region || ALL_REGIONS}
        onValueChange={(v) => onRegionChange(v === ALL_REGIONS ? "" : v)}
      >
        <SelectTrigger className="w-[100px] h-9 text-xs">
          <SelectValue placeholder="지역" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL_REGIONS}>전체</SelectItem>
          <SelectItem value="DOMESTIC">국내</SelectItem>
          <SelectItem value="GLOBAL">해외</SelectItem>
        </SelectContent>
      </Select>

      {onComplianceChange && (
        <Select
          value={complianceStatus || ALL_COMPLIANCE}
          onValueChange={(v) =>
            onComplianceChange(
              v === ALL_COMPLIANCE ? "" : (v as SourceComplianceStatus),
            )
          }
        >
          <SelectTrigger className="w-[120px] h-9 text-xs">
            <SelectValue placeholder="저작권" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_COMPLIANCE}>전체 저작권</SelectItem>
            <SelectItem value="EXPIRED">만료</SelectItem>
            <SelectItem value="EXPIRING_SOON">만료 임박</SelectItem>
            <SelectItem value="NEVER_REVIEWED">미검토</SelectItem>
            <SelectItem value="VALID">정상</SelectItem>
          </SelectContent>
        </Select>
      )}

      <span className="text-xs text-muted-foreground tabular-nums">
        {filteredCount}/{totalCount}
      </span>
    </div>
  );
}
