import { Globe, Lock } from "lucide-react";
import { Switch } from "@/components/ui/switch";
import type { Category } from "@/types/category";

interface SettingsVisibilitySectionProps {
  item: Category;
  isWorking: boolean;
  onTogglePublic: (id: string, isPublic: boolean) => void;
}

/**
 * 구독 공개/비공개 토글 섹션.
 * 비공개 전환 시 기존 구독자는 유지된다는 안내 문구를 하단에 노출한다.
 */
export function SettingsVisibilitySection({
  item,
  isWorking,
  onTogglePublic,
}: SettingsVisibilitySectionProps) {
  return (
    <div className="rounded-lg border border-border bg-muted/30">
      <div className="flex items-center justify-between px-3 py-2.5">
        <div className="flex items-center gap-2">
          {item.isPublic ? (
            <Globe className="h-4 w-4 text-[var(--status-success-text)]" />
          ) : (
            <Lock className="h-4 w-4 text-muted-foreground" />
          )}
          <span className="text-sm font-medium">구독 가능한 주제에 공개</span>
        </div>
        <Switch
          checked={item.isPublic}
          onCheckedChange={(checked) => onTogglePublic(item.id, checked)}
          disabled={isWorking}
        />
      </div>
      {!item.isPublic && (
        <p className="text-xs text-muted-foreground px-3 pb-2.5">
          비공개 — 기존 구독자는 유지됩니다
        </p>
      )}
    </div>
  );
}
