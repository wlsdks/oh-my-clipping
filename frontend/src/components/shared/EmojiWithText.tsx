import type { ReactNode } from "react";
import { cn } from "@/utils/cn";
import { TruncatedText } from "./TruncatedText";

interface EmojiWithTextProps {
  /** 표시할 이모지. falsy면 fallbackIcon을 렌더 */
  emoji?: string | null;
  text: string;
  /** emoji가 없을 때 렌더할 아이콘 (예: Lucide 아이콘) */
  fallbackIcon?: ReactNode;
  /** 이모지 박스 크기: sm=24px, md=36px(기본), lg=48px */
  size?: "sm" | "md" | "lg";
  /** 텍스트 줄 수 (기본 1) */
  lines?: 1 | 2;
  className?: string;
  /** 텍스트 영역 className 오버라이드 */
  textClassName?: string;
}

const SIZE_CLASSES: Record<NonNullable<EmojiWithTextProps["size"]>, { box: string; emoji: string }> = {
  sm: { box: "h-6 w-6", emoji: "text-base" },
  md: { box: "h-9 w-9", emoji: "text-xl" },
  lg: { box: "h-12 w-12", emoji: "text-2xl" },
};

/**
 * 이모지 + 텍스트 조합을 baseline 정렬 문제 없이 렌더.
 *
 * - 이모지 박스는 고정 크기 + flex 중앙 정렬 + `leading-none`으로 iOS Safari에서
 *   이모지가 과도하게 커 보이는 문제를 회피한다.
 * - 텍스트는 `TruncatedText`를 사용해 말줄임 + title 속성 자동 제공.
 * - `emoji`가 비어 있으면 `fallbackIcon`을 렌더한다.
 */
export function EmojiWithText({
  emoji,
  text,
  fallbackIcon,
  size = "md",
  lines = 1,
  className,
  textClassName,
}: EmojiWithTextProps): ReactNode {
  const sizes = SIZE_CLASSES[size];
  const hasEmoji = Boolean(emoji && emoji.trim().length > 0);

  return (
    <span className={cn("inline-flex min-w-0 items-center gap-2", className)}>
      <span
        aria-hidden={hasEmoji ? true : undefined}
        className={cn(
          "inline-flex shrink-0 items-center justify-center leading-none",
          sizes.box,
        )}
      >
        {hasEmoji ? <span className={sizes.emoji}>{emoji}</span> : fallbackIcon}
      </span>
      <TruncatedText lines={lines} className={cn("min-w-0 flex-1", textClassName)}>
        {text}
      </TruncatedText>
    </span>
  );
}
