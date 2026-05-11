import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { organizationService } from "@/services/organizationService";
import { organizationKeys } from "@/queries/organizationKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { OrganizationMultiSelect } from "@/components/shared/OrganizationMultiSelect";

interface SettingsOrganizationsSectionProps {
  categoryId: string;
  isWorking: boolean;
}

/**
 * Phase 3 PR2: 관련 조직(경쟁사/고객사/파트너) 연결 섹션.
 * 카테고리에 연결된 조직 목록을 조회하고, MultiSelect 로 교체할 때마다 링크를 전체 대체한다.
 */
export function SettingsOrganizationsSection({ categoryId, isWorking }: SettingsOrganizationsSectionProps) {
  const queryClient = useQueryClient();

  // 카테고리에 연결된 조직 id 목록 조회 — 빈 리스트도 유효값.
  const linkedOrgsQuery = useQuery({
    queryKey: organizationKeys.byCategory(categoryId),
    queryFn: () => organizationService.listByCategoryId(categoryId),
  });

  const linkedOrgIds = linkedOrgsQuery.data?.content.map((o) => o.id) ?? [];

  // Category ↔ Organization 링크 교체 mutation — 전체 대체 방식.
  const orgLinkMutation = useMutation({
    mutationFn: (organizationIds: string[]) =>
      organizationService.setCategoryOrganizations(categoryId, { organizationIds }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: organizationKeys.byCategory(categoryId) });
      toast.success("관련 조직이 저장됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "관련 조직 저장에 실패했어요")),
  });

  const disabled = isWorking || orgLinkMutation.isPending || linkedOrgsQuery.isLoading;

  return (
    <>
      <div className="flex items-center justify-between mt-3">
        <h4 className="text-sm font-medium text-foreground">관련 조직</h4>
        <span className="text-[11px] text-muted-foreground">선택 — 분석에 활용</span>
      </div>
      <div className="rounded-lg border border-border bg-muted/30 p-3 space-y-2">
        {linkedOrgIds.length === 0 && !linkedOrgsQuery.isLoading && (
          <p className="text-xs text-muted-foreground">
            연결된 조직이 없어요. 경쟁사/고객사/파트너 를 선택하면 분석에 활용할 수 있어요.
          </p>
        )}
        <OrganizationMultiSelect
          value={linkedOrgIds}
          onChange={(next) => orgLinkMutation.mutate(next)}
          disabled={disabled}
          placeholder="관련 조직을 선택하세요"
        />
      </div>
    </>
  );
}
