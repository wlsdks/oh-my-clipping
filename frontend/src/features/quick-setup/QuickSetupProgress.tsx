import { Check, X, Circle } from "lucide-react";
import type { SetupProgressStep } from "./model/quickSetupTypes";
import { cn } from "@/utils/cn";

interface QuickSetupProgressProps {
  steps: SetupProgressStep[];
}

export function QuickSetupProgress({ steps }: QuickSetupProgressProps) {
  return (
    <div className="space-y-2 py-2">
      {steps.map((step) => (
        <div key={step.id} className="flex items-start gap-3">
          <div
            className={cn(
              "w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shrink-0 mt-0.5",
              step.status === "done" && "bg-[var(--status-success-text)] text-white",
              step.status === "error" && "bg-[var(--status-danger-text)] text-white",
              step.status === "running" && "bg-[var(--status-neutral-text)] text-white animate-pulse",
              step.status === "pending" && "bg-muted text-muted-foreground"
            )}
          >
            {step.status === "done" ? <Check className="h-3 w-3" /> : step.status === "error" ? <X className="h-3 w-3" /> : step.status === "running" ? <Circle className="h-2 w-2 fill-current" /> : <Circle className="h-2 w-2" />}
          </div>
          <div className="min-w-0">
            <div
              className={cn(
                "text-sm",
                step.status === "done" && "text-foreground",
                step.status === "error" && "text-destructive",
                step.status === "running" && "text-foreground font-medium",
                step.status === "pending" && "text-muted-foreground"
              )}
            >
              {step.label}
            </div>
            {step.status === "error" && step.errorMessage && (
              <div className="text-xs text-destructive mt-0.5">{step.errorMessage}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
