import { useState, type KeyboardEvent } from "react";
import type { QuickSetupForm } from "./model/quickSetupTypes";
import { cn } from "@/utils/cn";

interface QuickSetupStepDetailsProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled: boolean;
  isUserMode?: boolean;
  validationMessage?: string | null;
  validationTarget?: "delivery-day-first" | null;
}

const ALL_DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
const DAY_LABELS: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일"
};
const ALLOWED_HOURS = [8, 12, 18] as const;

function hourLabel(h: number): string {
  if (h === 0) return "자정";
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

export function QuickSetupStepDetails({
  form,
  onChange,
  disabled,
  isUserMode,
  validationMessage,
  validationTarget
}: QuickSetupStepDetailsProps) {
  const scheduleValidationMessage = validationTarget === "delivery-day-first" ? validationMessage : null;

  return (
    <div className="space-y-4 py-2">
      <div>
        <h3 className="text-sm font-semibold mb-1">뉴스 수신 옵션을 설정해 주세요</h3>
        <p className="text-xs text-muted-foreground">하루에 받을 뉴스 수와 발송 시간을 정해요</p>
      </div>

      <NewsOptionsSection
        form={form}
        onChange={onChange}
        disabled={disabled}
        showExcludeKeywords={Boolean(isUserMode)}
      />

      {isUserMode ? (
        <DeliveryScheduleSection
          form={form}
          onChange={onChange}
          disabled={disabled}
          validationMessage={scheduleValidationMessage}
        />
      ) : (
        <p className="text-xs text-muted-foreground">
          관리자 빠른 세팅에서는 하루 최대 뉴스 수만 바로 반영돼요. 상세 수신 옵션은 구독 설정에서 조정할 수 있어요.
        </p>
      )}
    </div>
  );
}

function NewsOptionsSection({
  form,
  onChange,
  disabled,
  showExcludeKeywords
}: {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled: boolean;
  showExcludeKeywords: boolean;
}) {
  const [newKw, setNewKw] = useState("");

  function addKeyword() {
    const kw = newKw.trim();
    if (!kw) return;
    if (form.excludeKeywords.includes(kw)) {
      setNewKw("");
      return;
    }
    onChange({ excludeKeywords: [...form.excludeKeywords, kw] });
    setNewKw("");
  }

  function removeKeyword(kw: string) {
    onChange({ excludeKeywords: form.excludeKeywords.filter((k) => k !== kw) });
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      addKeyword();
    }
  }

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <label className="text-xs font-medium">하루 최대 뉴스 수</label>
        <div className="flex gap-1.5">
          {[1, 3, 5].map((n) => (
            <button
              key={n}
              type="button"
              className={cn(
                "px-2.5 py-1 text-xs rounded-full border transition-colors",
                Number(form.maxItems) === n
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-background border-border hover:bg-muted"
              )}
              aria-pressed={Number(form.maxItems) === n}
              onClick={() => onChange({ maxItems: n })}
              disabled={disabled}
            >
              {n}건
            </button>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">적게 받으면 핵심만, 많이 받으면 폭넓게 볼 수 있어요</p>
      </div>

      {showExcludeKeywords && (
        <div className="space-y-1.5">
          <label className="text-xs font-medium">제외 키워드</label>
          {form.excludeKeywords.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {form.excludeKeywords.map((kw) => (
                <span
                  key={kw}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-primary/10 text-primary border border-primary/20"
                >
                  {kw}
                  <button
                    type="button"
                    onClick={() => removeKeyword(kw)}
                    disabled={disabled}
                    className="hover:opacity-70"
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
          <div className="flex gap-2">
            <input
              type="text"
              className="flex-1 px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
              value={newKw}
              onChange={(e) => setNewKw(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="키워드 입력 후 Enter"
              disabled={disabled}
            />
            <button
              type="button"
              className="px-3 py-2 text-xs border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
              onClick={addKeyword}
              disabled={disabled || !newKw.trim()}
            >
              추가
            </button>
          </div>
          <p className="text-xs text-muted-foreground">이 키워드가 포함된 뉴스는 자동으로 걸러져요</p>
        </div>
      )}
    </div>
  );
}

function DeliveryScheduleSection({
  form,
  onChange,
  disabled,
  validationMessage
}: {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled: boolean;
  validationMessage?: string | null;
}) {
  function handlePresetChange(preset: "WEEKDAYS" | "EVERYDAY" | "CUSTOM") {
    if (preset === "WEEKDAYS") {
      onChange({ deliveryPreset: preset, deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"] });
    } else if (preset === "EVERYDAY") {
      onChange({ deliveryPreset: preset, deliveryDays: [...ALL_DAYS] });
    } else {
      onChange({ deliveryPreset: preset });
    }
  }

  function toggleDay(day: string) {
    const next = form.deliveryDays.includes(day)
      ? form.deliveryDays.filter((d) => d !== day)
      : [...form.deliveryDays, day];
    onChange({ deliveryDays: next, deliveryPreset: "CUSTOM" });
  }

  return (
    <div className="space-y-3 pt-2 border-t">
      <h4 className="text-xs font-semibold">발송 시간</h4>

      <div className="space-y-1.5">
        <label className="text-xs font-medium">받고 싶은 요일</label>
        <div className="flex gap-1.5">
          {(["WEEKDAYS", "EVERYDAY", "CUSTOM"] as const).map((p) => (
            <button
              key={p}
              type="button"
              className={cn(
                "flex-1 py-1.5 px-2 text-xs rounded-md border transition-colors",
                form.deliveryPreset === p
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-background border-border hover:bg-muted"
              )}
              aria-pressed={form.deliveryPreset === p}
              onClick={() => handlePresetChange(p)}
              disabled={disabled}
            >
              {p === "WEEKDAYS" ? "평일만" : p === "EVERYDAY" ? "매일" : "직접 선택"}
            </button>
          ))}
        </div>

        {form.deliveryPreset === "CUSTOM" && (
          <div className="flex gap-1">
            {ALL_DAYS.map((d) => (
              <button
                key={d}
                type="button"
                className={cn(
                  "flex-1 py-1.5 text-xs rounded-md border transition-colors",
                  form.deliveryDays.includes(d)
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-background border-border hover:bg-muted"
                )}
                aria-pressed={form.deliveryDays.includes(d)}
                onClick={() => toggleDay(d)}
                data-focus-target={d === ALL_DAYS[0] ? "delivery-day-first" : undefined}
                disabled={disabled}
              >
                {DAY_LABELS[d]}
              </button>
            ))}
          </div>
        )}
        {validationMessage && (
          <p className="text-xs text-destructive" role="alert">
            {validationMessage}
          </p>
        )}
      </div>

      <div className="space-y-1.5">
        <label className="text-xs font-medium">받고 싶은 시간</label>
        <div className="flex gap-1.5">
          {ALLOWED_HOURS.map((h) => (
            <button
              key={h}
              type="button"
              className={cn(
                "flex-1 py-1.5 px-2 text-xs rounded-md border transition-colors",
                form.deliveryHour === h
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-background border-border hover:bg-muted"
              )}
              aria-pressed={form.deliveryHour === h}
              onClick={() => onChange({ deliveryHour: h })}
              disabled={disabled}
            >
              {hourLabel(h)}
            </button>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">
          채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
        </p>
      </div>
    </div>
  );
}
