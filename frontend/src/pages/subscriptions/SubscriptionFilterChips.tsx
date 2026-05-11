import { cn } from "@/utils/cn";
import { CHIP_FILTERS } from "./model/constants";
import type { SubscriptionFilter } from "./model/types";

interface SubscriptionFilterChipsProps {
  selected: SubscriptionFilter;
  counts: Record<SubscriptionFilter, number>;
  onSelect: (filter: SubscriptionFilter) => void;
}

export function SubscriptionFilterChips({
  selected,
  counts,
  onSelect,
}: SubscriptionFilterChipsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {CHIP_FILTERS.map((chip) => {
        const isSelected = selected === chip.value;
        const count = counts[chip.value] ?? 0;

        return (
          <button
            key={chip.value}
            type="button"
            onClick={() => onSelect(chip.value)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors",
              isSelected
                ? "bg-primary text-primary-foreground"
                : "border border-border bg-card text-foreground hover:bg-muted",
              chip.dimmed && !isSelected && "opacity-50",
            )}
          >
            {chip.label}
            <span
              className={cn(
                "text-xs tabular-nums",
                isSelected ? "text-primary-foreground/70" : "text-muted-foreground",
              )}
            >
              {count}
            </span>
          </button>
        );
      })}
    </div>
  );
}
