import { Rss } from "lucide-react";
import { Button } from "@/components/ui/button";

interface SourcesEmptyStateProps {
  onAddClick: () => void;
}

export function SourcesEmptyState({ onAddClick }: SourcesEmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted mb-4">
        <Rss size={28} className="text-muted-foreground" />
      </div>
      <h2 className="text-lg font-semibold">첫 뉴스 소스를 추가해보세요</h2>
      <p className="text-sm text-muted-foreground mt-2 max-w-sm">
        RSS 피드 URL을 입력하면 AI가 자동으로 기사를 수집해 요약해 드려요
      </p>
      <Button className="mt-6" onClick={onAddClick}>
        소스 추가
      </Button>
    </div>
  );
}
