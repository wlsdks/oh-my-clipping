// frontend/src/pages/review-queue/ReviewDetailPanel.tsx
import { motion, AnimatePresence } from "framer-motion";
import { ReviewDetailContent } from "./ReviewDetailContent";
import type { ReviewQueueItem } from "@/types/review";

interface ReviewDetailPanelProps {
  item: ReviewQueueItem | null;
  isPending: boolean;
  onAction: (summaryId: string, action: "approve" | "exclude" | "review") => void;
  allDone?: boolean;
  channelLabel?: string;
}

export function ReviewDetailPanel({ item, isPending, onAction, allDone, channelLabel }: ReviewDetailPanelProps) {
  if (allDone) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
        모두 검토 완료!
      </div>
    );
  }

  if (!item) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
        왼쪽 목록에서 뉴스를 선택하세요
      </div>
    );
  }

  return (
    <div className="max-h-[calc(100vh-200px)] overflow-y-auto px-1">
      <AnimatePresence mode="wait">
        <motion.div
          key={item.summaryId}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          transition={{ duration: 0.15 }}
        >
          <ReviewDetailContent item={item} isPending={isPending} onAction={onAction} channelLabel={channelLabel} />
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
