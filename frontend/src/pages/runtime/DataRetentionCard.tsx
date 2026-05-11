import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

const schema = z.object({
  retentionRssItemsDays: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(7, "최소 7일")
    .max(365, "최대 365일"),
  retentionBatchSummariesDays: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(7, "최소 7일")
    .max(730, "최대 730일"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

/** 접힌 상태에서 보여줄 요약 문자열을 생성한다 */
function buildSummary(settings: RuntimeSettings): string {
  return `원본 기사 ${settings.retentionRssItemsDays}일 / AI 요약 ${settings.retentionBatchSummariesDays}일`;
}

export function DataRetentionCard({ settings, isSaving, onSave }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isDirty, errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      retentionRssItemsDays: settings.retentionRssItemsDays,
      retentionBatchSummariesDays: settings.retentionBatchSummariesDays,
    },
  });

  // 서버 설정이 바뀌면 폼 기본값을 동기화한다
  useEffect(() => {
    reset({
      retentionRssItemsDays: settings.retentionRssItemsDays,
      retentionBatchSummariesDays: settings.retentionBatchSummariesDays,
    });
  }, [settings, reset]);

  function onSubmit(values: FormValues) {
    onSave({
      retentionRssItemsDays: values.retentionRssItemsDays,
      retentionBatchSummariesDays: values.retentionBatchSummariesDays,
    });
  }

  return (
    <CollapsibleSection
      title="데이터 보관 정책"
      description="북마크·피드백이 있는 요약은 기간과 무관하게 영구 보관됩니다"
      summary={buildSummary(settings)}
      defaultOpen={false}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-4">
        <div className="space-y-1">
          <Label htmlFor="retentionRssItemsDays">원본 기사 보관 (일)</Label>
          <Input
            id="retentionRssItemsDays"
            type="number"
            {...register("retentionRssItemsDays")}
          />
          <p className="text-xs text-muted-foreground">최소 7일, 최대 365일 (약 1년)</p>
          {errors.retentionRssItemsDays && (
            <p className="text-xs text-destructive">{errors.retentionRssItemsDays.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="retentionBatchSummariesDays">AI 요약 보관 (일)</Label>
          <Input
            id="retentionBatchSummariesDays"
            type="number"
            {...register("retentionBatchSummariesDays")}
          />
          <p className="text-xs text-muted-foreground">최소 7일, 최대 730일 (약 2년)</p>
          {errors.retentionBatchSummariesDays && (
            <p className="text-xs text-destructive">{errors.retentionBatchSummariesDays.message}</p>
          )}
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );
}
