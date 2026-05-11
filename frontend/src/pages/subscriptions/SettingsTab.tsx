import type { Category } from "@/types/category";
import { SettingsBasicSection } from "./SettingsBasicSection";
import { SettingsMetadataSection } from "./SettingsMetadataSection";
import { SettingsOrganizationsSection } from "./SettingsOrganizationsSection";
import { SettingsVisibilitySection } from "./SettingsVisibilitySection";
import { SettingsKeywordsSection } from "./SettingsKeywordsSection";

interface SettingsTabProps {
  item: Category;
  onEdit: (id: string, data: { name: string; slackChannelId?: string; maxItems: number }) => void;
  onTogglePublic: (id: string, isPublic: boolean) => void;
  isWorking: boolean;
  onOpenKeywordDrawer: () => void;
}

/**
 * 구독 상세 사이드패널의 "설정" 탭 루트 컨테이너.
 * 순수 orchestration — 각 섹션 컴포넌트에 데이터/콜백을 나눠 전달한다.
 *
 * 섹션 구조:
 *  1. 기본 설정 (이름/채널/최대 기사)
 *  2. 분석 용도 metadata (목적/배경/문제)
 *  3. 관련 조직
 *  4. 공개/비공개 토글
 *  5. 키워드 규칙 요약 + 관리 버튼
 */
export function SettingsTab({
  item,
  onEdit,
  onTogglePublic,
  isWorking,
  onOpenKeywordDrawer,
}: SettingsTabProps) {
  // 개별 필드 저장 — 현재값에 변경값만 병합하여 onEdit 으로 전달.
  function saveField(patch: Partial<{ name: string; slackChannelId: string; maxItems: number }>) {
    onEdit(item.id, {
      name: patch.name ?? item.name,
      slackChannelId: patch.slackChannelId ?? item.slackChannelId ?? undefined,
      maxItems: patch.maxItems ?? item.maxItems,
    });
  }

  return (
    <div className="flex flex-col gap-3">
      <h4 className="text-sm font-medium text-foreground">설정</h4>

      <SettingsBasicSection item={item} isWorking={isWorking} onSaveField={saveField} />

      <SettingsMetadataSection item={item} isWorking={isWorking} />

      <SettingsOrganizationsSection categoryId={item.id} isWorking={isWorking} />

      <SettingsVisibilitySection
        item={item}
        isWorking={isWorking}
        onTogglePublic={onTogglePublic}
      />

      <SettingsKeywordsSection
        categoryId={item.id}
        onOpenKeywordDrawer={onOpenKeywordDrawer}
      />
    </div>
  );
}
