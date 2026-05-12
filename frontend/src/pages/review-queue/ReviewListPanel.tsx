// frontend/src/pages/review-queue/ReviewListPanel.tsx
import { useRef, useEffect } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Checkbox } from "@/components/ui/checkbox";
import { ReviewListItem } from "./ReviewListItem";
import type { ReviewQueueItem } from "@/types/review";

interface ReviewListPanelProps {
  items: ReviewQueueItem[];
  selectedId: string | null;
  showCategory: boolean;
  onSelect: (id: string) => void;
  /** 일괄 선택 모드 on/off. off면 체크박스 자체를 렌더하지 않는다. */
  batchMode?: boolean;
  checkedIds?: Set<string>;
  onToggle?: (id: string) => void;
  onToggleRange?: (id: string) => void;
  onSelectAll?: () => void;
  onClearAll?: () => void;
}

export function ReviewListPanel({
  items,
  selectedId,
  showCategory,
  onSelect,
  batchMode = false,
  checkedIds,
  onToggle,
  onToggleRange,
  onSelectAll,
  onClearAll,
}: ReviewListPanelProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // 선택 항목으로 스크롤
  useEffect(() => {
    if (!selectedId || !containerRef.current) return;
    const el = containerRef.current.querySelector(`[data-id="${selectedId}"]`);
    el?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [selectedId]);

  const checkedCount = checkedIds?.size ?? 0;
  const allChecked = batchMode && items.length > 0 && checkedCount === items.length;
  const someChecked = batchMode && checkedCount > 0 && checkedCount < items.length;

  return (
    <div className="space-y-2">
      {batchMode && items.length > 0 && (
        <div className="flex items-center justify-between px-1 py-1 text-xs">
          <label className="flex items-center gap-2 cursor-pointer select-none py-1.5">
            <Checkbox
              aria-label="모두 선택"
              checked={allChecked ? true : someChecked ? "indeterminate" : false}
              onCheckedChange={(checked) => {
                if (checked === true) onSelectAll?.();
                else onClearAll?.();
              }}
            />
            <span className="text-muted-foreground">
              {checkedCount > 0 ? `${checkedCount}건 선택됨` : "모두 선택"}
            </span>
          </label>
        </div>
      )}
      <div
        ref={containerRef}
        role="listbox"
        aria-label="뉴스 목록"
        className="max-h-[calc(100vh-240px)] overflow-y-auto space-y-1 pr-1"
      >
        <AnimatePresence initial={false}>
          {items.map((item) => {
            const isChecked = checkedIds?.has(item.summaryId) ?? false;
            return (
              <motion.div
                key={item.summaryId}
                data-id={item.summaryId}
                layout
                exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                transition={{ duration: 0.2 }}
                className="flex items-stretch gap-2"
              >
                {batchMode && (
                  // 체크박스는 독립 라벨로 감싸 44x44 터치 타겟 보장 + row onClick 전파 차단
                  <label
                    className="flex shrink-0 items-center justify-center w-11 cursor-pointer select-none"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <Checkbox
                      checked={isChecked}
                      aria-label={isChecked ? "선택 해제" : "선택"}
                      onClick={(e) => {
                        e.stopPropagation();
                        // Shift 키 누른 상태면 범위 선택
                        if (e.shiftKey) onToggleRange?.(item.summaryId);
                        else onToggle?.(item.summaryId);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === " " || e.key === "Enter") {
                          e.preventDefault();
                          e.stopPropagation();
                          if (e.shiftKey) onToggleRange?.(item.summaryId);
                          else onToggle?.(item.summaryId);
                        }
                      }}
                    />
                  </label>
                )}
                <div className="flex-1 min-w-0">
                  <ReviewListItem
                    item={item}
                    isSelected={selectedId === item.summaryId}
                    showCategory={showCategory}
                    onClick={() => onSelect(item.summaryId)}
                  />
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </div>
  );
}
