import { cn } from "@/utils/cn";

interface BannerProps {
  variant?: "info" | "warning" | "error" | "success";
  children: React.ReactNode;
  className?: string;
}

const VARIANT_CLASSES = {
  info: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border-[var(--status-neutral-bg)]",
  warning: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)] border-[var(--status-warning-bg)]",
  error: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)] border-[var(--status-danger-bg)]",
  success: "bg-[var(--status-success-bg)] text-[var(--status-success-text)] border-[var(--status-success-bg)]"
};

export function Banner({ variant = "info", children, className }: BannerProps) {
  return (
    <div className={cn("rounded-lg border px-4 py-3 text-sm", VARIANT_CLASSES[variant], className)}>{children}</div>
  );
}
