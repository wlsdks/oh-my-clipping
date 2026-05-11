import { useState } from "react";
import type { UndeliveredDigest } from "@/types/insight";
import { ArticleDetailModal } from "@/components/shared/ArticleDetailModal";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";

interface UndeliveredDigestModalProps {
  open: boolean;
  onClose: () => void;
  digests: UndeliveredDigest[];
}

const STATUS_LABEL: Record<string, string> = {
  ABANDONED: "발송 실패",
  STALE: "만료됨",
  FAILED: "재시도 중",
};

/**
 * 미전달 다이제스트 상세 모달.
 * 카테고리+시간별로 그룹화된 미전달 기사 목록을 표시한다.
 * 각 기사를 클릭하면 ArticleDetailModal(subscription)을 열어 상세를 확인할 수 있다.
 */
export function UndeliveredDigestModal({
  open,
  onClose,
  digests,
}: UndeliveredDigestModalProps) {
  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null);

  return (
    <>
      <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
        <DialogContent className="max-w-lg max-h-[70vh] flex flex-col p-0">
          <DialogHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <DialogTitle>전달되지 않은 뉴스 요약</DialogTitle>
            <DialogDescription>
              발송에 실패한 뉴스 요약을 확인할 수 있어요. 기사를 클릭하면 내용을 볼 수 있어요.
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 min-h-0 overflow-y-auto px-6 py-4 space-y-5">
            {digests.map((digest) => {
              const statusLabel = STATUS_LABEL[digest.status] ?? digest.status;
              return (
                <div key={digest.deliveryLogId} className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-semibold">{digest.categoryName}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {digest.deliveryDate} {digest.deliveryTimeLabel} · {statusLabel}
                        {digest.retryCount > 0 && (
                          <span> ({digest.retryCount}/{digest.maxRetries}회 시도)</span>
                        )}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground shrink-0">
                      {digest.articleCount}건
                    </span>
                  </div>

                  {digest.articles.length > 0 ? (
                    <div className="rounded-md border divide-y">
                      {digest.articles.map((article) => (
                        <button
                          key={article.summaryId}
                          type="button"
                          className="w-full text-left p-3 space-y-1 hover:bg-muted/50 transition-colors"
                          onClick={() => setSelectedArticleId(article.summaryId)}
                        >
                          <p className="text-sm font-medium line-clamp-2">{article.title}</p>
                          {article.summary && (
                            <p className="text-xs text-muted-foreground line-clamp-2">
                              {article.summary}
                            </p>
                          )}
                        </button>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground pl-1">기사 정보가 없어요</p>
                  )}
                </div>
              );
            })}
          </div>
        </DialogContent>
      </Dialog>

      <ArticleDetailModal
        variant="subscription"
        articleId={selectedArticleId}
        onClose={() => setSelectedArticleId(null)}
      />
    </>
  );
}
