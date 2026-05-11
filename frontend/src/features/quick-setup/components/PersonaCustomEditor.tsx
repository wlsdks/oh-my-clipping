import { forwardRef } from "react";
import type { QuickSetupForm } from "../model/quickSetupTypes";
import {
  ADDON_OPTIONS,
  LENGTH_OPTIONS,
  PERSPECTIVE_OPTIONS,
  TONE_OPTIONS,
  hasSnippet,
  type PromptOption
} from "../model/personaPromptOptions";
import { PersonaChipGroup } from "./PersonaChipGroup";

interface PersonaCustomEditorProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  /** 편집 모드 여부 (true = 기존 스타일 수정, false = 신규 작성) */
  isEditing: boolean;
  /** 저장 진행 중 여부 */
  saving: boolean;
  /** 선택 화면으로 복귀 */
  onBack: () => void;
  /** 내 스타일로 저장 */
  onSave: () => void;
  disabled?: boolean;
}

/**
 * 페르소나 커스텀 편집 뷰.
 *
 * 구성:
 * - 스타일 이름 입력
 * - 관점/분량/말투/추가옵션 4개 chip group
 * - 자유 텍스트(추가 요청 사항) textarea
 * - 저장 버튼
 *
 * ref 는 상위가 `scrollIntoView` 하기 위해 전달한다.
 */
export const PersonaCustomEditor = forwardRef<HTMLDivElement, PersonaCustomEditorProps>(
  function PersonaCustomEditor(
    { form, onChange, isEditing, saving, onBack, onSave, disabled },
    ref
  ) {
    // 배타 그룹 토글 — 기존 옵션 snippet 전체 제거 후 새 snippet 추가
    function toggleExclusive(options: PromptOption[], opt: PromptOption) {
      let next = form.personaPrompt;
      for (const o of options) {
        next = next.replace(o.snippet, "");
      }
      next = next.replace(/\n{2,}/g, "\n").trim();
      if (!hasSnippet(form.personaPrompt, opt.snippet)) {
        next = next ? `${next}\n${opt.snippet}` : opt.snippet;
      }
      onChange({ personaPrompt: next });
    }

    // 다중 선택 그룹 토글 — 이미 있으면 제거, 없으면 추가
    function toggleAddon(opt: PromptOption) {
      const current = form.personaPrompt;
      if (hasSnippet(current, opt.snippet)) {
        onChange({
          personaPrompt: current
            .replace(opt.snippet, "")
            .replace(/\n{2,}/g, "\n")
            .trim()
        });
      } else {
        onChange({
          personaPrompt: current ? `${current}\n${opt.snippet}` : opt.snippet
        });
      }
    }

    // "일반 독자" 는 snippet 이 빈 문자열 — 나머지 관점이 선택 안 돼 있으면 기본값으로 간주
    const perspectiveExclusive = PERSPECTIVE_OPTIONS.filter((p) => p.snippet !== "");
    function isPerspectiveSelected(opt: PromptOption): boolean {
      if (opt.snippet === "") {
        return !perspectiveExclusive.some((p) => hasSnippet(form.personaPrompt, p.snippet));
      }
      return hasSnippet(form.personaPrompt, opt.snippet);
    }

    const saveDisabled =
      disabled || saving || !form.personaName.trim() || !form.personaPrompt.trim();

    return (
      <div className="space-y-4 py-2" ref={ref}>
        <button
          type="button"
          className="text-xs text-muted-foreground hover:text-foreground transition-colors"
          onClick={onBack}
        >
          &larr; 스타일 선택으로
        </button>
        <h3 className="text-sm font-semibold">
          {isEditing ? "스타일 수정" : "내 스타일 만들기"}
        </h3>

        <div className="space-y-1">
          <label className="text-xs font-medium" htmlFor="persona-custom-name">
            스타일 이름
          </label>
          <input
            id="persona-custom-name"
            className="w-full px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
            value={form.personaName}
            onChange={(e) => onChange({ personaName: e.target.value })}
            placeholder="예: 우리 팀 전용 스타일"
            disabled={disabled}
          />
        </div>

        <PersonaChipGroup
          label="관점"
          selectionHint="1개 선택"
          options={PERSPECTIVE_OPTIONS}
          isSelected={isPerspectiveSelected}
          onToggle={(opt) => toggleExclusive(perspectiveExclusive, opt)}
          role="radiogroup"
          disabled={disabled}
        />

        <PersonaChipGroup
          label="분량"
          selectionHint="1개 선택"
          options={LENGTH_OPTIONS}
          isSelected={(opt) => hasSnippet(form.personaPrompt, opt.snippet)}
          onToggle={(opt) => toggleExclusive(LENGTH_OPTIONS, opt)}
          role="radiogroup"
          disabled={disabled}
        />

        <PersonaChipGroup
          label="말투"
          selectionHint="1개 선택"
          options={TONE_OPTIONS}
          isSelected={(opt) => hasSnippet(form.personaPrompt, opt.snippet)}
          onToggle={(opt) => toggleExclusive(TONE_OPTIONS, opt)}
          role="radiogroup"
          disabled={disabled}
        />

        <PersonaChipGroup
          label="추가 옵션"
          selectionHint="여러 개 선택 가능"
          options={ADDON_OPTIONS}
          isSelected={(opt) => hasSnippet(form.personaPrompt, opt.snippet)}
          onToggle={toggleAddon}
          role="group"
          disabled={disabled}
        />

        <div className="space-y-1">
          <label className="text-xs font-medium" htmlFor="persona-custom-prompt">
            추가 요청 사항 (선택)
          </label>
          <textarea
            id="persona-custom-prompt"
            className="w-full px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring resize-none"
            rows={3}
            value={form.personaPrompt}
            onChange={(e) => onChange({ personaPrompt: e.target.value })}
            placeholder={"위 옵션을 조합하거나, 자유롭게 적어보세요.\n예: 교육부 정책 뉴스는 반드시 포함해주세요"}
            disabled={disabled}
          />
          <p className="text-xs text-muted-foreground">
            위 옵션을 선택하면 자동으로 채워져요. 직접 수정하거나 추가해도 돼요
          </p>
        </div>

        <button
          type="button"
          className="px-4 py-2 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors disabled:opacity-50"
          disabled={saveDisabled}
          onClick={onSave}
        >
          {saving ? "저장 중\u2026" : isEditing ? "수정 저장" : "내 스타일로 저장"}
        </button>
      </div>
    );
  }
);
