import { useState } from "react";
import { ChevronDown, ChevronUp, Copy, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { translateRawMessage } from "@/shared/lib/httpError";
import { regionLabel } from "./sourceHelpers";
import { relativeTime } from "@/utils/date";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

interface ErrorDetailDrawerProps {
  source: Source | null;
  category?: Category;
  isPending: boolean;
  onClose: () => void;
  onRetry: (id: string) => void;
  onEdit: (source: Source) => void;
  onCompliance: (source: Source) => void;
  onArchive: (id: string) => void;
  onDelete: (id: string) => void;
}

export function ErrorDetailDrawer({
  source,
  category,
  isPending,
  onClose,
  onRetry,
  onEdit,
  onCompliance,
  onArchive,
  onDelete,
}: ErrorDetailDrawerProps) {
  const [showRawError, setShowRawError] = useState(false);
  const copyUrl = () => {
    if (!source) return;
    navigator.clipboard.writeText(source.url).then(() => {
      toast.success("URL이 복사됐어요");
    });
  };

  return (
    <Sheet open={source !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent side="right" className="w-full sm:max-w-md overflow-y-auto">
        {source && (
          <>
            <SheetHeader>
              <SheetTitle className="text-base font-semibold">{source.name}</SheetTitle>
            </SheetHeader>

            <div className="space-y-5 mt-4">
              {/* 기본 정보 */}
              <div className="space-y-2">
                <h4 className="text-xs font-medium text-muted-foreground">채널 정보</h4>
                <div className="rounded-lg border bg-muted/30 p-3 space-y-2 text-sm">
                  <div className="flex items-start gap-2">
                    <span className="text-xs text-muted-foreground shrink-0 w-14 pt-0.5">URL</span>
                    <div className="flex-1 flex items-start gap-1.5">
                      <a
                        href={source.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs text-primary hover:underline break-all"
                      >
                        {source.url}
                      </a>
                      <button
                        type="button"
                        onClick={copyUrl}
                        className="shrink-0 text-muted-foreground hover:text-foreground"
                        aria-label="URL 복사"
                      >
                        <Copy size={12} />
                      </button>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground w-14">카테고리</span>
                    <span className="text-xs">{category?.name ?? "-"}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground w-14">지역</span>
                    <span className="text-xs">{regionLabel(source.sourceRegion) || "-"}</span>
                  </div>
                </div>
              </div>

              {/* 오류 상세 */}
              <div className="space-y-2">
                <h4 className="text-xs font-medium text-muted-foreground">오류 상세</h4>
                <div className="rounded-lg border border-[var(--status-danger-text)]/30 bg-[var(--status-danger-bg)] p-3 space-y-2">
                  <p className="text-xs font-medium text-[var(--status-danger-text)]">
                    {source.crawlFailCount}회 연속 실패
                  </p>
                  {source.lastCrawlError && (
                    <>
                      <p className="text-xs text-[var(--status-danger-text)] break-words">
                        {translateRawMessage(
                          source.lastCrawlError,
                          "연결 중 알 수 없는 오류가 발생했어요",
                        )}
                      </p>
                      <button
                        type="button"
                        onClick={() => setShowRawError((v) => !v)}
                        className="inline-flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
                      >
                        원본 메시지
                        {showRawError ? <ChevronUp size={10} /> : <ChevronDown size={10} />}
                      </button>
                      {showRawError && (
                        <p className="text-[10px] text-muted-foreground font-mono break-words bg-background rounded px-2 py-1">
                          {source.lastCrawlError}
                        </p>
                      )}
                    </>
                  )}
                  <p className="text-[10px] text-muted-foreground">
                    마지막 확인 {relativeTime(source.updatedAt)}
                  </p>
                </div>
              </div>

              {/* 액션 */}
              <div className="space-y-2 pt-2 border-t">
                <Button
                  className="w-full"
                  disabled={isPending}
                  onClick={() => { onRetry(source.id); }}
                >
                  재시도
                </Button>
                <div className="grid grid-cols-2 gap-2">
                  <Button variant="outline" size="sm" onClick={() => { onEdit(source); onClose(); }}>
                    편집
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => { onCompliance(source); onClose(); }}>
                    저작권
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => { onArchive(source.id); onClose(); }}>
                    보관
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-destructive"
                    onClick={() => { onDelete(source.id); onClose(); }}
                  >
                    삭제
                  </Button>
                </div>
                <a
                  href={source.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:underline"
                >
                  원문 열기 <ExternalLink size={10} />
                </a>
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
