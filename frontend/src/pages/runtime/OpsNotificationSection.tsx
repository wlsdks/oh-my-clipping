import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

/* ── 상수 ── */

const OPS_PROFILE_OPTIONS = [
  { value: "FULL", label: "FULL — 모든 성공/실패 알림" },
  { value: "BATCHED", label: "BATCHED — 시간당 배치 + 실패" },
  { value: "CRITICAL_ONLY", label: "CRITICAL_ONLY — 실패/리포트만" },
] as const;

const DAY_OPTIONS = [
  { value: "MONDAY", label: "월요일" },
  { value: "TUESDAY", label: "화요일" },
  { value: "WEDNESDAY", label: "수요일" },
  { value: "THURSDAY", label: "목요일" },
  { value: "FRIDAY", label: "금요일" },
  { value: "SATURDAY", label: "토요일" },
  { value: "SUNDAY", label: "일요일" },
] as const;

/* ── 스키마 ── */

const schema = z.object({
  opsNotificationProfile: z.enum(["FULL", "BATCHED", "CRITICAL_ONLY"]),
  opsDailyForecastHour: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(0, "0 이상이어야 해요")
    .max(23, "23 이하이어야 해요"),
  opsWeeklyReportDay: z.enum([
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
  ]),
  opsWeeklyReportHour: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(0, "0 이상이어야 해요")
    .max(23, "23 이하이어야 해요"),
  opsPipelineCooldownMinutes: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(0, "0 이상이어야 해요"),
  opsIncidentWindowMinutes: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1 이상이어야 해요"),
  opsIncidentThresholdCategories: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1 이상이어야 해요"),
  opsScheduleMissGraceMinutes: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(0, "0 이상이어야 해요"),
  opsBudgetWarnPct: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .min(0, "0 이상이어야 해요")
    .max(100, "100 이하이어야 해요"),
  opsBudgetCriticalPct: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .min(0, "0 이상이어야 해요")
    .max(100, "100 이하이어야 해요"),
  opsAdminBaseUrl: z
    .string()
    .max(500, "최대 500자까지 입력할 수 있어요")
    .refine((v) => v === "" || v.startsWith("https://"), "https://로 시작해야 해요"),
  opsSilentHoursEnabled: z.boolean(),
  opsRecoveryStreakThreshold: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1 이상이어야 해요"),
  opsLogsEnabled: z.boolean(),
});

type FormValues = z.infer<typeof schema>;

/* ── Props ── */

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

/* ── 요약 문자열 ── */
function buildSummary(settings: RuntimeSettings): string {
  if (!settings.opsLogsEnabled) return "⚠ Kill Switch 활성 — 알림 전체 비활성";
  const profile = settings.opsNotificationProfile ?? "FULL";
  const silent = settings.opsSilentHoursEnabled ? "Silent Hours 켜짐" : "Silent Hours 꺼짐";
  return `${profile} · ${silent}`;
}

/* ── 컴포넌트 ── */

export function OpsNotificationSection({ settings, isSaving, onSave }: Props) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { isDirty, errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: buildDefaultValues(settings),
  });

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다
  useEffect(() => {
    reset(buildDefaultValues(settings));
  }, [settings, reset]);

  function onSubmit(values: FormValues) {
    // opsAdminBaseUrl: 빈 문자열은 null로, 후행 슬래시는 제거
    const opsAdminBaseUrl = values.opsAdminBaseUrl === ""
      ? null
      : values.opsAdminBaseUrl.replace(/\/$/, "");

    onSave({
      opsNotificationProfile: values.opsNotificationProfile,
      opsDailyForecastHour: values.opsDailyForecastHour,
      opsWeeklyReportDay: values.opsWeeklyReportDay,
      opsWeeklyReportHour: values.opsWeeklyReportHour,
      opsPipelineCooldownMinutes: values.opsPipelineCooldownMinutes,
      opsIncidentWindowMinutes: values.opsIncidentWindowMinutes,
      opsIncidentThresholdCategories: values.opsIncidentThresholdCategories,
      opsScheduleMissGraceMinutes: values.opsScheduleMissGraceMinutes,
      opsBudgetWarnPct: values.opsBudgetWarnPct,
      opsBudgetCriticalPct: values.opsBudgetCriticalPct,
      opsAdminBaseUrl,
      opsSilentHoursEnabled: values.opsSilentHoursEnabled,
      opsRecoveryStreakThreshold: values.opsRecoveryStreakThreshold,
      opsLogsEnabled: values.opsLogsEnabled,
    });
  }

  const opsLogsEnabled = watch("opsLogsEnabled");

  return (
    <CollapsibleSection
      title="운영 알림 프로필"
      description="Slack 운영 알림 발송 방식과 스케줄을 설정해요"
      summary={buildSummary(settings)}
      defaultOpen={false}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-5">
        {/* Kill Switch — 최상단 경고 영역 */}
        <div className="flex items-start gap-3 rounded-md border border-destructive/20 bg-destructive/5 p-3">
          <Switch
            id="opsLogsEnabled"
            checked={opsLogsEnabled}
            onCheckedChange={(v) => setValue("opsLogsEnabled", v, { shouldDirty: true })}
          />
          <div>
            <Label htmlFor="opsLogsEnabled" className="text-destructive font-medium">
              Kill Switch (전체 알림 활성)
            </Label>
            <p className="text-xs text-muted-foreground mt-0.5">
              끄면 어떤 운영 알림도 발송되지 않아요. 비상 시에만 사용하세요.
            </p>
          </div>
        </div>

        {/* 알림 프로필 */}
        <div className="space-y-1.5">
          <Label htmlFor="opsNotificationProfile-trigger">알림 프로필</Label>
          <Select
            value={watch("opsNotificationProfile")}
            onValueChange={(v) =>
              setValue(
                "opsNotificationProfile",
                v as FormValues["opsNotificationProfile"],
                { shouldDirty: true }
              )
            }
          >
            <SelectTrigger id="opsNotificationProfile-trigger" aria-label="알림 프로필">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {OPS_PROFILE_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors.opsNotificationProfile && (
            <p className="text-xs text-destructive">{errors.opsNotificationProfile.message}</p>
          )}
        </div>

        {/* Silent Hours */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="opsSilentHoursEnabled">Silent Hours</Label>
            <p className="text-xs text-muted-foreground">
              22:00–08:00 KST 및 주말에는 알림을 억제해요.
            </p>
          </div>
          <Switch
            id="opsSilentHoursEnabled"
            checked={watch("opsSilentHoursEnabled")}
            onCheckedChange={(v) => setValue("opsSilentHoursEnabled", v, { shouldDirty: true })}
          />
        </div>

        {/* 일일 Forecast 시간 */}
        <div className="space-y-1">
          <Label htmlFor="opsDailyForecastHour">일일 Forecast 시간 (KST, 0–23)</Label>
          <Input
            id="opsDailyForecastHour"
            type="number"
            min={0}
            max={23}
            {...register("opsDailyForecastHour")}
          />
          {errors.opsDailyForecastHour && (
            <p className="text-xs text-destructive">{errors.opsDailyForecastHour.message}</p>
          )}
        </div>

        {/* 주간 리포트 요일 */}
        <div className="space-y-1.5">
          <Label htmlFor="opsWeeklyReportDay-trigger">주간 리포트 요일</Label>
          <Select
            value={watch("opsWeeklyReportDay")}
            onValueChange={(v) =>
              setValue(
                "opsWeeklyReportDay",
                v as FormValues["opsWeeklyReportDay"],
                { shouldDirty: true }
              )
            }
          >
            <SelectTrigger id="opsWeeklyReportDay-trigger" aria-label="주간 리포트 요일">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DAY_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* 주간 리포트 시간 */}
        <div className="space-y-1">
          <Label htmlFor="opsWeeklyReportHour">주간 리포트 시간 (KST, 0–23)</Label>
          <Input
            id="opsWeeklyReportHour"
            type="number"
            min={0}
            max={23}
            {...register("opsWeeklyReportHour")}
          />
          {errors.opsWeeklyReportHour && (
            <p className="text-xs text-destructive">{errors.opsWeeklyReportHour.message}</p>
          )}
        </div>

        {/* 관리자 대시보드 Base URL */}
        <div className="space-y-1">
          <Label htmlFor="opsAdminBaseUrl">관리자 대시보드 Base URL</Label>
          <Input
            id="opsAdminBaseUrl"
            type="url"
            placeholder="https://admin.example.com"
            {...register("opsAdminBaseUrl")}
          />
          <p className="text-xs text-muted-foreground">
            https://로 시작해야 해요. 비워두면 Slack 버튼을 생략해요.
          </p>
          {errors.opsAdminBaseUrl && (
            <p className="text-xs text-destructive">{errors.opsAdminBaseUrl.message}</p>
          )}
        </div>

        {/* 고급 옵션 — CollapsibleSection 내부이므로 inline 버튼으로 구현 */}
        <AdvancedOptions register={register} errors={errors} />

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );
}

/* ── 고급 옵션 서브 컴포넌트 ── */

function AdvancedOptions({
  register,
  errors,
}: {
  register: ReturnType<typeof useForm<FormValues>>["register"];
  errors: ReturnType<typeof useForm<FormValues>>["formState"]["errors"];
}) {
  const [open, setOpen] = useState(false);

  return (
    <div>
      <button
        type="button"
        className="text-sm text-primary underline-offset-2 hover:underline text-left"
        onClick={() => setOpen((prev: boolean) => !prev)}
        aria-expanded={open}
      >
        {open ? "고급 옵션 숨기기" : "고급 옵션 보기"}
      </button>

      {open && (
        <div className="grid gap-3 mt-3 p-4 border rounded-md bg-muted/20">
          {/* 쿨다운 */}
          <div className="space-y-1">
            <Label htmlFor="opsPipelineCooldownMinutes">쿨다운 (분)</Label>
            <Input
              id="opsPipelineCooldownMinutes"
              type="number"
              min={0}
              {...register("opsPipelineCooldownMinutes")}
            />
            {errors.opsPipelineCooldownMinutes && (
              <p className="text-xs text-destructive">
                {errors.opsPipelineCooldownMinutes.message}
              </p>
            )}
          </div>

          {/* 인시던트 윈도우 */}
          <div className="space-y-1">
            <Label htmlFor="opsIncidentWindowMinutes">인시던트 감지 윈도우 (분)</Label>
            <Input
              id="opsIncidentWindowMinutes"
              type="number"
              min={1}
              {...register("opsIncidentWindowMinutes")}
            />
            {errors.opsIncidentWindowMinutes && (
              <p className="text-xs text-destructive">
                {errors.opsIncidentWindowMinutes.message}
              </p>
            )}
          </div>

          {/* 인시던트 임계 카테고리 수 */}
          <div className="space-y-1">
            <Label htmlFor="opsIncidentThresholdCategories">인시던트 임계 카테고리 수</Label>
            <Input
              id="opsIncidentThresholdCategories"
              type="number"
              min={1}
              {...register("opsIncidentThresholdCategories")}
            />
            {errors.opsIncidentThresholdCategories && (
              <p className="text-xs text-destructive">
                {errors.opsIncidentThresholdCategories.message}
              </p>
            )}
          </div>

          {/* 스케줄 미스 유예 시간 */}
          <div className="space-y-1">
            <Label htmlFor="opsScheduleMissGraceMinutes">스케줄 미스 유예 시간 (분)</Label>
            <Input
              id="opsScheduleMissGraceMinutes"
              type="number"
              min={0}
              {...register("opsScheduleMissGraceMinutes")}
            />
            {errors.opsScheduleMissGraceMinutes && (
              <p className="text-xs text-destructive">
                {errors.opsScheduleMissGraceMinutes.message}
              </p>
            )}
          </div>

          {/* 예산 경고 임계 */}
          <div className="space-y-1">
            <Label htmlFor="opsBudgetWarnPct">예산 경고 임계 (%)</Label>
            <Input
              id="opsBudgetWarnPct"
              type="number"
              min={0}
              max={100}
              {...register("opsBudgetWarnPct")}
            />
            {errors.opsBudgetWarnPct && (
              <p className="text-xs text-destructive">{errors.opsBudgetWarnPct.message}</p>
            )}
          </div>

          {/* 예산 위험 임계 */}
          <div className="space-y-1">
            <Label htmlFor="opsBudgetCriticalPct">예산 위험 임계 (%)</Label>
            <Input
              id="opsBudgetCriticalPct"
              type="number"
              min={0}
              max={100}
              {...register("opsBudgetCriticalPct")}
            />
            {errors.opsBudgetCriticalPct && (
              <p className="text-xs text-destructive">{errors.opsBudgetCriticalPct.message}</p>
            )}
          </div>

          {/* 복구 연속 임계 */}
          <div className="space-y-1">
            <Label htmlFor="opsRecoveryStreakThreshold">복구 연속 성공 임계 횟수</Label>
            <Input
              id="opsRecoveryStreakThreshold"
              type="number"
              min={1}
              {...register("opsRecoveryStreakThreshold")}
            />
            {errors.opsRecoveryStreakThreshold && (
              <p className="text-xs text-destructive">
                {errors.opsRecoveryStreakThreshold.message}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/* ── 헬퍼 ── */

function buildDefaultValues(settings: RuntimeSettings): FormValues {
  return {
    opsNotificationProfile: settings.opsNotificationProfile ?? "FULL",
    opsDailyForecastHour: settings.opsDailyForecastHour ?? 8,
    opsWeeklyReportDay: settings.opsWeeklyReportDay ?? "MONDAY",
    opsWeeklyReportHour: settings.opsWeeklyReportHour ?? 9,
    opsPipelineCooldownMinutes: settings.opsPipelineCooldownMinutes ?? 30,
    opsIncidentWindowMinutes: settings.opsIncidentWindowMinutes ?? 60,
    opsIncidentThresholdCategories: settings.opsIncidentThresholdCategories ?? 2,
    opsScheduleMissGraceMinutes: settings.opsScheduleMissGraceMinutes ?? 15,
    opsBudgetWarnPct: settings.opsBudgetWarnPct ?? 70,
    opsBudgetCriticalPct: settings.opsBudgetCriticalPct ?? 90,
    // null → 빈 문자열로 변환 (폼 입력 필드는 string만 허용)
    opsAdminBaseUrl: settings.opsAdminBaseUrl ?? "",
    opsSilentHoursEnabled: settings.opsSilentHoursEnabled ?? true,
    opsRecoveryStreakThreshold: settings.opsRecoveryStreakThreshold ?? 3,
    opsLogsEnabled: settings.opsLogsEnabled ?? true,
  };
}
