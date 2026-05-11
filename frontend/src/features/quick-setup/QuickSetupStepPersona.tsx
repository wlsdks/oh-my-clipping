import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import type { QuickSetupForm } from "./model/quickSetupTypes";
import { DEFAULT_PERSONA_PROMPT } from "./model/quickSetupTypes";
import type { Persona } from "@/types/persona";
import { personaService } from "@/services/personaService";
import { personaKeys } from "@/queries/personaKeys";
import { buildPersonaLabelMap } from "@/shared/lib/personaLabels";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { PersonaPresetGrid, type StylePreset } from "./components/PersonaPresetGrid";
import { PersonaPreview } from "./components/PersonaPreview";
import { SavedPersonaList } from "./components/SavedPersonaList";
import { PersonaCustomEditor } from "./components/PersonaCustomEditor";
import { PersonaDeleteDialog } from "./components/PersonaDeleteDialog";

interface QuickSetupStepPersonaProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled?: boolean;
  savedPersonas: Persona[];
  onPersonaSaved: () => void;
  isUserMode?: boolean;
  onCustomEditing?: (editing: boolean) => void;
}

type ViewMode = "select" | "custom";

/** Persona 엔티티 → 프리셋 카드 표시용 DTO */
function presetToStylePreset(p: Persona): StylePreset {
  return {
    id: p.id,
    label: p.name,
    desc: p.description ?? "",
    summaryStyle: p.summaryStyle ?? "",
    targetAudience: p.targetAudience ?? "",
    prompt: p.systemPrompt,
    previewTitle: p.previewTitle ?? "",
    previewSource: p.previewSource ?? "",
    previewBody: p.previewBody ?? ""
  };
}

/** 현재 form 상태 + 프리셋/저장목록을 보고 어떤 카드가 선택돼 있는지 판정한다 */
function detectSelectedPresetKey(
  form: QuickSetupForm,
  presets: StylePreset[],
  savedPersonas: Persona[]
): string {
  if (form.selectedPresetId) {
    const preset = presets.find((item) => item.id === form.selectedPresetId);
    if (preset) return preset.id;
  }
  if (form.selectedPersonaId) {
    const savedPersona = savedPersonas.find((persona) => persona.id === form.selectedPersonaId);
    if (savedPersona) return `saved-${savedPersona.id}`;
  }
  const preset = presets.find((item) => item.prompt.trim() === form.personaPrompt.trim());
  return preset?.id ?? "";
}

/**
 * 위자드 Step: 요약 스타일(Persona) 선택 또는 커스텀 작성.
 *
 * 책임: 상태 orchestration + 데이터 fetch + 하위 컴포넌트 조합.
 * 실제 렌더링은 select/custom 뷰별로 컴포넌트 분리.
 */
export function QuickSetupStepPersona({
  form,
  onChange,
  disabled,
  savedPersonas,
  onPersonaSaved,
  isUserMode,
  onCustomEditing
}: QuickSetupStepPersonaProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("select");
  const [editingPersonaId, setEditingPersonaId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [pendingDeletePersona, setPendingDeletePersona] = useState<Persona | null>(null);
  const customAreaRef = useRef<HTMLDivElement>(null);
  const selectionBeforeCustomRef = useRef<Partial<QuickSetupForm> | null>(null);

  // 서버에서 관리자 프리셋을 동적 로드한다.
  const { data: presetPersonas = [] } = useQuery({
    queryKey: personaKeys.presets(),
    queryFn: () => personaService.getPresets()
  });

  const stylePresets: StylePreset[] = presetPersonas.map(presetToStylePreset);

  // 프리셋 로드 실패/지연 시 최소한의 기본 카드 1개를 보장한다.
  const fallbackPreset: StylePreset = {
    id: "core-summary",
    label: "핵심 요약",
    desc: "바쁜 하루, 1분이면 충분합니다",
    summaryStyle: "핵심 2~3줄 + 왜 중요한가 1~2줄 + 한 줄 요약",
    targetAudience: "전 직원 (기본 템플릿)",
    prompt: DEFAULT_PERSONA_PROMPT,
    previewTitle: "",
    previewSource: "",
    previewBody: ""
  };

  const effectivePresets = stylePresets.length > 0 ? stylePresets : [fallbackPreset];
  const selectedPresetKey = detectSelectedPresetKey(form, effectivePresets, savedPersonas);
  const currentPreset = effectivePresets.find((preset) => preset.id === selectedPresetKey);
  const personaLabelById = buildPersonaLabelMap(savedPersonas);

  // user/admin 모드별로 서비스 API 를 교환 가능하게 바인딩한다.
  const svcApi = {
    create: isUserMode ? personaService.createUser : personaService.create,
    update: isUserMode ? personaService.updateUser : personaService.update,
    delete: isUserMode ? personaService.deleteUser : personaService.delete
  };

  function handlePresetSelect(preset: StylePreset) {
    // 프리셋은 복사본을 만들지 않고 ID 를 직접 참조한다.
    // 관리자가 프리셋을 수정하면 모든 구독자에게 즉시 반영된다.
    onChange({
      createPersona: false,
      selectedPresetId: preset.id,
      selectedPersonaId: undefined,
      personaName: preset.label,
      personaDescription: preset.desc,
      personaSummaryStyle: preset.summaryStyle,
      personaTargetAudience: preset.targetAudience,
      personaPrompt: preset.prompt
    });
  }

  function handleSavedPersonaSelect(persona: Persona) {
    setEditingPersonaId(null);
    onChange({
      createPersona: false,
      selectedPersonaId: persona.id,
      personaName: persona.name,
      personaDescription: persona.description ?? "",
      personaSummaryStyle: persona.summaryStyle ?? "",
      personaTargetAudience: persona.targetAudience ?? "",
      personaPrompt: persona.systemPrompt
    });
  }

  // 커스텀 뷰 진입 시 현재 선택 상태를 백업해 두고 "뒤로" 시 복원한다.
  function snapshotSelection(): Partial<QuickSetupForm> {
    return {
      createPersona: form.createPersona,
      selectedPersonaId: form.selectedPersonaId,
      personaName: form.personaName,
      personaDescription: form.personaDescription,
      personaSummaryStyle: form.personaSummaryStyle,
      personaTargetAudience: form.personaTargetAudience,
      personaPrompt: form.personaPrompt
    };
  }

  function handleEnterCustom() {
    selectionBeforeCustomRef.current = snapshotSelection();
    setViewMode("custom");
    onCustomEditing?.(true);
    setEditingPersonaId(null);
    onChange({
      createPersona: true,
      selectedPersonaId: undefined,
      personaName: form.personaName.trim() || "내 스타일"
    });
    requestAnimationFrame(() => {
      customAreaRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start"
      });
    });
  }

  function handleBackToSelect() {
    setViewMode("select");
    onCustomEditing?.(false);
    setEditingPersonaId(null);
    if (selectionBeforeCustomRef.current) {
      onChange(selectionBeforeCustomRef.current);
      selectionBeforeCustomRef.current = null;
    }
  }

  function handleEditPersona(persona: Persona) {
    selectionBeforeCustomRef.current = snapshotSelection();
    setViewMode("custom");
    onCustomEditing?.(true);
    setEditingPersonaId(persona.id);
    onChange({
      createPersona: true,
      selectedPersonaId: undefined,
      personaName: persona.name,
      personaDescription: persona.description ?? "",
      personaSummaryStyle: persona.summaryStyle ?? "",
      personaTargetAudience: persona.targetAudience ?? "",
      personaPrompt: persona.systemPrompt
    });
  }

  async function confirmDeletePersona() {
    if (!pendingDeletePersona || deleting) return;
    setDeleting(true);
    try {
      await svcApi.delete(pendingDeletePersona.id);
      if (selectedPresetKey === `saved-${pendingDeletePersona.id}`) {
        handlePresetSelect(effectivePresets[0]);
      }
      toast.success("내 스타일을 삭제했어요");
      setPendingDeletePersona(null);
      onPersonaSaved();
    } catch (err) {
      // 409 Conflict 는 구독에서 사용 중인 경우 — 구체적 안내 메시지로 전환
      const isConflict =
        err instanceof Error && "status" in err && (err as { status: number }).status === 409;
      toast.error(
        isConflict
          ? "이 스타일을 사용 중인 구독이 있어서 삭제할 수 없어요. 구독을 먼저 변경해 주세요."
          : userFriendlyMessage(err, "삭제하지 못했어요")
      );
    } finally {
      setDeleting(false);
    }
  }

  async function handleSaveCustom() {
    const name = form.personaName.trim();
    const prompt = form.personaPrompt.trim();
    if (!name || !prompt) {
      toast.error("스타일 이름과 AI 지시문을 모두 입력해 주세요");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name,
        description: form.personaDescription.trim() || null,
        systemPrompt: prompt,
        summaryStyle: form.personaSummaryStyle.trim() || null,
        targetAudience: form.personaTargetAudience.trim() || null,
        maxItems: 5,
        language: "ko" as const
      };
      if (editingPersonaId) {
        const persona = await svcApi.update(editingPersonaId, payload);
        handleSavedPersonaSelect(persona);
        toast.success("내 스타일을 수정했어요");
      } else {
        const persona = await svcApi.create(payload);
        handleSavedPersonaSelect(persona);
        toast.success("내 스타일을 저장했어요");
      }
      setViewMode("select");
      onCustomEditing?.(false);
      setEditingPersonaId(null);
      selectionBeforeCustomRef.current = null;
      onPersonaSaved();
    } catch (err) {
      toast.error(userFriendlyMessage(err, "저장하지 못했어요"));
    } finally {
      setSaving(false);
    }
  }

  if (viewMode === "custom") {
    return (
      <PersonaCustomEditor
        ref={customAreaRef}
        form={form}
        onChange={onChange}
        isEditing={Boolean(editingPersonaId)}
        saving={saving}
        onBack={handleBackToSelect}
        onSave={handleSaveCustom}
        disabled={disabled}
      />
    );
  }

  return (
    <>
      <div className="space-y-4 py-2">
        <div>
          <h3 className="text-sm font-semibold mb-1">어떤 스타일로 요약할까요?</h3>
          <p className="text-xs text-muted-foreground">Slack에 전달되는 요약 형식을 골라보세요.</p>
        </div>

        <PersonaPresetGrid
          presets={effectivePresets}
          selectedKey={selectedPresetKey}
          onSelect={handlePresetSelect}
          disabled={disabled}
        />

        {currentPreset && currentPreset.previewTitle && <PersonaPreview preset={currentPreset} />}

        <SavedPersonaList
          personas={savedPersonas}
          labelById={personaLabelById}
          selectedKey={selectedPresetKey}
          onSelect={handleSavedPersonaSelect}
          onEdit={handleEditPersona}
          onRequestDelete={setPendingDeletePersona}
          disabled={disabled}
        />

        <button
          type="button"
          className="w-full p-3 text-left rounded-lg border border-dashed border-border hover:bg-muted transition-colors space-y-0.5"
          onClick={handleEnterCustom}
          disabled={disabled}
        >
          <div className="text-sm font-medium">직접 만들기</div>
          <div className="text-xs text-muted-foreground">
            현재 스타일을 바탕으로 자유롭게 조정할 수 있어요.
          </div>
        </button>

        <p className="text-xs text-muted-foreground">나중에 설정에서 언제든 바꿀 수 있어요.</p>
      </div>

      <PersonaDeleteDialog
        persona={pendingDeletePersona}
        deleting={deleting}
        onClose={() => setPendingDeletePersona(null)}
        onConfirm={confirmDeletePersona}
      />
    </>
  );
}
