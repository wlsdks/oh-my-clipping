import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { categoryService } from "@/services/categoryService";
import { categoryKeys } from "@/queries/categoryKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { Category, CategoryPurpose } from "@/types/category";
import { EditablePurposeRow, EditableTextareaRow } from "./SettingsTabRows";

interface SettingsMetadataSectionProps {
  item: Category;
  isWorking: boolean;
}

/**
 * V123(Phase 3 PR1): 분석 목적 metadata 섹션 — 목적/배경/문제.
 * 일반 `onEdit` 경로와 다른 필드라 독립적인 mutation 으로 관리한다.
 * 빈 문자열은 서비스 레이어에서 null(초기화)로 해석된다.
 */
export function SettingsMetadataSection({ item, isWorking }: SettingsMetadataSectionProps) {
  const queryClient = useQueryClient();

  // metadata 저장 mutation — purpose/background/problemStatement 전용.
  const metadataMutation = useMutation({
    mutationFn: (patch: {
      purpose?: CategoryPurpose | "" | null;
      background?: string | null;
      problemStatement?: string | null;
    }) =>
      categoryService.update(item.id, {
        ...patch,
        expectedUpdatedAt: item.updatedAt,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      toast.success("저장했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err)),
  });

  const disabled = isWorking || metadataMutation.isPending;

  return (
    <>
      <div className="flex items-center justify-between mt-3">
        <h4 className="text-sm font-medium text-foreground">분석 용도</h4>
        <span className="text-[11px] text-muted-foreground">선택 — 운영/분석에 활용</span>
      </div>
      <div className="rounded-lg border border-border bg-muted/30 divide-y divide-border">
        <EditablePurposeRow
          value={item.purpose ?? null}
          isWorking={disabled}
          onSave={(next) => metadataMutation.mutate({ purpose: next ?? "" })}
        />
        <EditableTextareaRow
          label="배경"
          value={item.background ?? ""}
          popoverLabel="구독을 만든 배경"
          placeholder="예) 영업팀이 매일 경쟁사 동향을 수집해야 해서 만들었어요"
          isWorking={disabled}
          onSave={(v) => metadataMutation.mutate({ background: v })}
        />
        <EditableTextareaRow
          label="문제"
          value={item.problemStatement ?? ""}
          popoverLabel="해결하려는 문제"
          placeholder="예) 수동으로 검색하면 시간이 많이 걸리고 핵심을 놓칠 때가 많아요"
          isWorking={disabled}
          onSave={(v) => metadataMutation.mutate({ problemStatement: v })}
        />
      </div>
    </>
  );
}
