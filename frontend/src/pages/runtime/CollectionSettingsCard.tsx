import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link } from "react-router-dom";
import { ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

const schema = z.object({
  defaultHoursBack: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1시간 이상으로 설정해 주세요")
    .max(720, "최대 720시간(30일)까지 설정할 수 있어요"),
  digestDefaultMaxItems: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1건 이상 설정해 주세요")
    .max(50, "최대 50건까지 설정할 수 있어요"),
  digestMinImportanceScore: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .min(0, "0 이상이어야 해요")
    .max(1, "1 이하여야 해요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

/** 접힌 상태에서 보여줄 요약 문자열을 생성한다 */
function buildSummary(settings: RuntimeSettings): string {
  return `${settings.defaultHoursBack}시간 / 최대 ${settings.digestDefaultMaxItems}건 / 중요도 ${settings.digestMinImportanceScore}+`;
}

export function CollectionSettingsCard({ settings, isSaving, onSave }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isDirty, errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      defaultHoursBack: settings.defaultHoursBack,
      digestDefaultMaxItems: settings.digestDefaultMaxItems,
      digestMinImportanceScore: settings.digestMinImportanceScore,
    },
  });

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다
  useEffect(() => {
    reset({
      defaultHoursBack: settings.defaultHoursBack,
      digestDefaultMaxItems: settings.digestDefaultMaxItems,
      digestMinImportanceScore: settings.digestMinImportanceScore,
    });
  }, [settings, reset]);

  function onSubmit(values: FormValues) {
    onSave({
      defaultHoursBack: values.defaultHoursBack,
      digestDefaultMaxItems: values.digestDefaultMaxItems,
      digestMinImportanceScore: values.digestMinImportanceScore,
    });
  }

  return (
    <CollapsibleSection
      title="뉴스 수집 설정"
      description="뉴스를 수집할 때 적용되는 기본 범위와 필터 기준이에요"
      summary={buildSummary(settings)}
      defaultOpen={false}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-4">
        <Link
          to="/admin/pipeline"
          className="inline-flex items-center gap-1.5 text-sm text-primary hover:underline"
        >
          <ExternalLink className="h-3.5 w-3.5" />
          파이프라인 모니터링 보기
        </Link>

        <div className="space-y-1">
          <Label htmlFor="defaultHoursBack">기본 수집 기간 (시간)</Label>
          <Input id="defaultHoursBack" type="number" {...register("defaultHoursBack")} />
          <p className="text-xs text-muted-foreground">
            몇 시간 이내에 발행된 기사를 수집할지 결정합니다. 기본값 24시간.
          </p>
          {errors.defaultHoursBack && (
            <p className="text-xs text-destructive">{errors.defaultHoursBack.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="digestDefaultMaxItems">기본 최대 기사 수</Label>
          <Input id="digestDefaultMaxItems" type="number" {...register("digestDefaultMaxItems")} />
          <p className="text-xs text-muted-foreground">
            한 번 발송 시 포함할 수 있는 기사의 최대 개수이에요.
          </p>
          {errors.digestDefaultMaxItems && (
            <p className="text-xs text-destructive">{errors.digestDefaultMaxItems.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="digestMinImportanceScore">최소 중요도 점수 (0~1)</Label>
          <Input
            id="digestMinImportanceScore"
            type="number"
            step="0.05"
            {...register("digestMinImportanceScore")}
          />
          <p className="text-xs text-muted-foreground">
            이 점수 미만의 기사는 발송 대상에서 제외돼요. 0.5 이상 권장.
          </p>
          {errors.digestMinImportanceScore && (
            <p className="text-xs text-destructive">{errors.digestMinImportanceScore.message}</p>
          )}
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );
}
