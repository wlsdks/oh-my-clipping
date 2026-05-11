import { cn } from "@/utils/cn";

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
  /** 아이콘에 clipping-breathe 애니메이션 적용 여부 */
  breathing?: boolean;
}

export function EmptyState({ title, description, icon, action, className, breathing }: EmptyStateProps) {
  const shouldBreathe = breathing ?? !action;
  return (
    <div className={cn("flex flex-col items-center justify-center py-16 text-center", className)}>
      {icon && (
        <div className={cn("mb-3 text-muted-foreground", shouldBreathe && "animate-[clipping-breathe_3s_ease-in-out_infinite]")}>
          {icon}
        </div>
      )}
      <p className="text-lg font-medium text-foreground">{title}</p>
      {description && <p className="mt-1 text-sm text-muted-foreground max-w-sm">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
