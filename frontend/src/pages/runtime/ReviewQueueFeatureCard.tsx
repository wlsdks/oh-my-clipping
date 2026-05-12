import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { CollapsibleSection } from "@/components/shared/CollapsibleSection";
import { MAX_BULK_SELECT } from "@/features/review-queue/model/bulkLimits";
import type { RuntimeSettings } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

/**
 * 뉴스 검토(batch UX) 관련 운영 설정 카드.
 *
 * 제공 설정:
 * - `reviewBatchUxEnabled`: 일괄 검토 UI 킬 스위치. off면 기존 단건 플로우만 노출.
 * - `defaultReviewPerCategory`: 전체 조회 시 카테고리별 top-N 샘플링 기본값(0=비활성).
 *
 * 주의: 일괄 UI를 켜면 관리자는 동시에 최대 {@link MAX_BULK_SELECT}건까지 처리할 수 있다.
 */

const schema = z.object({
  reviewBatchUxEnabled: z.boolean(),
  defaultReviewPerCategory: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(0, "0 이상이어야 해요")
    .max(100, "최대 100까지 설정할 수 있어요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

function buildSummary(settings: RuntimeSettings): string {
  const flag = settings.reviewBatchUxEnabled ? "일괄 UI 켜짐" : "단건 모드";
  const perCat =
    settings.defaultReviewPerCategory > 0
      ? `카테고리별 ${settings.defaultReviewPerCategory}건`
      : "샘플링 비활성";
  return `${flag} · ${perCat}`;
}

export function ReviewQueueFeatureCard({ settings, isSaving, onSave }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { isDirty, errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      reviewBatchUxEnabled: settings.reviewBatchUxEnabled,
      defaultReviewPerCategory: settings.defaultReviewPerCategory,
    },
  });

  useEffect(() => {
    reset({
      reviewBatchUxEnabled: settings.reviewBatchUxEnabled,
      defaultReviewPerCategory: settings.defaultReviewPerCategory,
    });
  }, [settings, reset]);

  const batchEnabled = watch("reviewBatchUxEnabled");

  function onSubmit(values: FormValues) {
    onSave({
      reviewBatchUxEnabled: values.reviewBatchUxEnabled,
      defaultReviewPerCategory: values.defaultReviewPerCategory,
    });
  }

  return (
    <CollapsibleSection
      title="뉴스 검토 UX"
      description="일괄 승인 UI와 카테고리별 샘플링 기본값을 관리해요"
      summary={buildSummary(settings)}
      defaultOpen={false}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="pt-4 space-y-5">
        {/* 일괄 UI 토글 */}
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <Label htmlFor="reviewBatchUxEnabled" className="text-sm font-medium">
              일괄 검토 UI 사용
            </Label>
            <p className="text-xs text-muted-foreground">
              뉴스 검토 탭에서 체크박스로 최대 {MAX_BULK_SELECT}건까지 일괄 승인/제외할 수 있어요.
              300 유저 운영 중 주의해서 켜 주세요. 모바일에는 노출되지 않아요.
            </p>
          </div>
          <Switch
            id="reviewBatchUxEnabled"
            checked={batchEnabled}
            aria-label="일괄 검토 UI 사용"
            onCheckedChange={(checked) =>
              setValue("reviewBatchUxEnabled", checked, { shouldDirty: true })
            }
          />
        </div>

        {/* 카테고리별 top-N */}
        <div className="space-y-1">
          <Label htmlFor="defaultReviewPerCategory">카테고리별 샘플 수 (0~100)</Label>
          <Input
            id="defaultReviewPerCategory"
            type="number"
            {...register("defaultReviewPerCategory")}
          />
          <p className="text-xs text-muted-foreground">
            전체 조회 시 각 카테고리에서 이 수만큼 뽑아 공평하게 노출해요. 0은 비활성(기존 정렬 사용).
          </p>
          {errors.defaultReviewPerCategory && (
            <p className="text-xs text-destructive">{errors.defaultReviewPerCategory.message}</p>
          )}
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </CollapsibleSection>
  );
}
