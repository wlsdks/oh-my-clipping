import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { validateSlackChannelInput } from "@/shared/lib/slackChannel";
import type { Persona } from "@/types/persona";
import { personaService } from "@/services/personaService";
import { dashboardService } from "@/services/dashboardService";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import { useQuickSetupSubmit } from "./useQuickSetupSubmit";
import {
  type QuickSetupForm,
  type QuickSetupResult,
  createQuickSetupForm,
  isValidHttpUrl,
} from "./model/quickSetupTypes";
import { QuickSetupStepSource } from "./QuickSetupStepSource";
import { QuickSetupStepSiteFilter } from "./QuickSetupStepSiteFilter";
import { QuickSetupStepPersona } from "./QuickSetupStepPersona";
import { QuickSetupStepDetails } from "./QuickSetupStepDetails";
import { QuickSetupStepSlack } from "./QuickSetupStepSlack";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/utils/cn";

interface QuickSetupWizardProps {
  open: boolean;
  onClose: () => void;
  onComplete: () => void;
  isUserMode?: boolean;
  initialForm?: Partial<QuickSetupForm>;
}

type WizardStep = 1 | 2 | 3 | 4 | 5 | "done";
type SlackValidationTarget = "slack-channel-input" | "slack-primary-action";
type DetailsValidationTarget = "delivery-day-first";
type StepValidationTarget = SlackValidationTarget | DetailsValidationTarget;

interface StepValidationState {
  message: string;
  target: StepValidationTarget;
}

const STEPPER_ITEMS = [
  { num: 1 as const, label: "사이트" },
  { num: 2 as const, label: "뉴스 선택" },
  { num: 3 as const, label: "요약 스타일" },
  { num: 4 as const, label: "수신 옵션" },
  { num: 5 as const, label: "Slack" }
];

type CollectStatus = "idle" | "collecting" | "done" | "error";

const COLLECT_PHASES = ["뉴스 소스에 연결하는 중…", "최신 기사를 가져오는 중…", "AI가 요약을 준비하는 중…"];
const PHASE_INTERVAL = 2000;

function DoneScreen({
  result,
  onClose,
  onComplete,
  isUserMode,
  isEditMode
}: {
  result: QuickSetupResult;
  onClose: () => void;
  onComplete: () => void;
  isUserMode?: boolean;
  isEditMode?: boolean;
}) {
  const [collectStatus, setCollectStatus] = useState<CollectStatus>("idle");
  const [collectMsg, setCollectMsg] = useState("");
  const [phase, setPhase] = useState(0);
  const needsConnectionCheck = !isEditMode && result.sourceReady === false;

  async function handleCollect() {
    setCollectStatus("collecting");
    setCollectMsg("");
    setPhase(0);
    const interval = setInterval(() => {
      setPhase((prev) => (prev < COLLECT_PHASES.length - 1 ? prev + 1 : prev));
    }, PHASE_INTERVAL);
    const minWait = new Promise((r) => setTimeout(r, COLLECT_PHASES.length * PHASE_INTERVAL));
    try {
      const runFn = isUserMode
        ? (categoryId: string, data: { sendToSlack?: boolean; unsentOnly?: boolean }) =>
            userIntelligenceService.triggerPipeline(categoryId, data)
        : dashboardService.runPipeline;
      await Promise.all([runFn(result.categoryId, { sendToSlack: false, unsentOnly: true }), minWait]);
      clearInterval(interval);
      setCollectStatus("done");
    } catch {
      clearInterval(interval);
      setCollectStatus("error");
      setCollectMsg("뉴스 가져오기에 실패했어요. 나중에 다시 시도해 주세요.");
    }
  }

  return (
    <div className="space-y-4 py-2">
      {!needsConnectionCheck && (
        <h3 className="text-sm font-semibold">{isEditMode ? "변경 요청 완료!" : "세팅 완료!"}</h3>
      )}

      <div
        className={cn(
          "p-3 rounded-lg border text-sm",
          needsConnectionCheck
            ? "bg-[var(--status-neutral-bg)] border-[var(--status-neutral-bg)]"
            : "bg-[var(--status-success-bg)] border-[var(--status-success-bg)]"
        )}
      >
        {isEditMode ? (
          <>변경사항이 관리자에게 전달되었어요.</>
        ) : (
          <>&quot;{result.categoryName}&quot; 주제를 만들었어요.</>
        )}
      </div>

      {result.prefSaveWarning && (
        <div className="p-3 rounded-lg border bg-[var(--status-warning-bg)] border-[var(--status-warning-bg)] text-sm">
          {result.prefSaveWarning}
        </div>
      )}

      {isEditMode ? (
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">관리자 검토 후 적용돼요. 진행 상태에서 확인할 수 있어요.</p>
          <div className="flex items-center gap-2">
            {isUserMode && (
              <a
                className="inline-flex items-center justify-center px-4 py-2.5 text-sm font-medium bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                href="/user/history"
              >
                진행 상태 확인하기
              </a>
            )}
            <Button
              variant="outline"
              onClick={() => {
                onComplete();
                onClose();
              }}
            >
              닫기
            </Button>
          </div>
        </div>
      ) : isUserMode ? (
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            관리자 검토 후 뉴스 발송이 시작돼요. 진행 상태에서 확인할 수 있어요.
          </p>
          <div className="flex items-center gap-2">
            <a
              className="inline-flex items-center justify-center px-4 py-2.5 text-sm font-medium bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
              href="/user/history"
            >
              진행 상태 보기
            </a>
            <Button
              variant="outline"
              onClick={() => {
                onComplete();
                onClose();
              }}
            >
              닫기
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          {collectStatus === "idle" && (
            <>
              <p className="text-xs text-muted-foreground">등록한 채널에서 바로 뉴스를 가져올 수 있어요.</p>
              <Button onClick={handleCollect}>지금 바로 뉴스 가져오기</Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onComplete();
                  onClose();
                }}
              >
                나중에 할게요
              </Button>
            </>
          )}
          {collectStatus === "collecting" && (
            <div className="flex items-center gap-3 py-2">
              <div className="w-4 h-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
              <p className="text-sm text-muted-foreground">{COLLECT_PHASES[phase]}</p>
            </div>
          )}
          {collectStatus === "done" && (
            <>
              <div className="p-3 rounded-lg bg-[var(--status-success-bg)] border border-[var(--status-success-bg)] text-sm">
                뉴스를 가져왔어요!
              </div>
              <p className="text-xs text-muted-foreground">AI가 요약을 준비하고 있어요. 잠시 후 확인해 보세요.</p>
              <Button
                onClick={() => {
                  onComplete();
                  onClose();
                }}
              >
                확인
              </Button>
            </>
          )}
          {collectStatus === "error" && (
            <>
              <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-sm text-destructive">
                {collectMsg}
              </div>
              <div className="flex gap-2">
                <Button onClick={handleCollect}>다시 시도</Button>
                <Button
                  variant="outline"
                  onClick={() => {
                    onComplete();
                    onClose();
                  }}
                >
                  닫기
                </Button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

export function QuickSetupWizard({ open, onClose, onComplete, isUserMode, initialForm }: QuickSetupWizardProps) {
  const [step, setStep] = useState<WizardStep>(1);
  const [form, setForm] = useState<QuickSetupForm>(createQuickSetupForm());
  const [stepValidation, setStepValidation] = useState<StepValidationState | null>(null);
  const [result, setResult] = useState<QuickSetupResult | null>(null);
  const [savedPersonas, setSavedPersonas] = useState<Persona[]>([]);
  const [personaCustomEditing, setPersonaCustomEditing] = useState(false);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  const { submit, isPending: working } = useQuickSetupSubmit();

  function reloadPersonas() {
    const listFn = isUserMode ? personaService.getUserAll : personaService.getAll;
    listFn()
      .then(setSavedPersonas)
      .catch(() => {});
  }

  useEffect(() => {
    if (!open) return;
    setStep(1);
    setForm({ ...createQuickSetupForm(), ...initialForm });
    setStepValidation(null);
    setResult(null);
    setPersonaCustomEditing(false);
    setError(null);
    const listFn = isUserMode ? personaService.getUserAll : personaService.getAll;
    listFn()
      .then(setSavedPersonas)
      .catch(() => {});
  }, [open]);

  useEffect(() => {
    if (!open) return;
    requestAnimationFrame(() => {
      scrollAreaRef.current?.scrollTo({ top: 0, behavior: "auto" });
    });
  }, [open, step]);

  function handleChange(updates: Partial<QuickSetupForm>) {
    setForm((prev) => ({ ...prev, ...updates }));
    if (error) setError(null);
    if (stepValidation) setStepValidation(null);
  }

  function validateStep1(): string | null {
    const hasEntries = form.entries.length > 0;
    const hasManual = form.includeSource && form.sourceName.trim() && isValidHttpUrl(form.sourceUrl.trim());
    if (!hasEntries && !hasManual) return "기업 또는 키워드를 1개 이상 추가해 주세요.";
    return null;
  }

  function validateStep3(): string | null {
    // 프리셋 선택 시 personaName/personaPrompt 검증 스킵 (DB 프리셋이 이미 유효)
    if (form.selectedPresetId) return null;
    if (!form.createPersona) return null;
    if (!form.personaName.trim()) return "스타일 이름을 입력해 주세요.";
    if (!form.personaPrompt.trim()) return "AI 지시문을 입력해 주세요.";
    return null;
  }

  function validateStep4Details(): StepValidationState | null {
    if (isUserMode && form.deliveryPreset === "CUSTOM" && form.deliveryDays.length === 0) {
      return { message: "뉴스를 받을 요일을 1개 이상 선택해 주세요.", target: "delivery-day-first" };
    }
    return null;
  }

  function validateStep5Slack(): StepValidationState | null {
    if (form.slackDeliveryMode === "channel") {
      const slackValidation = validateSlackChannelInput(form.slackChannelId, { allowBlank: false });
      if (!slackValidation.isValid) {
        return { message: slackValidation.message || "Slack 채널을 확인해 주세요.", target: "slack-channel-input" };
      }
      if (!form.slackChannelConfirmed) {
        return { message: "Slack 채널을 확인하고 확정해 주세요.", target: "slack-primary-action" };
      }
    }
    return null;
  }

  function focusQuickSetupTarget(target: StepValidationTarget) {
    requestAnimationFrame(() => {
      const element = document.querySelector<HTMLElement>(`[data-focus-target="${target}"]`);
      if (!element) return;
      element.scrollIntoView({ behavior: "smooth", block: "center" });
      element.focus();
    });
  }

  function reportStepValidation(validation: StepValidationState) {
    setStepValidation(validation);
    toast.error(validation.message);
    focusQuickSetupTarget(validation.target);
  }

  function handleSubscribeDm(categoryId: string, categoryName: string) {
    toast.success(`"${categoryName}" 주제를 DM으로 구독했어요`);
    setResult({ categoryId, categoryName });
    setStep("done");
  }

  function handleNext() {
    setError(null);
    setStepValidation(null);
    if (step === 1) {
      setStep(2);
    } else if (step === 2) {
      const err = validateStep1();
      if (err) {
        setError(err);
        return;
      }
      setStep(3);
    } else if (step === 3) {
      const err = validateStep3();
      if (err) {
        setError(err);
        return;
      }
      setStep(4);
    } else if (step === 4) {
      const validation = validateStep4Details();
      if (validation) {
        reportStepValidation(validation);
        return;
      }
      setStep(5);
    }
  }

  function handlePrev() {
    setError(null);
    setStepValidation(null);
    if (step === 2) setStep(1);
    else if (step === 3) setStep(2);
    else if (step === 4) setStep(3);
    else if (step === 5) setStep(4);
  }

  function handleSubmit() {
    const validation = validateStep5Slack();
    if (validation) {
      reportStepValidation(validation);
      return;
    }
    setStepValidation(null);
    setError(null);
    submit(form)
      .then((res) => {
        if (res.status !== "rejected") {
          // Toast for partial is handled by the hook; advance to done screen
          setResult({ categoryId: "", categoryName: form.categoryName });
          setStep("done");
        }
      })
      .catch(() => {
        // Toast for error is handled by the hook
      });
  }

  const isStepDoneOrPast = (num: number) => step === "done" || (typeof step === "number" && step > num);
  const isLastStep = step === 5;

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && !working) onClose();
      }}
    >
      <DialogContent className="max-w-lg max-h-[80vh] flex flex-col p-0 overflow-hidden">
        <DialogHeader className="px-6 pt-6 pb-0 shrink-0">
          <DialogTitle className="text-base">{initialForm?.editRequestId ? "구독 설정 변경" : "빠른 세팅"}</DialogTitle>
          <DialogDescription className="sr-only">뉴스 구독을 설정합니다</DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-6" ref={scrollAreaRef}>
          {step !== "done" && !working && (
            <>
              <div className="flex gap-1.5 py-4">
                {STEPPER_ITEMS.map((s) => (
                  <div
                    key={s.num}
                    className={cn(
                      "flex-1 h-1.5 rounded-full transition-all duration-300",
                      step === s.num ? "bg-primary" : isStepDoneOrPast(s.num) ? "bg-primary/30" : "bg-muted"
                    )}
                  />
                ))}
              </div>

              {error && (
                <div className="mb-3 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-sm text-destructive">
                  {error}
                </div>
              )}

              {step === 1 && (
                <QuickSetupStepSiteFilter
                  form={form}
                  onChange={handleChange}
                  disabled={working}
                  isUserMode={isUserMode}
                />
              )}
              {step === 2 && (
                <QuickSetupStepSource
                  form={form}
                  onChange={handleChange}
                  disabled={working}
                  isUserMode={isUserMode}
                  onSubscribeDm={isUserMode ? handleSubscribeDm : undefined}
                />
              )}
              {step === 3 && (
                <QuickSetupStepPersona
                  form={form}
                  onChange={handleChange}
                  disabled={working}
                  savedPersonas={savedPersonas}
                  onPersonaSaved={reloadPersonas}
                  isUserMode={isUserMode}
                  onCustomEditing={setPersonaCustomEditing}
                />
              )}
              {step === 4 && (
                <QuickSetupStepDetails
                  form={form}
                  onChange={handleChange}
                  disabled={working}
                  isUserMode={isUserMode}
                  validationMessage={stepValidation?.message ?? null}
                  validationTarget={
                    stepValidation?.target === "delivery-day-first" ? stepValidation.target : null
                  }
                />
              )}
              {step === 5 && (
                <QuickSetupStepSlack
                  form={form}
                  onChange={handleChange}
                  disabled={working}
                  isEditMode={!!initialForm?.editRequestId}
                  isUserMode={isUserMode}
                />
              )}

              {!(step === 3 && personaCustomEditing) && (
                <div className="flex items-center justify-between mt-4 pt-4 border-t">
                  {step > 1 ? (
                    <Button variant="ghost" onClick={handlePrev} disabled={working}>
                      ← 이전
                    </Button>
                  ) : (
                    <span />
                  )}
                  <div className="flex gap-2">
                    {!isLastStep ? (
                      <Button onClick={handleNext} disabled={working}>
                        다음
                      </Button>
                    ) : (
                      <Button onClick={handleSubmit} disabled={working}>
                        {initialForm?.editRequestId ? "변경 요청" : "세팅 시작"}
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </>
          )}

          {step === "done" && result && (
            <DoneScreen
              result={result}
              onClose={onClose}
              onComplete={onComplete}
              isUserMode={isUserMode}
              isEditMode={!!initialForm?.editRequestId}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
