import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

interface MetaDotProps {
  /** 렌더할 메타 항목. null/undefined/빈문자열은 자동 필터 */
  items: Array<string | null | undefined>;
  /** 구분자 (기본 ·) */
  separator?: string;
  className?: string;
  /** 각 항목 최대 길이 (chars). 초과 시 말줄임 (...) 추가 */
  itemMaxLength?: number;
}

function truncateItem(value: string, max?: number): string {
  if (!max || value.length <= max) return value;
  return `${value.slice(0, max)}…`;
}

/**
 * 카드 메타 정보를 `A · B · C` 형태로 일관되게 렌더.
 *
 * - `items` 중 null/undefined/빈문자열은 자동으로 제외한다.
 * - 구분자는 `text-border` 색상의 별도 span으로 렌더 — 텍스트와 구분되는 톤 유지.
 * - `itemMaxLength` 지정 시 각 항목을 말줄임 처리.
 */
export function MetaDot({
  items,
  separator = "·",
  className,
  itemMaxLength,
}: MetaDotProps): ReactNode {
  // null/undefined/빈문자열 정리
  const filtered = items
    .filter((v): v is string => typeof v === "string" && v.trim().length > 0)
    .map((v) => truncateItem(v, itemMaxLength));

  if (filtered.length === 0) return null;

  return (
    <span className={cn("inline-flex flex-wrap items-center gap-1.5", className)}>
      {filtered.map((item, idx) => (
        <span key={`${idx}-${item}`} className="inline-flex items-center gap-1.5">
          {idx > 0 && (
            <span aria-hidden className="text-border select-none">
              {separator}
            </span>
          )}
          <span className="truncate">{item}</span>
        </span>
      ))}
    </span>
  );
}
