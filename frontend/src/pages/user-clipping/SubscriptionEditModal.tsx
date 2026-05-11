import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { motion, AnimatePresence } from "framer-motion";
import { ChevronDown, X } from "lucide-react";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import type { UpdateUserSubscriptionPreferenceRequest } from "@/services/userService";
import type { UserClippingRequest, UserSubscriptionPreference, DeliveryPreset } from "@/types/user";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from "@/components/ui/dialog";
import { cn } from "@/utils/cn";

const ALL_DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
const DAY_LABELS: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일"
};
const DELIVERY_SLOTS = [8, 12, 18] as const;
// 위자드(QuickSetupStepDetails)와 옵션 통일 — 신청/변경 간 일관성을 유지한다.
const MAX_ITEMS_OPTIONS = [1, 3, 5];

function nearestSlot(h: number): number {
  return DELIVERY_SLOTS.reduce((best, slot) => (Math.abs(slot - h) < Math.abs(best - h) ? slot : best));
}

function hourLabel(h: number): string {
  if (h === 0) return "자정";
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

interface EditForm {
  displayName: string;
  isActive: boolean;
  maxItems: number;
  excludeKeywords: string[];
  excludeInput: string;
  deliveryPreset: DeliveryPreset;
  deliveryDays: string[];
  deliveryHour: number;
}

function buildForm(requestName: string, pref: UserSubscriptionPreference | null): EditForm {
  return {
    displayName: requestName,
    isActive: pref?.isActive ?? true,
    maxItems: pref?.maxItems ?? 3,
    excludeKeywords: pref?.excludeKeywords ?? [],
    excludeInput: "",
    deliveryPreset: (pref?.deliveryPreset as DeliveryPreset) ?? "WEEKDAYS",
    deliveryDays: pref?.deliveryDays ?? ["MON", "TUE", "WED", "THU", "FRI"],
    // 백엔드는 user_delivery_schedules 글로벌 폴백을 merge 해 내려준다 (UserClippingRequestService#buildPreferenceView).
    // null 은 preference 자체가 없을 때만 발생 — 안전한 슬롯으로 떨어뜨린다.
    deliveryHour:
      pref?.deliveryHour != null
        ? (DELIVERY_SLOTS as readonly number[]).includes(pref.deliveryHour)
          ? pref.deliveryHour
          : nearestSlot(pref.deliveryHour)
        : DELIVERY_SLOTS[0]
  };
}

/** 선택 가능한 pill 버튼 — 통일된 스타일 */
function PillButton({
  selected,
  onClick,
  disabled,
  children,
  className = ""
}: {
  selected: boolean;
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`
        rounded-full px-3.5 py-1.5 text-sm font-medium border
        transition-all duration-150
        ${
          selected
            ? "bg-primary text-primary-foreground border-primary shadow-sm"
            : "border-border bg-card text-foreground hover:bg-muted/60 hover:border-border"
        }
        disabled:opacity-50
        ${className}
      `}
    >
      {children}
    </button>
  );
}

interface Props {
  open: boolean;
  request: UserClippingRequest;
  preference: UserSubscriptionPreference | null;
  onClose: () => void;
}

export function SubscriptionEditModal({ open, request, preference, onClose }: Props) {
  const qc = useQueryClient();
  const [form, setForm] = useState<EditForm>(() => buildForm(request.requestName, preference));

  useEffect(() => {
    if (open) setForm(buildForm(request.requestName, preference));
  }, [open, preference, request.requestName]);

  const { mutate: saveAll, isPending } = useMutation({
    mutationFn: async (data: UpdateUserSubscriptionPreferenceRequest & { displayName: string }) => {
      const nameChanged = data.displayName.trim() && data.displayName.trim() !== request.requestName;
      const results = await Promise.all([
        userService.updateSubscriptionPreferences(request.id, data),
        nameChanged ? userService.renameClippingRequest(request.id, data.displayName.trim()) : null
      ]);
      return results;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.subscriptionPreferences(request.id) });
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      toast.success("구독 설정을 저장했어요");
      onClose();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "저장하지 못했어요"))
  });

  function update(patch: Partial<EditForm>) {
    setForm((prev) => ({ ...prev, ...patch }));
  }

  function addKeyword() {
    const kw = form.excludeInput.trim();
    if (!kw || form.excludeKeywords.includes(kw)) return;
    update({ excludeKeywords: [...form.excludeKeywords, kw], excludeInput: "" });
  }

  function handlePresetChange(p: DeliveryPreset) {
    if (p === "WEEKDAYS") update({ deliveryPreset: p, deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"] });
    else if (p === "EVERYDAY") update({ deliveryPreset: p, deliveryDays: [...ALL_DAYS] });
    else update({ deliveryPreset: p });
  }

  function handleSubmit() {
    if (form.deliveryPreset === "CUSTOM" && form.deliveryDays.length === 0) {
      toast.warning("최소 1개 요일을 선택해주세요");
      return;
    }
    saveAll({
      displayName: form.displayName,
      isActive: form.isActive,
      maxItems: form.maxItems,
      excludeKeywords: form.excludeKeywords,
      deliveryPreset: form.deliveryPreset,
      deliveryDays: form.deliveryDays,
      deliveryHour: form.deliveryHour
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o && !isPending) onClose();
      }}
    >
      <DialogContent className="max-w-md p-0 overflow-hidden">
        <div className="max-h-[min(70vh,640px)] overflow-y-auto">
          {/* 헤더 */}
          <div className="px-6 pt-6 pb-2">
            <DialogHeader>
              <DialogTitle>구독 설정 변경</DialogTitle>
              <DialogDescription className="sr-only">구독 설정을 변경합니다</DialogDescription>
            </DialogHeader>
          </div>

          {/* 이름 편집 영역 */}
          <div className="px-6 pb-4">
            <SubscriptionNameEditor
              name={form.displayName}
              onChange={(name) => update({ displayName: name })}
              disabled={isPending}
            />
          </div>

          <div className="space-y-0">
            {/* 뉴스 설정 섹션 */}
            <section className="px-6 py-4 border-t border-border/50">
              <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-4">뉴스 설정</h3>
              <div className="space-y-5">
                {/* 최대 기사 수 */}
                <div className="space-y-2.5">
                  <Label className="text-sm">하루 최대 뉴스 수</Label>
                  <div className="flex flex-wrap gap-2">
                    {MAX_ITEMS_OPTIONS.map((n) => (
                      <PillButton
                        key={n}
                        selected={form.maxItems === n}
                        onClick={() => update({ maxItems: n })}
                        disabled={isPending}
                      >
                        {n}건
                      </PillButton>
                    ))}
                  </div>
                </div>

                {/* 제외 키워드 */}
                <div className="space-y-2.5">
                  <Label className="text-sm">제외 키워드</Label>
                  <AnimatePresence>
                    {form.excludeKeywords.length > 0 && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="flex flex-wrap gap-1.5 overflow-hidden"
                      >
                        {form.excludeKeywords.map((kw) => (
                          <span
                            key={kw}
                            className="inline-flex items-center gap-1 rounded-full bg-secondary px-2.5 py-0.5 text-xs text-secondary-foreground"
                          >
                            {kw}
                            <button
                              type="button"
                              aria-label={`${kw} 제거`}
                              onClick={() => update({ excludeKeywords: form.excludeKeywords.filter((k) => k !== kw) })}
                              disabled={isPending}
                              className="text-secondary-foreground/50 hover:text-secondary-foreground transition-colors"
                            >
                              <X className="h-3 w-3" />
                            </button>
                          </span>
                        ))}
                      </motion.div>
                    )}
                  </AnimatePresence>
                  <div className="flex gap-2">
                    <Input
                      value={form.excludeInput}
                      onChange={(e) => update({ excludeInput: e.target.value })}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          addKeyword();
                        }
                      }}
                      placeholder="예: 광고, 후원 — Enter로 추가"
                      disabled={isPending}
                      className="text-sm"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={addKeyword}
                      disabled={isPending}
                      className="shrink-0"
                    >
                      추가
                    </Button>
                  </div>
                </div>
              </div>
            </section>

            {/* 발송 설정 섹션 */}
            <section className="px-6 py-4 border-t border-border/50">
              <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-4">발송 설정</h3>
              <div className="space-y-5">
                {/* 발송 요일 */}
                <div className="space-y-2.5">
                  <Label className="text-sm">발송 요일</Label>
                  <div className="flex flex-wrap gap-2">
                    {(["WEEKDAYS", "EVERYDAY", "CUSTOM"] as DeliveryPreset[]).map((p) => (
                      <PillButton
                        key={p}
                        selected={form.deliveryPreset === p}
                        onClick={() => handlePresetChange(p)}
                        disabled={isPending}
                      >
                        {p === "WEEKDAYS" ? "평일만" : p === "EVERYDAY" ? "매일" : "직접 선택"}
                      </PillButton>
                    ))}
                  </div>
                  <AnimatePresence>
                    {form.deliveryPreset === "CUSTOM" && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        transition={{ duration: 0.2 }}
                        className="overflow-hidden"
                      >
                        <div className="flex gap-1.5 flex-wrap pt-1">
                          {ALL_DAYS.map((d) => (
                            <button
                              key={d}
                              type="button"
                              onClick={() => {
                                const next = form.deliveryDays.includes(d)
                                  ? form.deliveryDays.filter((x) => x !== d)
                                  : [...form.deliveryDays, d];
                                update({ deliveryDays: next });
                              }}
                              disabled={isPending}
                              className={`
                                rounded-full w-10 h-10 text-sm font-medium border
                                transition-all duration-150
                                ${
                                  form.deliveryDays.includes(d)
                                    ? "bg-primary text-primary-foreground border-primary shadow-sm"
                                    : "border-border bg-card hover:bg-muted/60"
                                }
                              `}
                            >
                              {DAY_LABELS[d]}
                            </button>
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>

                {/* 발송 시간 */}
                <div className="space-y-2.5">
                  <Label className="text-sm">발송 시간</Label>
                  <div className="flex flex-wrap gap-2">
                    {DELIVERY_SLOTS.map((h) => (
                      <PillButton
                        key={h}
                        selected={form.deliveryHour === h}
                        onClick={() => update({ deliveryHour: h })}
                        disabled={isPending}
                      >
                        {hourLabel(h)}
                      </PillButton>
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
                  </p>
                </div>
              </div>
            </section>
          </div>

          {/* 하단 버튼 */}
          <div className="px-6 py-4 border-t border-border/50 bg-muted/30">
            <DialogFooter>
              <Button variant="outline" onClick={onClose} disabled={isPending}>
                취소
              </Button>
              <Button onClick={handleSubmit} disabled={isPending}>
                {isPending ? "저장 중..." : "저장"}
              </Button>
            </DialogFooter>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** 구독 별칭 Popover 편집 — 로컬 상태만 변경, "저장" 버튼으로 일괄 저장 */
function SubscriptionNameEditor({
  name,
  onChange,
  disabled
}: {
  name: string;
  onChange: (name: string) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(name);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setDraft(name);
      setTimeout(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      }, 50);
    }
  }, [open, name]);

  function handleConfirm() {
    const trimmed = draft.trim();
    if (trimmed) onChange(trimmed);
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={disabled}>
        <button
          type="button"
          className={cn(
            "flex items-center gap-2.5 rounded-xl w-full text-left",
            "bg-muted/40 px-3.5 py-2.5",
            "group cursor-pointer",
            "hover:bg-muted/60 transition-colors duration-150",
            "ring-1 ring-transparent hover:ring-border/50"
          )}
        >
          <span className="text-sm text-foreground line-clamp-2 flex-1 min-w-0">{name}</span>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground/0 group-hover:text-muted-foreground transition-colors shrink-0" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] p-3">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">구독 이름</label>
          <Input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="예: 핀테크 뉴스"
            maxLength={60}
            disabled={disabled}
            className="text-sm"
            onKeyDown={(e) => {
              if (e.key === "Enter") handleConfirm();
              if (e.key === "Escape") setOpen(false);
            }}
          />
          <span className="text-[11px] text-muted-foreground pt-1">Enter로 확인 · Esc로 취소</span>
        </div>
      </PopoverContent>
    </Popover>
  );
}
