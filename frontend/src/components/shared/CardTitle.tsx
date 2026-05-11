import type { ReactNode } from "react";
import { cn } from "@/utils/cn";
import { TruncatedText } from "./TruncatedText";

interface CardTitleProps {
  children: string;
  /** 우측에 배치할 뱃지/버튼 슬롯 */
  rightSlot?: ReactNode;
  /** 텍스트 크기 변형 */
  size?: "sm" | "md" | "lg";
  /** 제목 최대 줄 수 (기본 1) */
  lines?: 1 | 2;
  className?: string;
  titleClassName?: string;
}

const SIZE_CLASSES: Record<NonNullable<CardTitleProps["size"]>, string> = {
  sm: "text-sm font-semibold",
  md: "text-base font-semibold",
  lg: "text-lg font-semibold",
};

/**
 * 카드 상단 `제목 + 우측 뱃지` 패턴 표준화.
 *
 * - 제목은 `TruncatedText`로 말줄임 + title 속성 자동.
 * - `min-w-0` + `justify-between`으로 긴 제목이 뱃지를 밀어내지 않는다.
 * - `rightSlot`은 `shrink-0`으로 축소 방지.
 */
export function CardTitle({
  children,
  rightSlot,
  size = "sm",
  lines = 1,
  className,
  titleClassName,
}: CardTitleProps): ReactNode {
  return (
    <div className={cn("flex items-start justify-between gap-3", className)}>
      <TruncatedText
        as="h3"
        lines={lines}
        className={cn("min-w-0 flex-1 text-foreground", SIZE_CLASSES[size], titleClassName)}
      >
        {children}
      </TruncatedText>
      {rightSlot && <div className="shrink-0">{rightSlot}</div>}
    </div>
  );
}
