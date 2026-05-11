import { useState } from "react";
import { Check, ChevronDown, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { cn } from "@/utils/cn";
import { organizationKeys } from "@/queries/organizationKeys";
import { organizationService } from "@/services/organizationService";
import { ORGANIZATION_TYPE_LABELS, type Organization } from "@/types/organization";

interface Props {
  /** 현재 선택된 organization id 목록. */
  value: string[];
  /** 선택 변경 시 호출. 새 id 배열을 그대로 전달. */
  onChange: (next: string[]) => void;
  /** 저장 중 / 수정 불가 상태. */
  disabled?: boolean;
  /** trigger button 의 placeholder (선택이 비어있을 때). */
  placeholder?: string;
}

/**
 * 여러 Organization 을 동시에 선택할 수 있는 Combobox 형 컨트롤.
 *
 * - trigger 를 누르면 Popover 열림 + 검색 input + 체크 리스트 표시
 * - 선택된 항목은 외부에 pill 로 표시 + X 버튼으로 개별 제거
 * - 빈 상태: "기업이 없어요" 안내 (운영자가 `/admin/organizations` 에서 먼저 생성)
 */
export function OrganizationMultiSelect({
  value,
  onChange,
  disabled,
  placeholder = "기업을 선택하세요",
}: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const { data, isLoading, isError } = useQuery({
    queryKey: organizationKeys.list(),
    queryFn: () => organizationService.list(),
  });

  const organizations = data?.content ?? [];
  const orgById = new Map(organizations.map((o) => [o.id, o]));
  const selectedOrgs: Organization[] = value
    .map((id) => orgById.get(id))
    .filter((o): o is Organization => !!o);

  // 선택된 id 중 서버에 없는 stale id 는 조용히 무시한다 (삭제된 organization).

  // 검색 필터 — 대소문자 무시 name 부분 일치.
  const q = query.trim().toLowerCase();
  const filtered = q
    ? organizations.filter(
        (o) => o.name.toLowerCase().includes(q) || (o.domain ?? "").toLowerCase().includes(q),
      )
    : organizations;

  function toggle(id: string) {
    if (value.includes(id)) {
      onChange(value.filter((v) => v !== id));
    } else {
      onChange([...value, id]);
    }
  }

  function remove(id: string) {
    onChange(value.filter((v) => v !== id));
  }

  return (
    <div className="space-y-2">
      <Popover open={open} onOpenChange={(v) => !disabled && setOpen(v)}>
        <PopoverTrigger asChild>
          <button
            type="button"
            disabled={disabled}
            className={cn(
              "flex w-full items-center justify-between rounded-lg border border-input bg-background px-3 py-2 text-sm",
              "hover:border-foreground/30 transition-colors",
              "disabled:cursor-not-allowed disabled:opacity-60",
            )}
            aria-haspopup="listbox"
            aria-expanded={open}
          >
            <span className={cn(selectedOrgs.length === 0 && "text-muted-foreground")}>
              {selectedOrgs.length === 0
                ? placeholder
                : `${selectedOrgs.length}개 선택됨`}
            </span>
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-[320px] p-2" align="start">
          <Input
            placeholder="이름 또는 도메인 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="mb-2"
            autoFocus
          />
          <div className="max-h-60 overflow-y-auto" role="listbox">
            {isLoading ? (
              <div className="p-3 text-xs text-muted-foreground">불러오는 중...</div>
            ) : isError ? (
              <div className="p-3 text-xs text-destructive">기업 목록을 불러오지 못했어요</div>
            ) : organizations.length === 0 ? (
              <div className="p-3 text-xs text-muted-foreground">
                아직 등록된 기업이 없어요. <br />
                관심 기업에서 먼저 추가하세요.
              </div>
            ) : filtered.length === 0 ? (
              <div className="p-3 text-xs text-muted-foreground">검색 결과가 없어요</div>
            ) : (
              <ul className="space-y-0.5">
                {filtered.map((org) => {
                  const isSelected = value.includes(org.id);
                  return (
                    <li key={org.id}>
                      <button
                        type="button"
                        role="option"
                        aria-selected={isSelected}
                        onClick={() => toggle(org.id)}
                        className={cn(
                          "flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-sm",
                          "hover:bg-muted transition-colors",
                        )}
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <Check
                            className={cn(
                              "h-4 w-4 shrink-0",
                              isSelected ? "text-primary" : "text-transparent",
                            )}
                          />
                          <span className="truncate">{org.name}</span>
                        </div>
                        <span className="text-xs text-muted-foreground shrink-0">
                          {ORGANIZATION_TYPE_LABELS[org.type]}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
          <div className="mt-2 flex justify-end">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setOpen(false)}
            >
              완료
            </Button>
          </div>
        </PopoverContent>
      </Popover>

      {selectedOrgs.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selectedOrgs.map((org) => (
            <Badge key={org.id} variant="secondary" className="gap-1 pr-1">
              {org.name}
              <button
                type="button"
                onClick={() => remove(org.id)}
                disabled={disabled}
                className="rounded-full p-0.5 hover:bg-muted disabled:opacity-50"
                aria-label={`${org.name} 제거`}
              >
                <X className="h-3 w-3" />
              </button>
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
}
