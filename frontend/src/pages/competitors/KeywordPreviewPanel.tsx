import { useMutation } from "@tanstack/react-query";
import { Loader2, ExternalLink, Search } from "lucide-react";
import { competitorService } from "@/services/competitorService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { Button } from "@/components/ui/button";
import type { KeywordPreviewResponse } from "@/types/competitor";

interface Props {
  name: string;
  aliases: string[];
  excludeKeywords: string[];
}

export function KeywordPreviewPanel({ name, aliases, excludeKeywords }: Props) {
  const { mutate, data, isPending, isError, error, reset } = useMutation<
    KeywordPreviewResponse,
    Error,
    string[]
  >({
    mutationFn: (kw) => competitorService.previewKeywords(kw),
  });

  const searchTerms = [name, ...aliases].filter((s) => s.trim().length > 0);
  const isEmpty = searchTerms.length === 0;

  const handlePreview = () => {
    reset();
    mutate(searchTerms);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" disabled={isEmpty || isPending} onClick={handlePreview}>
          {isPending ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <Search className="mr-1.5 h-3.5 w-3.5" />}
          미리보기
        </Button>
        {isEmpty && <span className="text-xs text-muted-foreground">이름을 입력하면 미리보기를 할 수 있어요</span>}
        {excludeKeywords.length > 0 && !isEmpty && <span className="text-xs text-muted-foreground">제외: {excludeKeywords.join(", ")}</span>}
      </div>
      {isError && <p className="text-xs text-destructive">{userFriendlyMessage(error, "미리보기에 실패했어요")}</p>}
      {data && data.items.length > 0 && (
        <ul className="space-y-1.5 rounded-lg border p-3">
          {data.items.slice(0, 5).map((item, idx) => (
            <li key={idx} className="flex items-start gap-2 text-xs">
              <ExternalLink className="mt-0.5 h-3 w-3 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <a href={item.link} target="_blank" rel="noopener noreferrer" className="line-clamp-1 font-medium text-primary hover:underline">{item.title}</a>
                {item.publishedAt && <span className="text-muted-foreground">{new Date(item.publishedAt).toLocaleDateString("ko-KR")}</span>}
              </div>
            </li>
          ))}
        </ul>
      )}
      {data && data.items.length === 0 && <p className="text-xs text-muted-foreground">{data.message || "검색 결과가 없어요"}</p>}
    </div>
  );
}
