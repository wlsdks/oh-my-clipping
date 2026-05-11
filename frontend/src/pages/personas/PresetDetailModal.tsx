import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage, extractStaleEditInfo } from "@/shared/lib/httpError";
import { useStaleEditStore } from "@/lib/staleEditBus";
import { useEditingPresence } from "@/hooks/useEditingPresence";
import { EditingPresenceBadge } from "@/components/shared/EditingPresenceBadge";
import { ChangeDetectionStrip } from "@/components/shared/ChangeDetectionStrip";
import { Clock, Undo2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog";
import { Form, FormField, FormItem, FormLabel, FormControl, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { TruncatedText } from "@/components/shared/TruncatedText";
import { personaService } from "@/services/personaService";
import { personaKeys } from "@/queries/personaKeys";
import { historyKeys } from "@/queries/historyKeys";
import { showSaveToastWithUndo } from "@/utils/saveToastUndo";
import { formatKoreanDateTime } from "@/utils/date";
import { cn } from "@/utils/cn";
import type { Persona } from "@/types/persona";
import { adminInputSchemas } from "@/shared/lib/inputSchemas";

const formSchema = z.object({
  // 공용 inputSchemas의 BE InputSanitizer 상한을 그대로 사용한다.
  name: adminInputSchemas.personaName,
  language: z.string(),
  description: adminInputSchemas.personaDescription,
  summaryStyle: adminInputSchemas.personaSummaryStyle,
  targetAudience: adminInputSchemas.personaTargetAudience,
  maxItems: z.coerce.number().int().min(1).max(20),
  systemPrompt: adminInputSchemas.personaSystemPrompt,
  previewTitle: adminInputSchemas.personaPreviewTitle,
  previewBody: adminInputSchemas.personaPreviewBody,
  isActive: z.boolean()
});

type FormValues = z.infer<typeof formSchema>;

interface PresetDetailModalProps {
  persona: Persona | null;
  subscriptionCount: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

type TabKey = "edit" | "history";

export function PresetDetailModal({ persona, subscriptionCount, open, onOpenChange }: PresetDetailModalProps) {
  const isCreate = !persona;
  const [activeTab, setActiveTab] = useState<TabKey>("edit");
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [rollbackConfirmVersion, setRollbackConfirmVersion] = useState<number | null>(null);
  const qc = useQueryClient();

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    values: persona
      ? {
          name: persona.name,
          language: persona.language,
          description: persona.description ?? "",
          summaryStyle: persona.summaryStyle ?? "",
          targetAudience: persona.targetAudience ?? "",
          maxItems: persona.maxItems,
          systemPrompt: persona.systemPrompt,
          previewTitle: persona.previewTitle ?? "",
          previewBody: persona.previewBody ?? "",
          isActive: persona.isActive
        }
      : {
          name: "",
          language: "ko",
          description: "",
          summaryStyle: "",
          targetAudience: "",
          maxItems: 5,
          systemPrompt: "",
          previewTitle: "",
          previewBody: "",
          isActive: true
        }
  });

  const { data: versions = [] } = useQuery({
    queryKey: persona ? personaKeys.versions(persona.id) : [],
    queryFn: () => (persona ? personaService.getVersions(persona.id) : Promise.resolve([])),
    enabled: !!persona && activeTab === "history"
  });

  // 편집 모달이 열려 있는 동안 다른 관리자의 변경을 감지하기 위해 단건을 짧은 주기로 폴링한다.
  const { data: liveDetail } = useQuery({
    queryKey: persona ? personaKeys.detail(persona.id) : [],
    queryFn: () => personaService.getById(persona!.id),
    enabled: !!persona && open,
    refetchInterval: persona && open ? 30_000 : false,
    refetchIntervalInBackground: false,
    retry: false
  });

  // 편집 presence heartbeat + 다른 편집자 목록 조회.
  const { otherEditors } = useEditingPresence({
    resourceType: "persona",
    resourceId: persona?.id,
    enabled: !!persona && open
  });

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) => {
      const payload = {
        name: values.name,
        description: values.description || null,
        systemPrompt: values.systemPrompt,
        summaryStyle: values.summaryStyle || null,
        targetAudience: values.targetAudience || null,
        maxItems: values.maxItems,
        language: values.language,
        isActive: values.isActive
      };
      if (persona) {
        // 낙관적 잠금: 편집 시점의 updated_at을 함께 전송해 경합을 감지한다.
        return personaService.update(persona.id, {
          ...payload,
          expectedUpdatedAt: persona.updatedAt
        });
      }
      return personaService.create(payload);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: personaKeys.lists() });
      if (persona) {
        qc.invalidateQueries({ queryKey: personaKeys.detail(persona.id) });
        qc.invalidateQueries({ queryKey: personaKeys.versions(persona.id) });
        qc.invalidateQueries({ queryKey: historyKeys.byResource("persona", persona.id) });
      } else {
        qc.invalidateQueries({ queryKey: personaKeys.detail(saved.id) });
      }
      // Create 직후엔 되돌릴 이전 revision이 없으므로 Undo action은 update 경로에서만 활성화한다.
      if (isCreate) {
        toast.success("템플릿을 생성했어요");
      } else {
        showSaveToastWithUndo({
          resource: "persona",
          savedId: saved.id,
          savedUpdatedAt: saved.updatedAt,
          onRestored: () => {
            qc.invalidateQueries({ queryKey: personaKeys.lists() });
            qc.invalidateQueries({ queryKey: personaKeys.detail(saved.id) });
            qc.invalidateQueries({ queryKey: historyKeys.byResource("persona", saved.id) });
          }
        });
      }
      onOpenChange(false);
    },
    onError: (err) => {
      // 낙관적 잠금 충돌이면 전역 모달을 통해 최신 불러오기 UX를 제공한다.
      const staleInfo = extractStaleEditInfo(err);
      if (staleInfo && persona) {
        useStaleEditStore.getState().show(
          staleInfo,
          async () => {
            await qc.invalidateQueries({ queryKey: personaKeys.lists() });
            await qc.invalidateQueries({ queryKey: personaKeys.detail(persona.id) });
          },
          { draftKey: `draft:persona:${persona.id}` }
        );
        return;
      }
      toast.error(userFriendlyMessage(err, "저장하지 못했어요"));
    }
  });

  const deleteMutation = useMutation({
    mutationFn: () => personaService.delete(persona!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: personaKeys.lists() });
      toast.success("템플릿을 삭제했어요");
      onOpenChange(false);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제하지 못했어요"))
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: number) => personaService.rollback(persona!.id, version),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: personaKeys.lists() });
      qc.invalidateQueries({ queryKey: personaKeys.detail(persona!.id) });
      qc.invalidateQueries({ queryKey: personaKeys.versions(persona!.id) });
      toast.success("이전 버전으로 되돌렸어요");
      onOpenChange(false);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "되돌리지 못했어요"))
  });

  function handleSave(values: FormValues) {
    saveMutation.mutate(values);
  }

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!v) setActiveTab("edit");
          onOpenChange(v);
        }}
      >
        <DialogContent className="max-w-2xl max-h-[min(80vh,720px)] flex flex-col p-0">
          <DialogHeader className="px-6 pt-6 pb-0">
            <DialogTitle className="leading-snug">
              {isCreate ? (
                "새 템플릿 만들기"
              ) : (
                <TruncatedText as="span" lines={2} className="block text-lg font-semibold">
                  {persona.name}
                </TruncatedText>
              )}
            </DialogTitle>
            <DialogDescription>
              {persona
                ? `v${persona.currentVersion} · 구독 ${subscriptionCount}건 · 마지막 수정 ${formatKoreanDateTime(persona.updatedAt)}`
                : "새 요약 템플릿의 이름, 언어, 지시문, 최대 기사 수를 설정해요."}
            </DialogDescription>
            {persona && otherEditors.length > 0 && (
              <div className="pt-2">
                <EditingPresenceBadge editors={otherEditors} />
              </div>
            )}
          </DialogHeader>
          {persona && (
            <div className="px-6 pt-2">
              <ChangeDetectionStrip
                initialUpdatedAt={persona.updatedAt}
                currentUpdatedAt={liveDetail?.updatedAt ?? persona.updatedAt}
                onReload={async () => {
                  await qc.invalidateQueries({ queryKey: personaKeys.lists() });
                  await qc.invalidateQueries({ queryKey: personaKeys.detail(persona.id) });
                }}
              />
            </div>
          )}

          {/* 탭 바 */}
          <div className="flex border-b px-6">
            <button
              type="button"
              className={cn(
                "px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px",
                activeTab === "edit"
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
              onClick={() => setActiveTab("edit")}
            >
              편집
            </button>
            <button
              type="button"
              className={cn(
                "px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px",
                activeTab === "history"
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
                isCreate && "opacity-50 pointer-events-none"
              )}
              onClick={() => !isCreate && setActiveTab("history")}
              disabled={isCreate}
            >
              <Clock className="inline-block w-3.5 h-3.5 mr-1 -mt-0.5" />
              이력
            </button>
          </div>

          {/* 탭 컨텐츠 */}
          <div className="flex-1 overflow-y-auto min-h-0 px-6 py-4">
            {activeTab === "edit" ? (
              <Form {...form}>
                <form id="preset-edit-form" onSubmit={form.handleSubmit(handleSave)} className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <FormField
                      control={form.control}
                      name="name"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>이름</FormLabel>
                          <FormControl>
                            <Input placeholder="예: 경영진 브리핑" {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="language"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>언어</FormLabel>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <FormControl>
                              <SelectTrigger>
                                <SelectValue />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              <SelectItem value="ko">한국어</SelectItem>
                              <SelectItem value="en">English</SelectItem>
                            </SelectContent>
                          </Select>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>

                  <FormField
                    control={form.control}
                    name="description"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>설명</FormLabel>
                        <FormControl>
                          <Input placeholder="예: 핵심만 짧게, 의사결정 중심" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <div className="grid grid-cols-2 gap-4">
                    <FormField
                      control={form.control}
                      name="summaryStyle"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>요약 스타일</FormLabel>
                          <FormControl>
                            <Input placeholder="예: 핵심 2줄 + 영향 1줄" {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="targetAudience"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>대상 독자</FormLabel>
                          <FormControl>
                            <Input placeholder="예: 경영진·임원" {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>

                  <FormField
                    control={form.control}
                    name="maxItems"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>최대 항목 수</FormLabel>
                        <FormControl>
                          <Input type="number" min={1} max={20} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="systemPrompt"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>AI 지시문</FormLabel>
                        <FormControl>
                          <Textarea
                            rows={5}
                            placeholder="AI가 뉴스를 요약할 때 사용할 지시문을 입력하세요"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="previewTitle"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>미리보기 제목</FormLabel>
                        <FormControl>
                          <Input placeholder="미리보기에 표시할 뉴스 제목" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="previewBody"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>미리보기 본문</FormLabel>
                        <FormControl>
                          <Textarea rows={4} placeholder="미리보기에 표시할 요약 본문" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="isActive"
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-3">
                        <FormLabel className="mt-0">활성 상태</FormLabel>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                            disabled={persona?.isPreset}
                          />
                        </FormControl>
                        {persona?.isPreset && (
                          <span className="text-xs text-muted-foreground">템플릿은 비활성화할 수 없어요</span>
                        )}
                      </FormItem>
                    )}
                  />
                </form>
              </Form>
            ) : (
              <VersionHistoryList
                versions={versions}
                currentVersion={persona?.currentVersion ?? 0}
                isRollingBack={rollbackMutation.isPending}
                onRollback={(version) => setRollbackConfirmVersion(version)}
              />
            )}
          </div>

          {/* 푸터 */}
          {activeTab === "edit" && (
            <DialogFooter className="px-6 pb-6 pt-2 flex-row justify-between">
              <div>
                {!isCreate && !persona?.isPreset && (
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span>
                          <Button
                            variant="destructive"
                            size="sm"
                            disabled={subscriptionCount > 0 || deleteMutation.isPending}
                            onClick={() => setDeleteConfirmOpen(true)}
                          >
                            삭제
                          </Button>
                        </span>
                      </TooltipTrigger>
                      {subscriptionCount > 0 && (
                        <TooltipContent>현재 {subscriptionCount}명이 사용 중입니다</TooltipContent>
                      )}
                    </Tooltip>
                  </TooltipProvider>
                )}
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => onOpenChange(false)}>
                  취소
                </Button>
                <Button type="submit" form="preset-edit-form" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? "저장 중..." : "저장"}
                </Button>
              </div>
            </DialogFooter>
          )}
        </DialogContent>
      </Dialog>

      {/* 삭제 확인 */}
      {persona && (
        <ConfirmModal
          open={deleteConfirmOpen}
          onOpenChange={setDeleteConfirmOpen}
          title="템플릿을 삭제할까요?"
          description="삭제 후에는 이 템플릿을 사용하는 구독의 요약 방식이 변경될 수 있어요."
          confirmLabel="삭제"
          variant="destructive"
          onConfirm={() => deleteMutation.mutate()}
        />
      )}

      {/* 이전 버전으로 되돌리기 확인 */}
      <ConfirmModal
        open={rollbackConfirmVersion !== null}
        onOpenChange={(o) => {
          if (!o) setRollbackConfirmVersion(null);
        }}
        title="이 버전으로 되돌릴까요?"
        description="현재 설정이 선택한 버전의 내용으로 바뀌어요"
        confirmLabel="되돌리기"
        onConfirm={() => {
          if (rollbackConfirmVersion !== null) {
            rollbackMutation.mutate(rollbackConfirmVersion);
            setRollbackConfirmVersion(null);
          }
        }}
      />
    </>
  );
}

/* ── 이력 목록 서브 컴포넌트 ── */

interface VersionHistoryListProps {
  versions: { version: number; changeSummary: string | null; createdAt: string }[];
  currentVersion: number;
  isRollingBack: boolean;
  onRollback: (version: number) => void;
}

function VersionHistoryList({ versions, currentVersion, isRollingBack, onRollback }: VersionHistoryListProps) {
  if (versions.length === 0) {
    return <p className="text-sm text-muted-foreground py-8 text-center">이력이 없어요</p>;
  }

  return (
    <div className="space-y-2">
      {versions.map((v) => (
        <div key={v.version} className="flex items-center justify-between rounded-xl border bg-card p-3">
          <div className="space-y-0.5">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium">v{v.version}</span>
              {v.version === currentVersion && (
                <span className="inline-flex items-center rounded-full bg-primary/10 text-primary px-2 py-0.5 text-[11px] font-medium">
                  현재
                </span>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              {formatKoreanDateTime(v.createdAt)}
              {v.changeSummary && ` — ${v.changeSummary}`}
            </p>
          </div>
          {v.version !== currentVersion && (
            <Button variant="ghost" size="sm" disabled={isRollingBack} onClick={() => onRollback(v.version)}>
              <Undo2 className="w-3.5 h-3.5 mr-1" />이 버전으로 되돌리기
            </Button>
          )}
        </div>
      ))}
    </div>
  );
}
