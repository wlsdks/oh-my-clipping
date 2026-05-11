import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";

import { userFriendlyMessage, extractStaleEditInfo } from "@/shared/lib/httpError";
import { useStaleEditStore } from "@/lib/staleEditBus";
import { userService, type ApproveClippingRequestData } from "@/services/userService";
import { categoryService } from "@/services/categoryService";
import { userKeys } from "@/queries/userKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { historyKeys } from "@/queries/historyKeys";
import { showSaveToastWithUndo } from "@/utils/saveToastUndo";

/**
 * 구독 관리 페이지에서 사용하는 모든 뮤테이션을 모아 반환한다.
 *
 * @param onClose - 뮤테이션 성공 후 사이드 패널을 닫는 콜백
 * @param setSelectedIds - 벌크 작업 완료 후 선택 초기화 콜백
 */
export function useSubscriptionMutations({
  onClose,
  setSelectedIds,
}: {
  onClose: () => void;
  setSelectedIds: (ids: Set<string>) => void;
}) {
  const queryClient = useQueryClient();

  // 벌크 승인 진행 상태 (진행 중 표시용)
  const [bulkApproveProgress, setBulkApproveProgress] = useState<{ done: number; total: number } | null>(null);

  // ── 단건 승인 ──
  const approveMutation = useMutation({
    mutationFn: (args: { id: string; data: ApproveClippingRequestData }) =>
      userService.approveAdminClippingRequest(args.id, args.data),
    onSuccess: () => {
      toast.success("요청을 승인했어요");
      queryClient.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      queryClient.invalidateQueries({ queryKey: userKeys.requests() });
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 단건 반려 ──
  const rejectMutation = useMutation({
    mutationFn: (args: { id: string; note: string }) =>
      userService.rejectAdminClippingRequest(args.id, { reviewNote: args.note }),
    onSuccess: () => {
      toast.success("요청을 반려했어요");
      queryClient.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      queryClient.invalidateQueries({ queryKey: userKeys.requests() });
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 카테고리 편집 ──
  const editCategoryMutation = useMutation({
    mutationFn: (args: {
      id: string;
      data: { name: string; slackChannelId?: string; maxItems: number };
      expectedUpdatedAt?: string | null;
    }) =>
      categoryService.update(args.id, {
        ...args.data,
        expectedUpdatedAt: args.expectedUpdatedAt ?? null,
      }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      queryClient.invalidateQueries({ queryKey: historyKeys.byResource("category", saved.id) });
      showSaveToastWithUndo({
        resource: "category",
        savedId: saved.id,
        savedUpdatedAt: saved.updatedAt,
        successMessage: "구독 설정을 수정했어요",
        onRestored: () => {
          queryClient.invalidateQueries({ queryKey: categoryKeys.all });
          queryClient.invalidateQueries({ queryKey: historyKeys.byResource("category", saved.id) });
        }
      });
      onClose();
    },
    onError: (err, vars) => {
      // 낙관적 잠금 충돌이면 전역 모달을 띄운다.
      const staleInfo = extractStaleEditInfo(err);
      if (staleInfo) {
        useStaleEditStore.getState().show(
          staleInfo,
          async () => {
            await queryClient.invalidateQueries({ queryKey: categoryKeys.all });
          },
          { draftKey: `draft:category:${vars.id}` }
        );
        return;
      }
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 일시정지 ──
  const pauseMutation = useMutation({
    mutationFn: (id: string) => categoryService.pause(id),
    onSuccess: () => {
      toast.success("구독을 일시정지했습니다");
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 재개 ──
  const resumeMutation = useMutation({
    mutationFn: (id: string) => categoryService.resume(id),
    onSuccess: () => {
      toast.success("구독을 재개했습니다");
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 공개/비공개 토글 ──
  const togglePublicMutation = useMutation({
    mutationFn: (args: { id: string; isPublic: boolean; expectedUpdatedAt?: string | null }) =>
      categoryService.update(args.id, {
        isPublic: args.isPublic,
        expectedUpdatedAt: args.expectedUpdatedAt ?? null,
      }),
    onSuccess: (_data, vars) => {
      toast.success(vars.isPublic ? "구독 가능한 주제에 공개했어요" : "구독 가능한 주제에서 숨겼어요");
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
    },
    onError: (err, vars) => {
      const staleInfo = extractStaleEditInfo(err);
      if (staleInfo) {
        useStaleEditStore.getState().show(staleInfo, async () => {
          await queryClient.invalidateQueries({ queryKey: categoryKeys.all });
        }, { draftKey: `draft:category:${vars.id}` });
        return;
      }
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 삭제 ──
  const deleteCategoryMutation = useMutation({
    mutationFn: (id: string) => categoryService.delete(id),
    onSuccess: () => {
      toast.success("구독을 삭제했어요");
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 벌크 활성화/비활성화 ──
  const bulkToggleMutation = useMutation({
    mutationFn: async (args: { ids: string[]; isActive: boolean }) => {
      const results = await Promise.allSettled(
        args.ids.map((id) => categoryService.update(id, { isActive: args.isActive }))
      );
      const fulfilled = results.filter((r) => r.status === "fulfilled").length;
      const rejected = results.filter((r) => r.status === "rejected").length;
      return { fulfilled, rejected, total: args.ids.length };
    },
    onSuccess: (result, vars) => {
      // 일부라도 성공했으면 쿼리 무효화 (성공한 항목 반영)
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      setSelectedIds(new Set());

      if (result.rejected === 0) {
        toast.success(
          vars.isActive
            ? `${result.fulfilled}개 구독을 활성화했어요`
            : `${result.fulfilled}개 구독을 비활성화했어요`
        );
      } else if (result.fulfilled === 0) {
        toast.error(
          vars.isActive
            ? `${result.total}개 구독 활성화에 모두 실패했어요`
            : `${result.total}개 구독 비활성화에 모두 실패했어요`
        );
      } else {
        toast.warning(`${result.fulfilled}개 성공, ${result.rejected}개 실패했어요`);
      }
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err));
    },
  });

  // ── 벌크 승인 (pending 요청 순차 처리) ──
  const bulkApproveMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const defaultData: ApproveClippingRequestData = {
        legalBasis: "QUOTATION_ONLY",
        summaryAllowed: true,
        fulltextAllowed: false,
        reviewNotes: null,
        responsibilityAcknowledged: true,
      };
      let fulfilled = 0;
      let rejected = 0;
      for (let i = 0; i < ids.length; i++) {
        try {
          await userService.approveAdminClippingRequest(ids[i], defaultData);
          fulfilled++;
        } catch {
          rejected++;
        }
        setBulkApproveProgress({ done: i + 1, total: ids.length });
      }
      return { fulfilled, rejected, total: ids.length };
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      queryClient.invalidateQueries({ queryKey: userKeys.requests() });
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      setSelectedIds(new Set());
      setBulkApproveProgress(null);

      if (result.rejected === 0) {
        toast.success(`${result.fulfilled}건의 요청을 승인했어요`);
      } else if (result.fulfilled === 0) {
        toast.error(`${result.total}건의 승인이 모두 실패했어요`);
      } else {
        toast.warning(`${result.fulfilled}건 승인, ${result.rejected}건 실패했어요`);
      }
    },
    onError: (err) => {
      setBulkApproveProgress(null);
      toast.error(userFriendlyMessage(err));
    },
  });

  const isCategoryWorking =
    editCategoryMutation.isPending ||
    pauseMutation.isPending ||
    resumeMutation.isPending ||
    togglePublicMutation.isPending ||
    deleteCategoryMutation.isPending;

  return {
    approveMutation,
    rejectMutation,
    editCategoryMutation,
    pauseMutation,
    resumeMutation,
    togglePublicMutation,
    deleteCategoryMutation,
    bulkToggleMutation,
    bulkApproveMutation,
    bulkApproveProgress,
    isCategoryWorking,
  };
}
