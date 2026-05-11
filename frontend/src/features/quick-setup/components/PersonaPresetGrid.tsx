import { cn } from "@/utils/cn";

/** 관리자가 미리 정의한 요약 스타일 프리셋 */
export interface StylePreset {
  id: string;
  label: string;
  desc: string;
  summaryStyle: string;
  targetAudience: string;
  prompt: string;
  previewTitle: string;
  previewSource: string;
  previewBody: string;
}

interface PersonaPresetGridProps {
  /** 노출할 프리셋 목록 (서버 로드 실패 시 폴백 포함) */
  presets: StylePreset[];
  /** 현재 선택된 프리셋 키 */
  selectedKey: string;
  /** 프리셋 선택 핸들러 */
  onSelect: (preset: StylePreset) => void;
  disabled?: boolean;
}

/**
 * 요약 스타일 프리셋을 2열 그리드 카드로 노출한다.
 * 배타 선택이므로 radiogroup 으로 감싸 접근성을 보강한다.
 */
export function PersonaPresetGrid({
  presets,
  selectedKey,
  onSelect,
  disabled
}: PersonaPresetGridProps) {
  return (
    <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="요약 스타일 프리셋">
      {presets.map((preset) => {
        const selected = selectedKey === preset.id;
        return (
          <button
            key={preset.id}
            type="button"
            className={cn(
              "p-3 text-left rounded-lg border transition-colors space-y-0.5",
              selected ? "border-primary bg-primary/5" : "border-border hover:bg-muted"
            )}
            role="radio"
            aria-checked={selected}
            onClick={() => onSelect(preset)}
            disabled={disabled}
          >
            <div className="text-sm font-medium">{preset.label}</div>
            <div className="text-xs text-muted-foreground">{preset.desc}</div>
          </button>
        );
      })}
    </div>
  );
}
