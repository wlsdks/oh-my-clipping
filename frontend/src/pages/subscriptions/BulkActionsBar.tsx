import { Play, Pause, X } from "lucide-react";
import { Button } from "@/components/ui/button";

interface BulkActionsBarProps {
  selectedCount: number;
  onActivate: () => void;
  onDeactivate: () => void;
  onClear: () => void;
  isWorking: boolean;
}

/** 선택된 항목에 대한 일괄 액션 스티키 바 */
export function BulkActionsBar({
  selectedCount,
  onActivate,
  onDeactivate,
  onClear,
  isWorking,
}: BulkActionsBarProps) {
  if (selectedCount === 0) return null;

  return (
    <div className="sticky bottom-4 z-20 mx-auto max-w-xl">
      <div className="flex items-center gap-3 rounded-xl border bg-card px-4 py-3 shadow-lg">
        <span className="text-sm font-medium text-foreground whitespace-nowrap">
          {selectedCount}개 선택
        </span>

        <div className="flex items-center gap-2 ml-auto">
          <Button
            size="sm"
            variant="outline"
            disabled={isWorking}
            onClick={onActivate}
          >
            <Play className="mr-1.5 h-3.5 w-3.5" />
            활성화
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={isWorking}
            onClick={onDeactivate}
          >
            <Pause className="mr-1.5 h-3.5 w-3.5" />
            비활성화
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onClear}
            className="h-8 w-8 p-0"
          >
            <X className="h-4 w-4" />
            <span className="sr-only">선택 해제</span>
          </Button>
        </div>
      </div>
    </div>
  );
}
