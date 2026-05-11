import { cn } from "@/utils/cn";
import type { PromptOption } from "../model/personaPromptOptions";

interface PersonaChipGroupProps {
  /** 그룹 제목 (예: "관점", "분량", "말투", "추가 옵션") */
  label: string;
  /** 선택 방식 안내 문구 */
  selectionHint: string;
  /** 그룹이 노출하는 옵션 목록 */
  options: PromptOption[];
  /** 특정 옵션이 현재 선택되어 있는지 판정 */
  isSelected: (option: PromptOption) => boolean;
  /** 옵션 토글 핸들러 */
  onToggle: (option: PromptOption) => void;
  /** 접근성 role — 배타 선택이면 "radiogroup", 다중 선택이면 "group" */
  role: "radiogroup" | "group";
  disabled?: boolean;
}

/**
 * 페르소나 커스텀 편집 화면에서 프롬프트 옵션을 pill-chip 으로 노출한다.
 * 관점/분량/말투(배타 선택), 추가 옵션(다중 선택) 모두 이 컴포넌트로 렌더링한다.
 */
export function PersonaChipGroup({
  label,
  selectionHint,
  options,
  isSelected,
  onToggle,
  role,
  disabled
}: PersonaChipGroupProps) {
  // 배타 선택 그룹은 radiogroup, 다중 선택은 일반 group 으로 처리한다.
  const isRadio = role === "radiogroup";
  return (
    <div className="space-y-1">
      <span className="text-xs font-medium">
        {label}{" "}
        <span className="text-muted-foreground font-normal">({selectionHint})</span>
      </span>
      <div className="flex flex-wrap gap-1.5" role={role} aria-label={label}>
        {options.map((opt) => {
          const selected = isSelected(opt);
          return (
            <button
              key={opt.label}
              type="button"
              className={cn(
                "px-2.5 py-1 text-xs rounded-full border transition-colors",
                selected
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-background border-border hover:bg-muted"
              )}
              role={isRadio ? "radio" : undefined}
              aria-checked={isRadio ? selected : undefined}
              aria-pressed={isRadio ? undefined : selected}
              onClick={() => onToggle(opt)}
              disabled={disabled}
            >
              {opt.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
