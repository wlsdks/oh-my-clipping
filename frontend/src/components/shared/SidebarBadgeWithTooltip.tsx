import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { useCanHover } from "@/hooks/useCanHover";
import { cn } from "@/utils/cn";

export interface SidebarBadgeWithTooltipProps {
  count: number;
  tooltipText: string;
  variant?: "default" | "destructive";
  ariaLabel: string;
}

/**
 * 사이드바 메뉴 항목 우측의 카운트 배지.
 * - hover 가능한 디바이스에서는 Radix Tooltip 으로 urgency 상세를 노출한다.
 * - 터치/모바일에서는 툴팁 없이 배지 숫자만 표시한다 (탭=네비게이션).
 *
 * 키보드 포커스 만으로는 툴팁이 열리지 않는다. Radix Trigger 가 내부 `<span>`
 * 에 연결돼 있지만 span 은 탭 순서에 없고, 상위 Link 가 포커스를 받을 때는 Trigger
 * 가 아니라 Link 에 포커스가 가기 때문. 툴팁 정보는 보조 정보이며, 배지 숫자와
 * aria-label 은 Link 의 accessible name 에 포함돼 스크린리더에 그대로 전달된다.
 *
 * 사용 전제: 상위에서 `<TooltipProvider delayDuration={200}>` 로 감싸져 있어야
 * delay-skip 동작이 정상이다. `Sidebar.tsx` 의 `<aside>` 루트에서 제공한다.
 */
export function SidebarBadgeWithTooltip({
  count,
  tooltipText,
  variant = "default",
  ariaLabel,
}: SidebarBadgeWithTooltipProps) {
  const canHover = useCanHover();

  const badge = (
    <span
      data-testid="sidebar-badge"
      aria-label={ariaLabel}
      className={cn(
        "ml-auto inline-flex items-center justify-center rounded-full px-1.5 py-0.5 text-[10px] font-medium min-w-[18px]",
        variant === "destructive"
          ? "bg-destructive text-destructive-foreground"
          : "bg-primary text-primary-foreground"
      )}
    >
      {count}
    </span>
  );

  if (!canHover) return badge;

  return (
    <Tooltip>
      <TooltipTrigger asChild>{badge}</TooltipTrigger>
      <TooltipContent side="right" className="max-w-xs text-xs leading-relaxed">
        {tooltipText}
      </TooltipContent>
    </Tooltip>
  );
}
