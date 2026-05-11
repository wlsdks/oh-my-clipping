import type { Persona } from "@/types/persona";
import { cn } from "@/utils/cn";

interface SavedPersonaListProps {
  /** 저장된 내 스타일 목록 */
  personas: Persona[];
  /** personaId → 표시 이름 매핑 (중복 시 접미사 처리) */
  labelById: Record<string, string>;
  /** 현재 선택된 키 (프리셋: presetId, 저장 스타일: `saved-{id}`) */
  selectedKey: string;
  /** 저장 스타일 카드 클릭 */
  onSelect: (persona: Persona) => void;
  /** 연필 아이콘 클릭 — 수정 모드 진입 */
  onEdit: (persona: Persona) => void;
  /** 휴지통 아이콘 클릭 — 삭제 확인 dialog 오픈 */
  onRequestDelete: (persona: Persona) => void;
  disabled?: boolean;
}

/**
 * 사용자가 직접 저장한 "내 스타일" 목록을 2열 그리드로 노출한다.
 * 카드 내부에 수정/삭제 아이콘 버튼을 배치하고 stopPropagation 으로
 * 카드 선택 액션과 버튼 액션이 겹치지 않게 한다.
 */
export function SavedPersonaList({
  personas,
  labelById,
  selectedKey,
  onSelect,
  onEdit,
  onRequestDelete,
  disabled
}: SavedPersonaListProps) {
  if (personas.length === 0) return null;

  return (
    <div className="space-y-2">
      <span className="text-xs font-medium text-muted-foreground">내 스타일</span>
      <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="저장된 내 스타일">
        {personas.map((persona) => {
          const selected = selectedKey === `saved-${persona.id}`;
          const displayName = labelById[persona.id] || persona.name;
          return (
            <button
              key={persona.id}
              type="button"
              className={cn(
                "p-3 text-left rounded-lg border transition-colors space-y-0.5 relative",
                selected ? "border-primary bg-primary/5" : "border-border hover:bg-muted"
              )}
              role="radio"
              aria-checked={selected}
              onClick={() => onSelect(persona)}
              disabled={disabled}
            >
              <div className="text-sm font-medium pr-8">{displayName}</div>
              <div className="text-xs text-muted-foreground">
                {persona.description || persona.targetAudience || "커스텀 스타일"}
              </div>
              <span
                className="absolute top-2 right-2 flex gap-1"
                onClick={(e) => e.stopPropagation()}
              >
                <button
                  type="button"
                  className="text-xs hover:opacity-70 transition-opacity"
                  title="수정"
                  aria-label={`${displayName} 스타일 수정`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onEdit(persona);
                  }}
                  disabled={disabled}
                >
                  &#x270F;&#xFE0F;
                </button>
                <button
                  type="button"
                  className="text-xs hover:opacity-70 transition-opacity"
                  title="삭제"
                  aria-label={`${displayName} 스타일 삭제`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onRequestDelete(persona);
                  }}
                  disabled={disabled}
                >
                  &#x1F5D1;&#xFE0F;
                </button>
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
