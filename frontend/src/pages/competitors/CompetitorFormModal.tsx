import { useEffect, useState } from "react";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Plus, X } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { competitorService } from "@/services/competitorService";
import { competitorKeys } from "@/queries/competitorKeys";
import type { Competitor } from "@/types/competitor";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { KeywordPreviewPanel } from "./KeywordPreviewPanel";
import { adminInputSchemas } from "@/shared/lib/inputSchemas";

// 공용 inputSchemas를 사용해 BE `InputSanitizer` 상한과 동기화한다.
const schema = z.object({
  name: adminInputSchemas.competitorName,
  tier: z.enum(["DIRECT", "ADJACENT", "GLOBAL"]),
  aliases: z.array(adminInputSchemas.competitorAlias).max(10, "별칭은 최대 10개까지"),
  excludeKeywords: z.array(adminInputSchemas.competitorExcludeKeyword).max(20, "제외 키워드는 최대 20개까지"),
  rssFeeds: z
    .array(
      z.object({
        url: z.string().trim().url("올바른 URL을 입력하세요"),
        label: z.string().trim().max(100, "라벨은 최대 100자까지 입력할 수 있어요").optional()
      })
    )
    .max(5, "수동 RSS는 최대 5개까지")
});
type FormValues = z.infer<typeof schema>;
const TIER_OPTIONS = [
  { value: "DIRECT" as const, label: "직접 경쟁" },
  { value: "ADJACENT" as const, label: "인접" },
  { value: "GLOBAL" as const, label: "글로벌" },
];
interface Props { open: boolean; onClose: () => void; competitor?: Competitor | null; }

export function CompetitorFormModal({ open, onClose, competitor }: Props) {
  const isEditMode = !!competitor;
  const qc = useQueryClient();
  const [aliasInput, setAliasInput] = useState("");
  const [excludeInput, setExcludeInput] = useState("");
  const { register, handleSubmit, control, setValue, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", tier: "DIRECT", aliases: [], excludeKeywords: [], rssFeeds: [] },
  });
  const { fields: rssFields, append: appendRss, remove: removeRss } = useFieldArray({ control, name: "rssFeeds" });
  const aliases = watch("aliases");
  const excludeKeywords = watch("excludeKeywords");

  useEffect(() => {
    if (!open) return;
    if (competitor) {
      reset({ name: competitor.name, tier: competitor.tier, aliases: [...competitor.aliases], excludeKeywords: [...competitor.excludeKeywords], rssFeeds: competitor.rssFeeds.map((f) => ({ url: f.feedUrl, label: f.label ?? undefined })) });
    } else {
      reset({ name: "", tier: "DIRECT", aliases: [], excludeKeywords: [], rssFeeds: [] });
    }
    setAliasInput(""); setExcludeInput("");
  }, [open, competitor, reset]);

  const { mutate: submitForm, isPending } = useMutation({
    mutationFn: (data: FormValues) => isEditMode ? competitorService.update(competitor!.id, data) : competitorService.create(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: competitorKeys.lists() }); toast.success(isEditMode ? "경쟁사가 수정됐어요" : "경쟁사가 추가됐어요"); onClose(); },
    onError: (err) => toast.error(userFriendlyMessage(err, isEditMode ? "수정에 실패했어요" : "추가에 실패했어요")),
  });

  const addAlias = (raw: string) => { const t = raw.trim(); if (!t || aliases.length >= 5 || aliases.includes(t)) return; setValue("aliases", [...aliases, t], { shouldValidate: true }); setAliasInput(""); };
  const removeAlias = (idx: number) => { setValue("aliases", aliases.filter((_, i) => i !== idx), { shouldValidate: true }); };
  const handleAliasKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => { if (e.key === "Enter" || e.key === ",") { e.preventDefault(); addAlias(aliasInput); } };
  const addExcludeKeyword = (raw: string) => { const t = raw.trim(); if (!t || excludeKeywords.length >= 10 || excludeKeywords.includes(t)) return; setValue("excludeKeywords", [...excludeKeywords, t], { shouldValidate: true }); setExcludeInput(""); };
  const removeExcludeKeyword = (idx: number) => { setValue("excludeKeywords", excludeKeywords.filter((_, i) => i !== idx), { shouldValidate: true }); };
  const handleExcludeKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => { if (e.key === "Enter" || e.key === ",") { e.preventDefault(); addExcludeKeyword(excludeInput); } };

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[min(85vh,720px)] overflow-y-auto p-0">
        <DialogHeader className="px-6 pt-6">
          <DialogTitle>{isEditMode ? "경쟁사 수정" : "경쟁사 추가"}</DialogTitle>
          <DialogDescription>{isEditMode ? "경쟁사 정보를 수정하세요" : "모니터링할 경쟁사를 추가하세요"}</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit((data) => submitForm(data))} className="space-y-5 px-6 pb-2">
          <div className="space-y-1.5">
            <Label htmlFor="competitor-name">이름</Label>
            <Input id="competitor-name" placeholder="경쟁사 이름" {...register("name")} />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>등급</Label>
            <Select value={watch("tier")} onValueChange={(v) => setValue("tier", v as FormValues["tier"], { shouldValidate: true })}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>{TIER_OPTIONS.map((opt) => (<SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>))}</SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label>다른 이름 <span className="text-muted-foreground font-normal">(선택)</span></Label>
              <span className="text-xs text-muted-foreground">{aliases.length}/5</span>
            </div>
            <p className="text-xs text-muted-foreground">영문명, 모회사명 등 추가 검색에 사용돼요</p>
            <div className="flex flex-wrap gap-1.5">
              {aliases.map((alias, idx) => (<Badge key={`a-${idx}`} variant="secondary" className="gap-1 pr-1">{alias}<button type="button" onClick={() => removeAlias(idx)} className="rounded-full p-0.5 hover:bg-muted" aria-label={`${alias} 삭제`}><X className="h-3 w-3" /></button></Badge>))}
            </div>
            {aliases.length < 5 && <Input placeholder="영문명, 모회사명 등" value={aliasInput} onChange={(e) => setAliasInput(e.target.value)} onKeyDown={handleAliasKeyDown} onBlur={() => addAlias(aliasInput)} />}
            {errors.aliases && <p className="text-xs text-destructive">{typeof errors.aliases === "object" && "message" in errors.aliases ? errors.aliases.message : "별칭을 확인하세요"}</p>}
            <KeywordPreviewPanel name={watch("name")} aliases={aliases} excludeKeywords={excludeKeywords} />
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label>제외할 단어 <span className="text-muted-foreground font-normal">(선택)</span></Label>
              <span className="text-xs text-muted-foreground">{excludeKeywords.length}/10</span>
            </div>
            <p className="text-xs text-muted-foreground">채용, 후기, 할인 등 노이즈를 걸러내요</p>
            <div className="flex flex-wrap gap-1.5">
              {excludeKeywords.map((kw, idx) => (<Badge key={`e-${idx}`} variant="secondary" className="gap-1 pr-1">{kw}<button type="button" onClick={() => removeExcludeKeyword(idx)} className="rounded-full p-0.5 hover:bg-muted" aria-label={`${kw} 삭제`}><X className="h-3 w-3" /></button></Badge>))}
            </div>
            {excludeKeywords.length < 10 && <Input placeholder="채용, 후기, 할인 등" value={excludeInput} onChange={(e) => setExcludeInput(e.target.value)} onKeyDown={handleExcludeKeyDown} onBlur={() => addExcludeKeyword(excludeInput)} />}
            {errors.excludeKeywords && <p className="text-xs text-destructive">{typeof errors.excludeKeywords === "object" && "message" in errors.excludeKeywords ? errors.excludeKeywords.message : "제외 키워드를 확인하세요"}</p>}
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>수동 RSS <span className="text-muted-foreground font-normal">(선택)</span></Label>
              <span className="text-xs text-muted-foreground">{rssFields.length}/5</span>
            </div>
            {rssFields.map((field, idx) => (<div key={field.id} className="flex items-start gap-2"><div className="flex-1 space-y-1"><Input placeholder="https://example.com/rss" {...register(`rssFeeds.${idx}.url`)} />{errors.rssFeeds?.[idx]?.url && <p className="text-xs text-destructive">{errors.rssFeeds[idx]?.url?.message}</p>}</div><Input placeholder="라벨 (선택)" className="w-28" {...register(`rssFeeds.${idx}.label`)} /><Button type="button" variant="ghost" size="icon" className="h-9 w-9 shrink-0" onClick={() => removeRss(idx)} aria-label="RSS 삭제"><X className="h-4 w-4" /></Button></div>))}
            {rssFields.length < 5 && <Button type="button" variant="outline" size="sm" onClick={() => appendRss({ url: "", label: "" })}><Plus className="mr-1.5 h-3.5 w-3.5" />RSS 추가</Button>}
            {errors.rssFeeds && typeof errors.rssFeeds === "object" && "message" in errors.rssFeeds && <p className="text-xs text-destructive">{errors.rssFeeds.message}</p>}
          </div>
        </form>
        <DialogFooter className="px-6 pb-6">
          <Button variant="outline" onClick={onClose} disabled={isPending}>취소</Button>
          <Button onClick={handleSubmit((data) => submitForm(data))} disabled={isPending}>
            {isPending && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}{isPending ? "저장 중..." : "저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
