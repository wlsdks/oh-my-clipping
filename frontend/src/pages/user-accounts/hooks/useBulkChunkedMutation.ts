import { useState, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { userKeys } from "@/queries/userKeys";
import type { BulkReviewResult } from "@/types/user";

const DEFAULT_CHUNK_SIZE = 50;

export interface ChunkProgress {
  total: number;
  completed: number;
  succeeded: number;
  failed: number;
}

export interface AggregatedResult {
  succeeded: string[];
  failed: BulkReviewResult["failed"];
}

/** 배열을 chunkSize 단위로 분할한다. */
export function chunkArray<T>(arr: T[], chunkSize: number): T[][] {
  if (arr.length === 0) return [];
  const chunks: T[][] = [];
  for (let i = 0; i < arr.length; i += chunkSize) {
    chunks.push(arr.slice(i, i + chunkSize));
  }
  return chunks;
}

/** 여러 BulkReviewResult를 하나로 합친다. */
export function aggregateResults(results: BulkReviewResult[]): AggregatedResult {
  const succeeded: string[] = [];
  const failed: BulkReviewResult["failed"] = [];
  for (const r of results) {
    succeeded.push(...r.succeeded);
    failed.push(...r.failed);
  }
  return { succeeded, failed };
}

interface UseBulkChunkedMutationOptions {
  mutationFn: (ids: string[], note: string | null) => Promise<BulkReviewResult>;
  chunkSize?: number;
  onComplete: (result: AggregatedResult) => void;
  onPartialError: (result: AggregatedResult, error: unknown) => void;
}

export function useBulkChunkedMutation({
  mutationFn,
  chunkSize = DEFAULT_CHUNK_SIZE,
  onComplete,
  onPartialError,
}: UseBulkChunkedMutationOptions) {
  const qc = useQueryClient();
  const [progress, setProgress] = useState<ChunkProgress | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const cancelledRef = useRef(false);

  async function execute(ids: string[], note: string | null) {
    const chunks = chunkArray(ids, chunkSize);
    const allResults: BulkReviewResult[] = [];
    cancelledRef.current = false;
    setIsProcessing(true);
    setProgress({ total: ids.length, completed: 0, succeeded: 0, failed: 0 });

    try {
      for (const chunk of chunks) {
        if (cancelledRef.current) break;

        const result = await mutationFn(chunk, note);
        allResults.push(result);

        const agg = aggregateResults(allResults);
        setProgress({
          total: ids.length,
          completed: agg.succeeded.length + agg.failed.length,
          succeeded: agg.succeeded.length,
          failed: agg.failed.length,
        });
      }

      const finalResult = aggregateResults(allResults);
      onComplete(finalResult);
    } catch (error) {
      const partialResult = aggregateResults(allResults);
      onPartialError(partialResult, error);
    } finally {
      setIsProcessing(false);
      setProgress(null);
      qc.invalidateQueries({ queryKey: userKeys.all });
    }
  }

  function cancel() {
    cancelledRef.current = true;
  }

  return { execute, cancel, progress, isProcessing };
}
