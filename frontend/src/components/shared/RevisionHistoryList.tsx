import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronDown, ChevronRight, History, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { EmptyState } from "@/components/shared/EmptyState";
import { cn } from "@/utils/cn";
import { formatRelativeDate } from "@/utils/date";

import { historyKeys } from "@/queries/historyKeys";
import { historyService } from "@/services/historyService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { RevisionResource, RevisionSummary } from "@/types/revisionHistory";

interface RevisionHistoryListProps {
  resource: RevisionResource;
  resourceId: string;
  /** 현재 엔티티의 updatedAt — 낙관적 잠금용. */
  currentUpdatedAt: string | null | undefined;
  /** 복원 성공 시 상위에서 detail 쿼리 invalidation을 수행하기 위한 훅. */
  onRestored?: () => void;
  /** 펼침 상태 초기값. 기본은 접힘. */
  defaultOpen?: boolean;
  /** 최상단 라벨을 숨기는 옵션(탭 내부에 렌더 시). */
  hideHeader?: boolean;
}

/**
 * 4개 도메인 공용 "변경 이력 + 이 버전으로 되돌리기" 섹션.
 *
 * 기본은 접힘 상태로 노출되며, 사용자가 펼치면 최근 20개 revision을 한 번에 보여준다.
 * 각 row에 "이 버전으로 되돌리기" 버튼이 있고, 클릭 시 ConfirmModal → restore API 호출 순서로 진행된다.
 */
export function RevisionHistoryList({
  resource,
  resourceId,
  currentUpdatedAt,
  onRestored,
  defaultOpen = false,
  hideHeader = false
}: RevisionHistoryListProps) {
  const [expanded, setExpanded] = useState(defaultOpen);
  const [targetRevision, setTargetRevision] = useState<RevisionSummary | null>(null);

  const queryClient = useQueryClient();

  const historyQuery = useQuery({
    queryKey: historyKeys.byResource(resource, resourceId),
    queryFn: () => historyService.getHistory(resource, resourceId, 20),
    enabled: expanded && Boolean(resourceId)
  });

  const restoreMutation = useMutation({
    mutationFn: (revisionId: string) => {
      if (!currentUpdatedAt) {
        return Promise.reject(new Error("저장된 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."));
      }
      return historyService.restore(resource, resourceId, {
        revisionId,
        expectedUpdatedAt: currentUpdatedAt
      });
    },
    onSuccess: () => {
      toast.success("선택한 버전으로 되돌렸어요");
      queryClient.invalidateQueries({ queryKey: historyKeys.byResource(resource, resourceId) });
      onRestored?.();
    },
    onError: (error) => {
      toast.error(userFriendlyMessage(error, "되돌리기에 실패했어요"));
    }
  });

  return (
    <section className="rounded-2xl border border-border bg-card">
      {!hideHeader && (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className={cn(
            "flex w-full items-center justify-between gap-2 px-4 py-3 text-left",
            "text-sm font-medium text-foreground"
          )}
        >
          <span className="flex items-center gap-2">
            <History className="h-4 w-4 text-muted-foreground" />
            변경 이력
          </span>
          {expanded ? (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          )}
        </button>
      )}

      {(expanded || hideHeader) && (
        <div className="border-t border-border px-4 py-3">
          {historyQuery.isLoading && (
            <p className="py-4 text-sm text-muted-foreground">불러오는 중…</p>
          )}
          {historyQuery.isError && (
            <p className="py-4 text-sm text-destructive">이력을 불러오지 못했어요.</p>
          )}
          {historyQuery.data && historyQuery.data.length === 0 && (
            <EmptyState
              title="변경 이력이 없어요"
              description="수정하면 이곳에 이력이 쌓여요."
            />
          )}
          {historyQuery.data && historyQuery.data.length > 0 && (
            <ul className="divide-y divide-border">
              {historyQuery.data.map((revision) => (
                <li
                  key={revision.revisionId}
                  className="flex items-center justify-between gap-4 py-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-foreground">
                      v{revision.revisionNumber} · {revision.editorName} ·{" "}
                      <span className="text-muted-foreground">
                        {formatRelativeDate(revision.createdAt)}
                      </span>
                    </p>
                    {revision.changedFields.length > 0 && (
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        {revision.changedFields.slice(0, 3).join(", ")}
                        {revision.changedFields.length > 3 && " 외"}
                      </p>
                    )}
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setTargetRevision(revision)}
                    disabled={restoreMutation.isPending}
                  >
                    <RotateCcw className="mr-1 h-3.5 w-3.5" />
                    되돌리기
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <ConfirmModal
        open={targetRevision !== null}
        onOpenChange={(open) => !open && setTargetRevision(null)}
        title="이 버전으로 되돌릴까요?"
        description={
          targetRevision
            ? `v${targetRevision.revisionNumber} (${targetRevision.editorName} · ${formatRelativeDate(
                targetRevision.createdAt
              )}) 시점으로 복원돼요. 현재 내용은 새 이력으로 남아요.`
            : undefined
        }
        confirmLabel="되돌리기"
        variant="destructive"
        onConfirm={() => {
          if (!targetRevision) return;
          restoreMutation.mutate(targetRevision.revisionId);
          setTargetRevision(null);
        }}
      />
    </section>
  );
}
