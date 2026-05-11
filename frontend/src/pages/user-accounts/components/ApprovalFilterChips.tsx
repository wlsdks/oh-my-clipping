import { cn } from "@/utils/cn";

type ApprovalFilter = "PENDING" | "REJECTED";

/**
 * 승인 탭 상단의 상태 필터 칩(PENDING/REJECTED) 세트다.
 *
 * 접근성: 라디오 그룹 패턴(`role="radiogroup"` + 각 칩 `role="radio"`)으로 구현해
 * 스크린리더가 "승인 대기/반려 중 하나를 선택하는 필터"임을 인지할 수 있게 한다.
 */
interface ApprovalFilterChipsProps {
  value: ApprovalFilter;
  onChange: (filter: ApprovalFilter) => void;
}

const CHIPS: { value: ApprovalFilter; label: string }[] = [
  { value: "PENDING", label: "승인 대기" },
  { value: "REJECTED", label: "반려" },
];

export function ApprovalFilterChips({ value, onChange }: ApprovalFilterChipsProps) {
  return (
    <div
      role="radiogroup"
      aria-label="승인 상태 필터"
      className="flex gap-2"
    >
      {CHIPS.map((chip) => {
        const selected = value === chip.value;
        return (
          <button
            key={chip.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(chip.value)}
            className={cn(
              "rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
              selected
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80",
            )}
          >
            {chip.label}
          </button>
        );
      })}
    </div>
  );
}
