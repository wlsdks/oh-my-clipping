import { cn } from "@/utils/cn";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { X } from "lucide-react";
import { MemberSearchBar } from "./MemberSearchBar";
import { isDefaultFilters, type MemberFilters, type SimplifiedActivityFilter } from "./memberFilters";

// 활동 상태 칩 3개
const ACTIVITY_OPTIONS: { value: SimplifiedActivityFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "ACTIVE", label: "활성" },
  { value: "INACTIVE", label: "비활성" },
];

interface ChipGroupProps {
  options: { value: SimplifiedActivityFilter; label: string }[];
  value: SimplifiedActivityFilter;
  onChange: (value: SimplifiedActivityFilter) => void;
}

function ActivityChipGroup({ options, value, onChange }: ChipGroupProps) {
  return (
    <div className="flex gap-1">
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className={cn(
            "rounded-full px-3 py-1 text-xs font-medium transition-colors",
            value === opt.value
              ? "bg-primary text-primary-foreground"
              : "bg-muted text-muted-foreground hover:bg-muted/80"
          )}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

interface MemberFilterBarProps {
  search: string;
  onSearchChange: (value: string) => void;
  filters: MemberFilters;
  onFiltersChange: (filters: MemberFilters) => void;
  departments: string[];
  hasNoDept: boolean;
}

export function MemberFilterBar({
  search,
  onSearchChange,
  filters,
  onFiltersChange,
  departments,
  hasNoDept,
}: MemberFilterBarProps) {
  function updateFilter<K extends keyof MemberFilters>(key: K, value: MemberFilters[K]) {
    onFiltersChange({ ...filters, [key]: value });
  }

  function handleDeptChange(value: string) {
    updateFilter("department", value === "all" ? null : value);
  }

  function handleRoleChange(value: string) {
    updateFilter("role", value as MemberFilters["role"]);
  }

  function handleSubscriptionChange(value: string) {
    updateFilter("subscription", value as MemberFilters["subscription"]);
  }

  const showReset = !isDefaultFilters(filters);

  return (
    <div className="flex items-center gap-2 flex-wrap">
      {/* 검색 */}
      <MemberSearchBar value={search} onChange={onSearchChange} />

      {/* 부서 */}
      <Select value={filters.department ?? "all"} onValueChange={handleDeptChange}>
        <SelectTrigger className="w-36 h-8 text-xs">
          <SelectValue placeholder="부서" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">전체 부서</SelectItem>
          {hasNoDept && <SelectItem value="__none__">(부서 없음)</SelectItem>}
          {departments.map((dept) => (
            <SelectItem key={dept} value={dept}>{dept}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* 역할 Select */}
      <Select value={filters.role} onValueChange={handleRoleChange}>
        <SelectTrigger className="w-28 h-8 text-xs">
          <SelectValue placeholder="역할" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">전체 역할</SelectItem>
          <SelectItem value="ADMIN">관리자</SelectItem>
          <SelectItem value="USER">사용자</SelectItem>
        </SelectContent>
      </Select>

      {/* 구독 Select */}
      <Select value={filters.subscription} onValueChange={handleSubscriptionChange}>
        <SelectTrigger className="w-28 h-8 text-xs">
          <SelectValue placeholder="구독" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">전체 구독</SelectItem>
          <SelectItem value="HAS">구독 있음</SelectItem>
          <SelectItem value="NONE">구독 없음</SelectItem>
        </SelectContent>
      </Select>

      {/* 활동 상태 칩 3개 */}
      <ActivityChipGroup
        options={ACTIVITY_OPTIONS}
        value={filters.activityStatus}
        onChange={(v) => updateFilter("activityStatus", v)}
      />

      {/* 필터 초기화 */}
      {showReset && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onFiltersChange({
            department: null,
            role: "ALL",
            activityStatus: "ALL",
            subscription: "ALL",
          })}
          className="h-7 text-xs text-muted-foreground gap-1"
        >
          <X className="h-3 w-3" />
          초기화
        </Button>
      )}
    </div>
  );
}
