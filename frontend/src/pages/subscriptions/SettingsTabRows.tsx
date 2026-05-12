import { useState, useRef, useEffect } from "react";
import { ChevronDown, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";
import { cn } from "@/utils/cn";
import { MAX_ITEMS_PRESETS } from "./model/constants";
import { CATEGORY_PURPOSE_LABELS, type CategoryPurpose } from "@/types/category";

/**
 * SettingsTab 의 편집 가능/읽기 전용 행 primitive 모음.
 * 섹션 컴포넌트(Basic/Metadata) 들이 공유해서 사용한다.
 */

/* ── 읽기 전용 행 ── */

/**
 * 라벨 + 값 만 보이는 한 줄 정보 행.
 * 키워드 요약처럼 편집 대상이 아닌 데이터를 노출할 때 쓴다.
 */
export function ReadonlyRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 px-3 py-2.5">
      <span className="text-xs text-muted-foreground w-16 shrink-0">{label}</span>
      <span className="text-sm text-foreground">{value}</span>
    </div>
  );
}

/* ── 텍스트 편집 Popover 행 ── */

interface EditableTextRowProps {
  label: string;
  value: string;
  rawValue?: string;
  popoverLabel: string;
  placeholder: string;
  isWorking: boolean;
  onSave: (value: string) => void;
}

/**
 * 한 줄 텍스트(이름, 채널 ID 등) 를 Popover 에서 편집하는 행.
 * Enter 로 저장, Esc 로 취소. 빈 값을 "이름" 라벨에 저장하려 하면 무시된다.
 */
export function EditableTextRow({
  label,
  value,
  rawValue,
  popoverLabel,
  placeholder,
  isWorking,
  onSave,
}: EditableTextRowProps) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [saved, setSaved] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const focusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (open) {
      setDraft(rawValue ?? value);
      setSaved(false);
      focusTimerRef.current = setTimeout(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
        focusTimerRef.current = null;
      }, 50);
    }
    return () => {
      if (focusTimerRef.current) clearTimeout(focusTimerRef.current);
    };
  }, [open, value, rawValue]);

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    };
  }, []);

  function handleSave() {
    const trimmed = draft.trim();
    if (!trimmed && label === "이름") return;
    onSave(trimmed);
    setSaved(true);
    closeTimerRef.current = setTimeout(() => {
      setOpen(false);
      closeTimerRef.current = null;
    }, 800);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={isWorking}>
        <button
          type="button"
          className={cn(
            "flex items-center gap-3 px-3 py-2.5 w-full text-left",
            "transition-colors hover:bg-muted/50",
            "group cursor-pointer"
          )}
        >
          <span className="text-xs text-muted-foreground w-16 shrink-0">{label}</span>
          <span className="text-sm text-foreground flex-1">{value}</span>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground/0 group-hover:text-muted-foreground transition-colors" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] p-3">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">
            {popoverLabel}
          </label>
          <input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={placeholder}
            className={cn(
              "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm",
              "focus:outline-none focus:ring-2 focus:ring-ring"
            )}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSave();
              if (e.key === "Escape") setOpen(false);
            }}
          />
          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-muted-foreground">
              Enter로 확인 · Esc로 취소
            </span>
            {saved && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--status-success-text)] animate-in fade-in duration-200">
                <Check className="h-3 w-3" />
                저장됨
              </span>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

/* ── Pill 선택 Popover 행 ── */

interface EditablePillRowProps {
  label: string;
  value: number;
  isWorking: boolean;
  onSave: (value: number) => void;
}

/**
 * 숫자 프리셋(최대 기사 수) 중 하나를 선택하는 Pill 행.
 * 현재값과 같은 항목을 다시 클릭하면 저장 없이 닫힌다.
 */
export function EditablePillRow({ label, value, isWorking, onSave }: EditablePillRowProps) {
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState(false);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function handleSelect(n: number) {
    if (n === value) {
      setOpen(false);
      return;
    }
    onSave(n);
    setSaved(true);
    closeTimerRef.current = setTimeout(() => {
      setOpen(false);
      closeTimerRef.current = null;
    }, 800);
  }

  useEffect(() => {
    if (open) setSaved(false);
  }, [open]);

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    };
  }, []);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={isWorking}>
        <button
          type="button"
          className={cn(
            "flex items-center gap-3 px-3 py-2.5 w-full text-left",
            "transition-colors hover:bg-muted/50",
            "group cursor-pointer"
          )}
        >
          <span className="text-xs text-muted-foreground w-16 shrink-0">{label}</span>
          <span className="text-sm text-foreground flex-1">{value}건</span>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground/0 group-hover:text-muted-foreground transition-colors" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] p-3">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">
            최대 기사 수
          </label>
          <div className="flex gap-1.5 flex-wrap">
            {MAX_ITEMS_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={cn(
                  "rounded-full px-3.5 py-1.5 text-sm font-medium border transition-colors",
                  value === preset
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-background text-muted-foreground border-input hover:border-foreground/30"
                )}
                onClick={() => handleSelect(preset)}
              >
                {preset}건
              </button>
            ))}
          </div>
          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-muted-foreground">클릭으로 선택</span>
            {saved && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--status-success-text)] animate-in fade-in duration-200">
                <Check className="h-3 w-3" />
                저장됨
              </span>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

/* ── Purpose Pill 선택 행 ── */

/**
 * V123(Phase 3 PR1): purpose 선택 행.
 * null 이면 "선택되지 않음" 표시, 선택/해제 모두 한 번의 클릭으로 가능.
 */
interface EditablePurposeRowProps {
  value: CategoryPurpose | null;
  isWorking: boolean;
  onSave: (next: CategoryPurpose | null) => void;
}

export function EditablePurposeRow({ value, isWorking, onSave }: EditablePurposeRowProps) {
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState(false);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (open) setSaved(false);
  }, [open]);

  const currentLabel = value ? CATEGORY_PURPOSE_LABELS[value] : "—";
  const purposeValues = Object.keys(CATEGORY_PURPOSE_LABELS) as CategoryPurpose[];

  function handleSelect(next: CategoryPurpose | null) {
    if (next === value) {
      setOpen(false);
      return;
    }
    onSave(next);
    setSaved(true);
    closeTimerRef.current = setTimeout(() => {
      setOpen(false);
      closeTimerRef.current = null;
    }, 800);
  }

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    };
  }, []);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={isWorking}>
        <button
          type="button"
          className={cn(
            "flex items-center gap-3 px-3 py-2.5 w-full text-left",
            "transition-colors hover:bg-muted/50",
            "group cursor-pointer"
          )}
        >
          <span className="text-xs text-muted-foreground w-16 shrink-0">목적</span>
          <span className="text-sm text-foreground flex-1">{currentLabel}</span>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground/0 group-hover:text-muted-foreground transition-colors" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] p-3">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">
            구독 목적
          </label>
          <div className="flex gap-1.5 flex-wrap">
            {purposeValues.map((p) => (
              <button
                key={p}
                type="button"
                className={cn(
                  "rounded-full px-3 py-1.5 text-sm font-medium border transition-colors",
                  value === p
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-background text-muted-foreground border-input hover:border-foreground/30"
                )}
                onClick={() => handleSelect(p)}
              >
                {CATEGORY_PURPOSE_LABELS[p]}
              </button>
            ))}
          </div>
          {value !== null && (
            <button
              type="button"
              className="self-start text-[11px] text-muted-foreground hover:text-foreground transition-colors"
              onClick={() => handleSelect(null)}
            >
              선택 해제
            </button>
          )}
          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-muted-foreground">클릭으로 선택</span>
            {saved && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--status-success-text)] animate-in fade-in duration-200">
                <Check className="h-3 w-3" />
                저장됨
              </span>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

/* ── Textarea 편집 Popover 행 ── */

interface EditableTextareaRowProps {
  label: string;
  value: string;
  popoverLabel: string;
  placeholder: string;
  isWorking: boolean;
  onSave: (value: string | null) => void;
}

/**
 * V123(Phase 3 PR1): 긴 텍스트 편집 행.
 * 배경/문제 같은 자유 텍스트에 쓴다. 저장 시 빈 값이면 null 로 초기화된다.
 */
export function EditableTextareaRow({
  label,
  value,
  popoverLabel,
  placeholder,
  isWorking,
  onSave,
}: EditableTextareaRowProps) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [saved, setSaved] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const focusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (open) {
      setDraft(value);
      setSaved(false);
      focusTimerRef.current = setTimeout(() => {
        textareaRef.current?.focus();
        focusTimerRef.current = null;
      }, 50);
    }
    return () => {
      if (focusTimerRef.current) clearTimeout(focusTimerRef.current);
    };
  }, [open, value]);

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    };
  }, []);

  function handleSave() {
    const trimmed = draft.trim();
    // 빈 문자열은 서비스 레이어에서 null 로 초기화로 해석된다.
    onSave(trimmed.length === 0 ? "" : trimmed);
    setSaved(true);
    closeTimerRef.current = setTimeout(() => {
      setOpen(false);
      closeTimerRef.current = null;
    }, 800);
  }

  // 요약 표시값: 긴 문장은 앞 30자만 노출.
  const summary = value.length > 30 ? `${value.slice(0, 30)}…` : value || "—";

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={isWorking}>
        <button
          type="button"
          className={cn(
            "flex items-center gap-3 px-3 py-2.5 w-full text-left",
            "transition-colors hover:bg-muted/50",
            "group cursor-pointer"
          )}
        >
          <span className="text-xs text-muted-foreground w-16 shrink-0">{label}</span>
          <span className="text-sm text-foreground flex-1 truncate">{summary}</span>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground/0 group-hover:text-muted-foreground transition-colors" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[360px] p-3">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">
            {popoverLabel}
          </label>
          <textarea
            ref={textareaRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={placeholder}
            rows={4}
            className={cn(
              "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm resize-none",
              "focus:outline-none focus:ring-2 focus:ring-ring"
            )}
          />
          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-muted-foreground">Cmd/Ctrl + Enter 로 저장</span>
            {saved && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--status-success-text)] animate-in fade-in duration-200">
                <Check className="h-3 w-3" />
                저장됨
              </span>
            )}
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button type="button" variant="ghost" size="sm" onClick={() => setOpen(false)}>
              취소
            </Button>
            <Button type="button" size="sm" onClick={handleSave}>
              저장
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
