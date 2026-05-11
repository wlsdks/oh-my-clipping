import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import type { Category } from "@/types/category";
import { EditableTextRow, EditablePillRow } from "./SettingsTabRows";

interface SettingsBasicSectionProps {
  item: Category;
  isWorking: boolean;
  onSaveField: (patch: Partial<{ name: string; slackChannelId: string; maxItems: number }>) => void;
}

/**
 * 구독 기본 설정 섹션 — 이름, Slack 채널, 최대 기사 수.
 * 각 필드는 Popover 안에서 편집되며, 저장 시 onSaveField 로 병합된 patch 가 전달된다.
 */
export function SettingsBasicSection({ item, isWorking, onSaveField }: SettingsBasicSectionProps) {
  const { formatChannel } = useSlackChannelMap();

  return (
    <div className="rounded-lg border border-border bg-muted/30 divide-y divide-border">
      <EditableTextRow
        label="이름"
        value={item.name}
        popoverLabel="이름"
        placeholder="구독 이름을 입력하세요"
        isWorking={isWorking}
        onSave={(v) => onSaveField({ name: v })}
      />
      <EditableTextRow
        label="채널"
        value={item.slackChannelId ? formatChannel(item.slackChannelId) : "—"}
        rawValue={item.slackChannelId ?? ""}
        popoverLabel="Slack 채널 ID"
        placeholder="C0123456789"
        isWorking={isWorking}
        onSave={(v) => onSaveField({ slackChannelId: v || undefined })}
      />
      <EditablePillRow
        label="최대 기사"
        value={item.maxItems}
        isWorking={isWorking}
        onSave={(v) => onSaveField({ maxItems: v })}
      />
    </div>
  );
}
