import { X } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ReviewSidePanel } from "./ReviewSidePanel";
import { OperationSidePanel, type OperationPanelTab } from "./OperationSidePanel";
import type { SubscriptionPanelItem } from "./model/types";
import type { ApproveClippingRequestData } from "@/services/userService";

interface SubscriptionSidePanelProps {
  item: SubscriptionPanelItem | null;
  onClose: () => void;
  onApprove: (id: string, data: ApproveClippingRequestData) => void;
  onReject: (id: string, note: string) => void;
  isApproving: boolean;
  onEdit: (id: string, data: { name: string; slackChannelId?: string; maxItems: number }) => void;
  onPause: (id: string) => void;
  onResume: (id: string) => void;
  onTogglePublic: (id: string, isPublic: boolean) => void;
  onDelete: (id: string) => void;
  isCategoryWorking: boolean;
  /** xl 이상에서 인라인 렌더링 여부 */
  isInline: boolean;
  /** 운영 패널 활성 탭 */
  operationTab: OperationPanelTab;
  onOperationTabChange: (tab: OperationPanelTab) => void;
}

/** Sheet 패널 타이틀 결정 */
function panelTitle(item: SubscriptionPanelItem | null): string {
  if (!item) return "";
  if (item.kind === "request") return item.data.requestName;
  return item.data.name;
}

/** 패널 컨텐츠 (Sheet/Inline 공용) */
function PanelContent({
  item,
  onApprove,
  onReject,
  isApproving,
  onEdit,
  onPause,
  onResume,
  onTogglePublic,
  onDelete,
  isCategoryWorking,
  operationTab,
  onOperationTabChange,
}: Omit<SubscriptionSidePanelProps, "onClose" | "isInline">) {
  if (!item) return null;

  if (item.kind === "request") {
    return (
      <ReviewSidePanel
        item={item.data}
        onApprove={onApprove}
        onReject={onReject}
        isWorking={isApproving}
      />
    );
  }

  return (
    <OperationSidePanel
      item={item.data}
      onEdit={onEdit}
      onPause={onPause}
      onResume={onResume}
      onTogglePublic={onTogglePublic}
      onDelete={onDelete}
      isWorking={isCategoryWorking}
      activeTab={operationTab}
      onTabChange={onOperationTabChange}
    />
  );
}

export function SubscriptionSidePanel({
  item,
  onClose,
  isInline,
  ...rest
}: SubscriptionSidePanelProps) {
  // xl 이상: 인라인 패널
  if (isInline) {
    if (!item) return null;

    return (
      <div className="w-[480px] shrink-0 border-l bg-card flex flex-col" style={{ maxHeight: "calc(100vh - 64px)" }}>
        <div className="flex items-center justify-between border-b bg-card px-5 py-3 shrink-0">
          <h2 className="text-base font-semibold line-clamp-2">
            {panelTitle(item)}
          </h2>
          <button
            type="button"
            className="rounded-md p-1 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
            <span className="sr-only">닫기</span>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto min-h-0 p-5">
          <PanelContent item={item} {...rest} />
        </div>
      </div>
    );
  }

  // xl 미만: Sheet(오버레이) 패널
  return (
    <Sheet open={!!item} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent side="right" className="w-full sm:max-w-md overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="text-base font-semibold line-clamp-2">
            {panelTitle(item)}
          </SheetTitle>
        </SheetHeader>
        <div className="mt-4">
          <PanelContent item={item} {...rest} />
        </div>
      </SheetContent>
    </Sheet>
  );
}
