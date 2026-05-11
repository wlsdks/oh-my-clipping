// frontend/src/pages/review-queue/ReviewSlideOver.tsx
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet";
import { ReviewDetailContent } from "./ReviewDetailContent";
import type { ReviewQueueItem } from "@/types/review";

interface ReviewSlideOverProps {
  item: ReviewQueueItem | null;
  isPending: boolean;
  onAction: (summaryId: string, action: "approve" | "exclude" | "review") => void;
  onClose: () => void;
  channelLabel?: string;
}

export function ReviewSlideOver({ item, isPending, onAction, onClose, channelLabel }: ReviewSlideOverProps) {
  return (
    <Sheet open={!!item} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent side="right" className="w-full sm:max-w-md overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="text-base font-semibold line-clamp-2">
            {item?.title ?? ""}
          </SheetTitle>
        </SheetHeader>
        {item && (
          <div className="mt-4">
            <ReviewDetailContent item={item} isPending={isPending} onAction={onAction} channelLabel={channelLabel} />
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
