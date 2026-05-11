import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

const schema = z.object({
  ralphOrchestrationEnabled: z.boolean(),
  ralphLoopEnabled: z.boolean(),
  ralphLoopMaxIterations: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1회 이상 설정해 주세요")
    .max(30, "최대 30회까지 설정할 수 있어요"),
  ralphLoopStopPhrase: z
    .string()
    .max(120, "최대 120자까지 입력할 수 있어요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

/** 접힌 상태에서 보여줄 요약 문자열을 생성한다 */
function buildSummary(settings: RuntimeSettings): string {
  if (!settings.ralphOrchestrationEnabled) return "Ralph 꺼짐";
  const loop = settings.ralphLoopEnabled
    ? `루프 ${settings.ralphLoopMaxIterations}회`
    : "루프 꺼짐";
  return `Ralph 켜짐 · ${loop}`;
}

export function RalphSettingsCard({ settings, isSaving, onSave }: Props) {
  const { register, handleSubmit, setValue, watch, reset, formState: { isDirty, errors } } =
    useForm<FormValues>({
      resolver: zodResolver(schema),
      defaultValues: {
        ralphOrchestrationEnabled: settings.ralphOrchestrationEnabled,
        ralphLoopEnabled: settings.ralphLoopEnabled,
        ralphLoopMaxIterations: settings.ralphLoopMaxIterations,
        ralphLoopStopPhrase: settings.ralphLoopStopPhrase,
      },
    });

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다
  useEffect(() => {
    reset({
      ralphOrchestrationEnabled: settings.ralphOrchestrationEnabled,
      ralphLoopEnabled: settings.ralphLoopEnabled,
      ralphLoopMaxIterations: settings.ralphLoopMaxIterations,
      ralphLoopStopPhrase: settings.ralphLoopStopPhrase,
    });
  }, [settings, reset]);

  function onSubmit(values: FormValues) {
    onSave({
      ralphOrchestrationEnabled: values.ralphOrchestrationEnabled,
      ralphLoopEnabled: values.ralphLoopEnabled,
      ralphLoopMaxIterations: values.ralphLoopMaxIterations,
      ralphLoopStopPhrase: values.ralphLoopStopPhrase,
    });
  }

  return (
    <CollapsibleSection
      title="고급 설정 (Ralph)"
      description="AI 오케스트레이션 파이프라인 설정이에요"
      summary={buildSummary(settings)}
      defaultOpen={false}
      warning
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-4">
        {/* 오케스트레이션 활성화 토글 */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="ralphOrchestrationEnabled">오케스트레이션 사용</Label>
            <p className="text-xs text-muted-foreground">
              Ralph AI 오케스트레이터를 통한 파이프라인 실행을 활성화합니다.
            </p>
          </div>
          <Switch
            id="ralphOrchestrationEnabled"
            checked={watch("ralphOrchestrationEnabled")}
            onCheckedChange={(v) =>
              setValue("ralphOrchestrationEnabled", v, { shouldDirty: true })
            }
          />
        </div>

        {/* 루프 활성화 토글 */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label htmlFor="ralphLoopEnabled">루프 사용</Label>
            <p className="text-xs text-muted-foreground">
              루프 모드에서 Ralph가 반복적으로 파이프라인을 실행합니다.
            </p>
          </div>
          <Switch
            id="ralphLoopEnabled"
            checked={watch("ralphLoopEnabled")}
            onCheckedChange={(v) => setValue("ralphLoopEnabled", v, { shouldDirty: true })}
          />
        </div>

        {/* 루프 최대 반복 횟수 */}
        <div className="space-y-1">
          <Label htmlFor="ralphLoopMaxIterations">루프 최대 반복 횟수</Label>
          <Input
            id="ralphLoopMaxIterations"
            type="number"
            {...register("ralphLoopMaxIterations")}
          />
          <p className="text-xs text-muted-foreground">
            루프가 무한 실행되지 않도록 반복 횟수 상한을 지정합니다.
          </p>
          {errors.ralphLoopMaxIterations && (
            <p className="text-xs text-destructive">{errors.ralphLoopMaxIterations.message}</p>
          )}
        </div>

        {/* 루프 종료 문구 */}
        <div className="space-y-1">
          <Label htmlFor="ralphLoopStopPhrase">루프 종료 문구</Label>
          <Input id="ralphLoopStopPhrase" {...register("ralphLoopStopPhrase")} />
          <p className="text-xs text-muted-foreground">
            AI 응답에 이 문구가 포함되면 루프를 즉시 종료합니다.
          </p>
          {errors.ralphLoopStopPhrase && (
            <p className="text-xs text-destructive">{errors.ralphLoopStopPhrase.message}</p>
          )}
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );
}
