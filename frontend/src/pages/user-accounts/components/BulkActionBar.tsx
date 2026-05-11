import { Button } from "@/components/ui/button";
import { Check, Loader2, X } from "lucide-react";
import type { ChunkProgress } from "../hooks/useBulkChunkedMutation";

interface BulkActionBarProps {
  selectedCount: number;
  onBulkApprove: () => void;
  onBulkReject: () => void;
  isWorking: boolean;
  progress?: ChunkProgress | null;
}

export function BulkActionBar({
  selectedCount,
  onBulkApprove,
  onBulkReject,
  isWorking,
  progress,
}: BulkActionBarProps) {
  if (selectedCount === 0 && !progress) return null;

  if (progress) {
    const pct = progress.total > 0 ? Math.round((progress.completed / progress.total) * 100) : 0;
    return (
      <div className="rounded-lg border bg-muted/50 px-4 py-3 space-y-2">
        <div className="flex items-center justify-between text-sm">
          <span className="font-medium">
            {progress.completed}/{progress.total} 처리 중…
          </span>
          <span className="text-muted-foreground">{pct}%</span>
        </div>
        <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
          <div
            className="h-full bg-primary rounded-full transition-all duration-300"
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3 rounded-lg border bg-muted/50 px-4 py-2.5">
      <span className="text-sm font-medium">{selectedCount}건 선택됨</span>
      <div className="flex items-center gap-2 ml-auto">
        <Button size="sm" onClick={onBulkApprove} disabled={isWorking}>
          {isWorking ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Check className="h-4 w-4 mr-1" />}
          {isWorking ? "처리 중…" : "일괄 승인"}
        </Button>
        <Button size="sm" variant="destructive" onClick={onBulkReject} disabled={isWorking}>
          {isWorking ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <X className="h-4 w-4 mr-1" />}
          {isWorking ? "처리 중…" : "일괄 반려"}
        </Button>
      </div>
    </div>
  );
}
