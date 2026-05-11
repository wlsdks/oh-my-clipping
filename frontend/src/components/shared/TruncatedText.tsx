import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

interface TruncatedTextProps {
  children: string;
  /** 표시할 최대 줄 수 (1=한 줄 truncate, 2~3=line-clamp) */
  lines?: 1 | 2 | 3;
  className?: string;
  /** true면 hover 시 전체 텍스트를 title 속성으로 노출 (기본 true) */
  showTitle?: boolean;
  /** 렌더 태그 선택 (기본 span) */
  as?: "span" | "div" | "p" | "h1" | "h2" | "h3" | "h4";
}

/**
 * 텍스트 말줄임 공용 컴포넌트.
 *
 * - `lines={1}`: CSS `truncate` (ellipsis).
 * - `lines={2|3}`: Tailwind `line-clamp-*`.
 * - 모든 경우 `break-words`로 레이아웃 깨짐 방지.
 * - 기본적으로 `title` 속성을 제공해 hover 시 전체 텍스트를 확인 가능.
 */
export function TruncatedText({
  children,
  lines = 1,
  className,
  showTitle = true,
  as: Component = "span",
}: TruncatedTextProps): ReactNode {
  const clampClass = lines === 1 ? "truncate" : `line-clamp-${lines}`;
  return (
    <Component
      className={cn(clampClass, "break-words", className)}
      title={showTitle ? children : undefined}
    >
      {children}
    </Component>
  );
}
