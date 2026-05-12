import { useState, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage, extractStaleEditInfo } from "@/shared/lib/httpError";
import { useStaleEditStore } from "@/lib/staleEditBus";
import { useEditingPresence } from "@/hooks/useEditingPresence";
import { EditingPresenceBadge } from "@/components/shared/EditingPresenceBadge";
import { ChangeDetectionStrip } from "@/components/shared/ChangeDetectionStrip";
import { sourceKeys } from "@/queries/sourceKeys";
import { sourceService } from "@/services/sourceService";
import { historyKeys } from "@/queries/historyKeys";
import { showSaveToastWithUndo } from "@/utils/saveToastUndo";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { ConfirmModal } from "@/components/shared/ConfirmModal";

const REGION_OPTIONS = [
  { value: "DOMESTIC", label: "국내" },
  { value: "GLOBAL", label: "해외" },
  { value: "UNKNOWN", label: "미지정" }
];

const VERIFICATION_STATUS_LABEL: Record<string, string> = {
  VERIFIED: "검증 완료",
  PENDING: "검증 대기",
  FAILED: "검증 실패",
  UNKNOWN: "미확인"
};

interface SourceEditModalProps {
  source: Source | null;
  categories: Category[];
  open: boolean;
  onClose: () => void;
}

interface SourceDraft {
  name: string;
  url: string;
  categoryId: string;
  sourceRegion: "GLOBAL" | "DOMESTIC" | "UNKNOWN";
  isActive: boolean;
}

function toSourceDraft(source: Source): SourceDraft {
  return {
    name: source.name,
    url: source.url,
    categoryId: source.categoryId,
    sourceRegion: source.sourceRegion,
    isActive: source.isActive
  };
}

export function SourceEditModal({ source, categories, open, onClose }: SourceEditModalProps) {
  const qc = useQueryClient();
  const [draft, setDraft] = useState<SourceDraft | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  useEffect(() => {
    if (source) setDraft(toSourceDraft(source));
  }, [source]);

  // 편집 presence + 변경 감지 폴링. 모달이 열려 있을 때만 동작.
  const { otherEditors } = useEditingPresence({
    resourceType: "rssSource",
    resourceId: source?.id,
    enabled: !!source && open
  });

  const { data: liveSource } = useQuery({
    queryKey: source ? sourceKeys.detail(source.id) : [],
    queryFn: () => sourceService.getById(source!.id),
    enabled: !!source && open,
    refetchInterval: source && open ? 30_000 : false,
    refetchIntervalInBackground: false,
    retry: false
  });

  const { mutate: updateSource, isPending: isUpdating } = useMutation({
    mutationFn: (data: SourceDraft) =>
      sourceService.update(source!.id, {
        ...data,
        expectedUpdatedAt: source!.updatedAt
      }),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      qc.invalidateQueries({ queryKey: historyKeys.byResource("rss_source", saved.id) });
      showSaveToastWithUndo({
        resource: "rss_source",
        savedId: saved.id,
        savedUpdatedAt: saved.updatedAt,
        successMessage: "소스가 수정됐어요",
        onRestored: () => {
          qc.invalidateQueries({ queryKey: sourceKeys.all });
          qc.invalidateQueries({ queryKey: historyKeys.byResource("rss_source", saved.id) });
        }
      });
      onClose();
    },
    onError: (err) => {
      // 낙관적 잠금 충돌이면 전역 모달을 통해 최신 불러오기 UX를 제공한다.
      const staleInfo = extractStaleEditInfo(err);
      if (staleInfo && source) {
        useStaleEditStore.getState().show(
          staleInfo,
          async () => {
            await qc.invalidateQueries({ queryKey: sourceKeys.all });
          },
          { draftKey: `draft:source:${source.id}` }
        );
        return;
      }
      toast.error(userFriendlyMessage(err, "수정하지 못했어요"));
    }
  });

  const { mutate: deleteSource, isPending: isDeleting } = useMutation({
    mutationFn: () => sourceService.delete(source!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success("소스가 삭제됐어요");
      onClose();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제하지 못했어요"))
  });

  if (!source || !draft) return null;

  const isWorking = isUpdating || isDeleting;

  function update<K extends keyof SourceDraft>(key: K, value: SourceDraft[K]) {
    setDraft((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(o) => {
          if (!o) onClose();
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>소스 편집</DialogTitle>
          <DialogDescription className="sr-only">소스 정보를 수정합니다</DialogDescription>
            {otherEditors.length > 0 && (
              <div className="pt-2">
                <EditingPresenceBadge editors={otherEditors} />
              </div>
            )}
          </DialogHeader>
          <ChangeDetectionStrip
            initialUpdatedAt={source.updatedAt}
            currentUpdatedAt={liveSource?.updatedAt ?? source.updatedAt}
            onReload={async () => {
              await qc.invalidateQueries({ queryKey: sourceKeys.all });
            }}
          />

          <div className="space-y-4">
            {/* 상태 뱃지 */}
            <div className="flex items-center gap-2">
              <Badge variant={source.verificationStatus === "VERIFIED" ? "default" : "secondary"}>
                {VERIFICATION_STATUS_LABEL[source.verificationStatus] ?? source.verificationStatus}
              </Badge>
              {!source.crawlApproved && <Badge variant="outline">미승인</Badge>}
              {source.crawlFailCount > 0 && <Badge variant="destructive">실패 {source.crawlFailCount}회</Badge>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="source-name">소스 이름</Label>
              <Input id="source-name" value={draft.name} onChange={(e) => update("name", e.target.value)} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="source-url">URL</Label>
              <Input id="source-url" value={draft.url} onChange={(e) => update("url", e.target.value)} />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>지역</Label>
                <Select
                  value={draft.sourceRegion}
                  onValueChange={(v) => update("sourceRegion", v as SourceDraft["sourceRegion"])}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {REGION_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        {opt.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>주제</Label>
                <Select value={draft.categoryId} onValueChange={(v) => update("categoryId", v)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Switch checked={draft.isActive} onCheckedChange={(v) => update("isActive", v)} />
              <Label>활성화</Label>
            </div>
          </div>

          <DialogFooter className="mt-4 flex-row gap-2">
            <Button
              variant="ghost"
              className="text-destructive hover:text-destructive mr-auto"
              onClick={() => setDeleteConfirmOpen(true)}
              disabled={isWorking}
            >
              삭제
            </Button>
            <Button variant="outline" onClick={onClose} disabled={isWorking}>
              취소
            </Button>
            <Button onClick={() => updateSource(draft)} disabled={isWorking}>
              {isUpdating ? "저장 중..." : "저장"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmModal
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title="소스를 삭제할까요?"
        description="삭제 후에는 이 소스의 뉴스가 더 이상 수집되지 않아요."
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          deleteSource();
          setDeleteConfirmOpen(false);
        }}
      />
    </>
  );
}
