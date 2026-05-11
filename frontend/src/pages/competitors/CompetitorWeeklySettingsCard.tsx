import { useEffect } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

const DAY_OPTIONS = [
  { label: "월요일", value: "MONDAY" },
  { label: "화요일", value: "TUESDAY" },
  { label: "수요일", value: "WEDNESDAY" },
  { label: "목요일", value: "THURSDAY" },
  { label: "금요일", value: "FRIDAY" },
  { label: "토요일", value: "SATURDAY" },
  { label: "일요일", value: "SUNDAY" },
] as const;

const DM_MODE_OPTIONS = [
  { label: "보내지 않음", value: "off" },
  { label: "승인된 사용자 전원", value: "all" },
  { label: "선택", value: "selected" },
] as const;

/** 0~23시를 한국어 오전/오후 형식으로 표시한다 */
function formatHourLabel(hour: number): string {
  if (hour === 0) return "오전 12시";
  if (hour < 12) return `오전 ${hour}시`;
  if (hour === 12) return "오후 12시";
  return `오후 ${hour - 12}시`;
}

/** 현재 날짜 기준으로 다음 발송 예정일을 계산한다 */
function calcNextDelivery(day: string, hour: number): string {
  const dayIndex: Record<string, number> = {
    SUNDAY: 0,
    MONDAY: 1,
    TUESDAY: 2,
    WEDNESDAY: 3,
    THURSDAY: 4,
    FRIDAY: 5,
    SATURDAY: 6,
  };
  const target = dayIndex[day];
  if (target === undefined) return "-";

  const now = new Date();
  const currentDay = now.getDay();
  let daysUntil = (target - currentDay + 7) % 7;
  // 같은 요일이면 시간 비교
  if (daysUntil === 0 && now.getHours() >= hour) {
    daysUntil = 7;
  }

  const next = new Date(now);
  next.setDate(now.getDate() + daysUntil);
  next.setHours(hour, 0, 0, 0);

  const m = next.getMonth() + 1;
  const d = next.getDate();
  const dayLabel = DAY_OPTIONS.find((o) => o.value === day)?.label ?? day;
  return `${m}월 ${d}일 (${dayLabel}) ${formatHourLabel(hour)}`;
}

const schema = z.object({
  competitorWeeklyEnabled: z.boolean(),
  competitorWeeklyChannelId: z.string().optional(),
  competitorWeeklyDay: z.string(),
  competitorWeeklyHour: z.coerce.number().int().min(0).max(23),
  competitorWeeklyDmMode: z.enum(["off", "all", "selected"]),
  competitorWeeklyDmUserIds: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

export function CompetitorWeeklySettingsCard({ settings, isSaving, onSave }: Props) {
  const { register, handleSubmit, reset, watch, control } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      competitorWeeklyEnabled: settings.competitorWeeklyEnabled,
      competitorWeeklyChannelId: settings.competitorWeeklyChannelId ?? "",
      competitorWeeklyDay: settings.competitorWeeklyDay || "MONDAY",
      competitorWeeklyHour: settings.competitorWeeklyHour ?? 9,
      competitorWeeklyDmMode: settings.competitorWeeklyDmMode ?? "off",
      competitorWeeklyDmUserIds: settings.competitorWeeklyDmUserIds ?? "",
    },
  });

  const enabled = watch("competitorWeeklyEnabled");
  const selectedDay = watch("competitorWeeklyDay");
  const selectedHour = watch("competitorWeeklyHour");
  const dmMode = watch("competitorWeeklyDmMode");

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다
  useEffect(() => {
    reset({
      competitorWeeklyEnabled: settings.competitorWeeklyEnabled,
      competitorWeeklyChannelId: settings.competitorWeeklyChannelId ?? "",
      competitorWeeklyDay: settings.competitorWeeklyDay || "MONDAY",
      competitorWeeklyHour: settings.competitorWeeklyHour ?? 9,
      competitorWeeklyDmMode: settings.competitorWeeklyDmMode ?? "off",
      competitorWeeklyDmUserIds: settings.competitorWeeklyDmUserIds ?? "",
    });
  }, [settings, reset]);

  function onSubmit(values: FormValues) {
    onSave({
      competitorWeeklyEnabled: values.competitorWeeklyEnabled,
      competitorWeeklyChannelId: values.competitorWeeklyChannelId?.trim() || undefined,
      competitorWeeklyDay: values.competitorWeeklyDay,
      competitorWeeklyHour: values.competitorWeeklyHour,
      competitorWeeklyDmMode: values.competitorWeeklyDmMode,
      competitorWeeklyDmUserIds:
        values.competitorWeeklyDmMode === "selected"
          ? values.competitorWeeklyDmUserIds?.trim() || undefined
          : undefined,
    });
  }

  return (
    <section className="rounded-lg border bg-card p-5 space-y-4">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {/* 헤더: 상태 뱃지 + 라벨 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-sm">주간 요약 발송</h3>
            <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${
              enabled
                ? "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
                : "bg-muted text-muted-foreground"
            }`}>
              {enabled ? "활성" : "비활성"}
            </span>
          </div>
          <Controller
            control={control}
            name="competitorWeeklyEnabled"
            render={({ field }) => (
              <Button
                type="button"
                variant={field.value ? "outline" : "default"}
                size="sm"
                className="text-xs h-7"
                onClick={() => field.onChange(!field.value)}
              >
                {field.value ? "비활성화" : "활성화하기"}
              </Button>
            )}
          />
        </div>
        <p className="text-xs text-muted-foreground -mt-2">
          경쟁사 분석 결과를 매주 Slack으로 발송해요
        </p>

        {/* 상세 설정 */}
        <div className={`space-y-4 pt-2 border-t ${!enabled ? "opacity-40 pointer-events-none select-none" : ""}`}>
            {/* 발송 채널 */}
            <div className="space-y-1">
              <Label htmlFor="competitorWeeklyChannelId">발송 채널</Label>
              <Input
                id="competitorWeeklyChannelId"
                placeholder="C0123456789"
                {...register("competitorWeeklyChannelId")}
              />
              <p className="text-xs text-muted-foreground">
                주간 요약을 받을 Slack 채널 ID를 입력하세요
              </p>
            </div>

            {/* 발송 시점 */}
            <div className="space-y-1">
              <Label>발송 시점</Label>
              <div className="flex gap-2">
                <Controller
                  control={control}
                  name="competitorWeeklyDay"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-[140px]">
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
                  )}
                />
                <Controller
                  control={control}
                  name="competitorWeeklyHour"
                  render={({ field }) => (
                    <Select
                      value={String(field.value)}
                      onValueChange={(v) => field.onChange(Number(v))}
                    >
                      <SelectTrigger className="w-[140px]">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {Array.from({ length: 24 }, (_, i) => (
                          <SelectItem key={i} value={String(i)}>
                            {formatHourLabel(i)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                다음 발송 예정: {calcNextDelivery(selectedDay, selectedHour)}
              </p>
            </div>

            {/* DM 발송 */}
            <div className="space-y-2">
              <Label>DM 발송</Label>
              <Controller
                control={control}
                name="competitorWeeklyDmMode"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger className="w-[200px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DM_MODE_OPTIONS.map((opt) => (
                        <SelectItem key={opt.value} value={opt.value}>
                          {opt.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {dmMode === "selected" && (
                <div className="space-y-1">
                  <Textarea
                    placeholder='["U0123456789", "U9876543210"]'
                    rows={3}
                    {...register("competitorWeeklyDmUserIds")}
                  />
                  <p className="text-xs text-muted-foreground">
                    DM을 받을 Slack 사용자 ID를 JSON 배열 형태로 입력하세요
                  </p>
                </div>
              )}
            </div>
        </div>

        <Button type="submit" disabled={isSaving || !enabled}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </section>
  );
}
