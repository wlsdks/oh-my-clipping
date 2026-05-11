import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

/** cron 프리셋 정의 */
const CRON_PRESETS = [
  { label: "평일 오전 9시", value: "0 9 * * 1-5" },
  { label: "매일 오전 9시", value: "0 9 * * *" },
  { label: "직접 입력", value: "__custom__" },
] as const;

/** 5필드 cron(분 시 일 월 요일) 형식인지 가벼운 사전 검증을 수행한다.
 * 정확한 검증은 백엔드의 CronExpression.parse가 담당하지만,
 * 프론트에서 명백한 오타는 즉시 사용자에게 안내해 백엔드 왕복을 줄인다. */
const CRON_FIELD_RE = /^[0-9*,\-/?LW#]+$/;
function isLikelyCron(value: string): boolean {
  const fields = value.trim().split(/\s+/);
  if (fields.length !== 5) return false;
  return fields.every((f) => CRON_FIELD_RE.test(f));
}

const schema = z.object({
  slackAutoDigestEnabled: z.boolean(),
  slackDigestCron: z
    .string()
    .min(1, "스케줄 표현식을 입력해 주세요")
    .refine(
      isLikelyCron,
      "분 시 일 월 요일 5개 필드로 입력해 주세요 (예: 0 9 * * 1-5)",
    ),
  slackAutoDigestMaxItems: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1건 이상 설정해 주세요")
    .max(5, "최대 5건까지 설정할 수 있어요"),
  slackAutoDigestUnsentOnly: z.boolean(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
  slackConnected: boolean;
}

/** cron 표현식이 알려진 프리셋에 해당하는지 여부를 반환한다 */
function isKnownPreset(cron: string): boolean {
  return CRON_PRESETS.some((p) => p.value !== "__custom__" && p.value === cron);
}

/** 접힌 상태에서 보여줄 요약 문자열을 생성한다 */
function buildSummary(settings: RuntimeSettings): string {
  const status = settings.slackAutoDigestEnabled ? "켜짐" : "꺼짐";
  const cronLabel =
    CRON_PRESETS.find((p) => p.value === settings.slackDigestCron)?.label ??
    settings.slackDigestCron;
  return `${status} · ${cronLabel} · 최대 ${settings.slackAutoDigestMaxItems}건`;
}

export function AutoDigestSettingsCard({ settings, isSaving, onSave, slackConnected }: Props) {
  const { register, handleSubmit, setValue, watch, reset, formState: { isDirty, errors } } =
    useForm<FormValues>({
      resolver: zodResolver(schema),
      defaultValues: {
        slackAutoDigestEnabled: settings.slackAutoDigestEnabled,
        slackDigestCron: settings.slackDigestCron,
        slackAutoDigestMaxItems: settings.slackAutoDigestMaxItems,
        slackAutoDigestUnsentOnly: settings.slackAutoDigestUnsentOnly,
      },
    });

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다 (커스텀 모드 여부는 cron 값으로 derive)
  useEffect(() => {
    reset({
      slackAutoDigestEnabled: settings.slackAutoDigestEnabled,
      slackDigestCron: settings.slackDigestCron,
      slackAutoDigestMaxItems: settings.slackAutoDigestMaxItems,
      slackAutoDigestUnsentOnly: settings.slackAutoDigestUnsentOnly,
    });
  }, [settings, reset]);

  const currentCron = watch("slackDigestCron");
  // 알려진 프리셋이 아니면 직접 입력 모드. 빈 문자열도 커스텀 모드로 간주한다.
  const showCustomInput = !isKnownPreset(currentCron);

  function handlePresetClick(presetValue: string) {
    if (presetValue === "__custom__") {
      // 직접 입력 칩 클릭 시 cron을 비워서 사용자가 새 값을 입력하도록 유도한다.
      // 단, 이미 커스텀 값이면 그대로 유지해 사용자의 입력을 보존한다.
      if (!showCustomInput) {
        setValue("slackDigestCron", "", { shouldDirty: true });
      }
      return;
    }
    // 프리셋 선택 시 cron 값을 설정하고 커스텀 입력은 자동으로 숨겨진다.
    setValue("slackDigestCron", presetValue, { shouldDirty: true });
  }

  function onSubmit(values: FormValues) {
    onSave({
      slackAutoDigestEnabled: values.slackAutoDigestEnabled,
      slackDigestCron: values.slackDigestCron,
      slackAutoDigestMaxItems: values.slackAutoDigestMaxItems,
      slackAutoDigestUnsentOnly: values.slackAutoDigestUnsentOnly,
    });
  }

  const content = (
    <CollapsibleSection
      title="자동 발송"
      description="Slack으로 자동 발송되는 다이제스트의 스케줄과 조건을 설정해요"
      summary={buildSummary(settings)}
      defaultOpen={false}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-4">
        {/* 자동 발송 활성화 토글 */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="slackAutoDigestEnabled">자동 발송 사용</Label>
            <p className="text-xs text-muted-foreground">
              스케줄에 따라 Slack에 자동으로 뉴스 다이제스트를 발송해요.
            </p>
          </div>
          <Switch
            id="slackAutoDigestEnabled"
            checked={watch("slackAutoDigestEnabled")}
            onCheckedChange={(v) => setValue("slackAutoDigestEnabled", v, { shouldDirty: true })}
          />
        </div>

        {/* Cron 프리셋 칩 */}
        <div className="space-y-2">
          <Label>발송 스케줄</Label>
          <div className="flex flex-wrap gap-2">
            {CRON_PRESETS.map((preset) => {
              // 직접 입력 칩은 showCustomInput일 때 활성화, 나머지는 cron 값 일치 여부로 판단한다
              const isActive =
                preset.value === "__custom__"
                  ? showCustomInput
                  : currentCron === preset.value;
              return (
                <button
                  key={preset.value}
                  type="button"
                  className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                    isActive
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-muted/80"
                  }`}
                  onClick={() => handlePresetClick(preset.value)}
                >
                  {preset.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* 직접 입력 모드이거나 커스텀 cron 값인 경우 텍스트 입력을 노출한다 */}
        {showCustomInput && (
          <div className="space-y-1">
            <Label htmlFor="slackDigestCron">스케줄 표현식</Label>
            <Input id="slackDigestCron" {...register("slackDigestCron")} />
            <p className="text-xs text-muted-foreground">
              예: <code className="font-mono bg-muted px-1 rounded">0 9 * * 1-5</code> -- 평일
              오전 9시 발송.
            </p>
            {errors.slackDigestCron && (
              <p className="text-xs text-destructive">{errors.slackDigestCron.message}</p>
            )}
          </div>
        )}

        {/* 자동 발송 최대 기사 수 */}
        <div className="space-y-1">
          <Label htmlFor="slackAutoDigestMaxItems">자동 발송 최대 기사 수</Label>
          <Input
            id="slackAutoDigestMaxItems"
            type="number"
            {...register("slackAutoDigestMaxItems")}
          />
          <p className="text-xs text-muted-foreground">
            자동 발송 시 포함할 기사 수 상한이에요. 기본 수집 설정과 별도로 적용돼요.
          </p>
          {errors.slackAutoDigestMaxItems && (
            <p className="text-xs text-destructive">{errors.slackAutoDigestMaxItems.message}</p>
          )}
        </div>

        {/* 미발송 항목만 포함 토글 */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="slackAutoDigestUnsentOnly">미발송 항목만 포함</Label>
            <p className="text-xs text-muted-foreground">
              이미 발송된 기사는 제외하고 새 기사만 발송해요.
            </p>
          </div>
          <Switch
            id="slackAutoDigestUnsentOnly"
            checked={watch("slackAutoDigestUnsentOnly")}
            onCheckedChange={(v) =>
              setValue("slackAutoDigestUnsentOnly", v, { shouldDirty: true })
            }
          />
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );

  // Slack 미연결 시 비활성 래퍼
  if (!slackConnected) {
    return (
      <div className="relative">
        <div className="opacity-50 pointer-events-none">{content}</div>
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="flex items-center gap-2 rounded-lg bg-[var(--status-warning-bg)] px-4 py-2.5 text-sm text-[var(--status-warning-text)] shadow-sm">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            Slack 연결 후 사용할 수 있어요
          </div>
        </div>
      </div>
    );
  }

  return content;
}
