import { useEffect, useRef, useState } from "react";
import { Pencil } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/utils/cn";
import { userFriendlyMessage } from "@/shared/lib/httpError";

interface ThresholdInlineEditorProps {
  categoryId: string;
  currentValue: number | null;
  onSave: (value: number | null) => Promise<void>;
}

// 자동 승인 임계값의 유효 범위 (0.0 ~ 1.0, step 0.05)
const MIN_THRESHOLD = 0;
const MAX_THRESHOLD = 1;
const THRESHOLD_STEP = 0.05;

/**
 * 자동 승인 임계값을 인라인에서 편집하는 컴포넌트.
 *
 * 표시 상태에서는 현재 값(또는 "설정 안 됨")을 보여주고, 편집 아이콘 클릭 시
 * number 인풋으로 전환한다. Enter/blur 로 저장, Escape 로 취소.
 *
 * - Optimistic update: 저장 요청 직후 UI 가 새 값을 즉시 반영한다.
 * - 실패 시: 원래 값으로 롤백 + 한국어 toast (백엔드 영어 메시지는 `userFriendlyMessage` 경유).
 * - 유효성: 0.0 ~ 1.0 범위 밖 입력은 저장하지 않고 인풋 border 에 error 표시.
 */
export function ThresholdInlineEditor({
  categoryId,
  currentValue,
  onSave,
}: ThresholdInlineEditorProps) {
  // 표시용 값 — optimistic update 로 부모 refetch 이전에도 즉시 반영
  const [displayValue, setDisplayValue] = useState<number | null>(currentValue);
  const [editing, setEditing] = useState(false);
  // 편집 중 사용자가 타이핑하는 문자열 (빈 문자열도 허용 — "설정 안 됨" 저장 시)
  const [draft, setDraft] = useState<string>("");
  const [saving, setSaving] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);

  // 부모가 전달하는 currentValue 가 refetch 등으로 갱신되면 표시값 동기화
  useEffect(() => {
    setDisplayValue(currentValue);
  }, [currentValue]);

  // 편집 모드 진입 시 인풋 포커스 + 기존 값 선택
  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [editing]);

  // draft 문자열을 숫자로 파싱. 빈 문자열 → null, 범위 밖 → "invalid"
  function parseDraft(value: string): number | null | "invalid" {
    const trimmed = value.trim();
    if (trimmed === "") return null;
    const num = Number(trimmed);
    if (Number.isNaN(num)) return "invalid";
    if (num < MIN_THRESHOLD || num > MAX_THRESHOLD) return "invalid";
    return num;
  }

  const parsed = parseDraft(draft);
  const isInvalid = parsed === "invalid";

  function startEdit() {
    // 현재값을 편집 초기값으로 세팅 (소숫점 2자리, null 은 빈 문자열)
    setDraft(displayValue === null ? "" : displayValue.toFixed(2));
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
    setDraft("");
  }

  async function commitEdit() {
    if (saving) return;
    // 유효하지 않으면 저장 무시 — 인풋 border 에 error 표시로 안내
    if (isInvalid) return;

    const nextValue = parsed as number | null;
    // 값이 그대로이면 API 호출 없이 편집만 종료
    if (nextValue === displayValue) {
      cancelEdit();
      return;
    }

    // Optimistic update — 부모 응답 전에 즉시 반영
    const previousValue = displayValue;
    setDisplayValue(nextValue);
    setSaving(true);

    try {
      await onSave(nextValue);
      toast.success("임계값이 저장되었습니다");
    } catch (error) {
      // 실패 시 이전 값으로 롤백 — 사용자에게는 원래 값이 유지된 것처럼 보임
      setDisplayValue(previousValue);
      // 영어 백엔드 메시지는 한국어로 변환 후 토스트
      toast.error(userFriendlyMessage(error, "임계값을 저장하지 못했어요"));
    } finally {
      setSaving(false);
      // 성공/실패 무관하게 편집 모드는 종료 (재시도는 display 를 다시 클릭)
      setEditing(false);
      setDraft("");
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      void commitEdit();
    } else if (e.key === "Escape") {
      e.preventDefault();
      cancelEdit();
    }
  }

  if (editing) {
    return (
      <span className="inline-flex items-center gap-2" data-category-id={categoryId}>
        <input
          ref={inputRef}
          type="number"
          min={MIN_THRESHOLD}
          max={MAX_THRESHOLD}
          step={THRESHOLD_STEP}
          value={draft}
          disabled={saving}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={() => void commitEdit()}
          onKeyDown={handleKeyDown}
          aria-label="자동 승인 임계값"
          aria-invalid={isInvalid || undefined}
          data-testid="threshold-input"
          className={cn(
            "w-20 rounded-md border bg-background px-2 py-1 text-sm tabular-nums",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
            isInvalid
              ? "border-destructive text-destructive focus-visible:ring-destructive"
              : "border-input",
          )}
        />
        {saving && <span className="text-xs text-muted-foreground">저장 중…</span>}
      </span>
    );
  }

  return (
    <button
      type="button"
      onClick={startEdit}
      data-testid="threshold-display"
      data-category-id={categoryId}
      className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm tabular-nums text-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      aria-label={
        displayValue === null
          ? "자동 승인 임계값 설정"
          : `자동 승인 임계값 편집 (현재 ${displayValue.toFixed(2)})`
      }
    >
      <span className={displayValue === null ? "text-muted-foreground" : undefined}>
        {displayValue === null ? "설정 안 됨" : displayValue.toFixed(2)}
      </span>
      <Pencil className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
    </button>
  );
}
