import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Play, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { pipelineService } from "@/services/pipelineService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { Category } from "@/types/category";

interface PipelineControlsProps {
  categories: Category[];
  selectedCategoryId: string;
  onCategoryChange: (id: string) => void;
  onRunStarted: (runId: string) => void;
  isRunning: boolean;
}

export function PipelineControls({
  categories,
  selectedCategoryId,
  onCategoryChange,
  onRunStarted,
  isRunning,
}: PipelineControlsProps) {
  const { mutate: executePipeline, isPending } = useMutation({
    mutationFn: () =>
      pipelineService.execute({
        categoryId: selectedCategoryId,
        sendToSlack: true,
      }),
    onSuccess: (data) => {
      onRunStarted(data.runId);
      toast.success("파이프라인 실행이 시작됐어요");
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "파이프라인을 실행하지 못했어요"));
    },
  });

  const disabled = !selectedCategoryId || isPending || isRunning;

  function handleExecute() {
    if (!selectedCategoryId) {
      toast.warning("카테고리를 선택하세요");
      return;
    }
    executePipeline();
  }

  return (
    <div className="flex items-center gap-3 rounded-2xl bg-card p-4 shadow-sm">
      <Select value={selectedCategoryId} onValueChange={onCategoryChange}>
        <SelectTrigger className="w-52">
          <SelectValue placeholder="카테고리 선택" />
        </SelectTrigger>
        <SelectContent>
          {categories.map((c) => (
            <SelectItem key={c.id} value={c.id}>
              {c.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Button disabled={disabled} onClick={handleExecute}>
        {isPending || isRunning ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            실행 중...
          </>
        ) : (
          <>
            <Play className="mr-2 h-4 w-4" />
            파이프라인 실행
          </>
        )}
      </Button>
    </div>
  );
}
