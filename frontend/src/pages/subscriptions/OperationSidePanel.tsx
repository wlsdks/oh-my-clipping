import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ExternalLink, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { cn } from "@/utils/cn";
import { formatRelativeDate } from "@/utils/date";
import { sourceService } from "@/services/sourceService";
import { deliveryService } from "@/services/deliveryService";
import { ruleService } from "@/services/ruleService";
import { categoryService } from "@/services/categoryService";
import { sourceKeys } from "@/queries/sourceKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { ruleKeys } from "@/queries/ruleKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { useEditingPresence } from "@/hooks/useEditingPresence";
import { EditingPresenceBadge } from "@/components/shared/EditingPresenceBadge";
import { ChangeDetectionStrip } from "@/components/shared/ChangeDetectionStrip";
import { getCategoryStatus, STATUS_STYLES } from "./model/constants";
import { SettingsTab } from "./SettingsTab";
import { KeywordRulesDrawer } from "./KeywordRulesDrawer";
import { ExcludedArticlesSection } from "./ExcludedArticlesSection";
import type { Category } from "@/types/category";
import type { DeliveryLogRecord } from "@/types/delivery";

export type OperationPanelTab = "config" | "delivery" | "settings";

interface OperationSidePanelProps {
  item: Category;
  onEdit: (id: string, data: { name: string; slackChannelId?: string; maxItems: number }) => void;
  onPause: (id: string) => void;
  onResume: (id: string) => void;
  onTogglePublic: (id: string, isPublic: boolean) => void;
  onDelete: (id: string) => void;
  isWorking: boolean;
  activeTab: OperationPanelTab;
  onTabChange: (tab: OperationPanelTab) => void;
}

const TABS: { value: OperationPanelTab; label: string }[] = [
  { value: "config", label: "구성" },
  { value: "delivery", label: "발송이력" },
  { value: "settings", label: "설정" },
];

export function OperationSidePanel({
  item,
  onEdit,
  onPause,
  onResume,
  onTogglePublic,
  onDelete,
  isWorking,
  activeTab,
  onTabChange,
}: OperationSidePanelProps) {
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showKeywordDrawer, setShowKeywordDrawer] = useState(false);
  const queryClient = useQueryClient();

  const status = getCategoryStatus(item);
  const style = STATUS_STYLES[status.type];

  // 이 패널이 Category 편집 엔트리(설정 탭) 역할을 하므로 카테고리 presence 를 추적한다.
  const { otherEditors } = useEditingPresence({
    resourceType: "category",
    resourceId: item.id,
    enabled: Boolean(item.id)
  });

  const { data: liveCategory } = useQuery({
    queryKey: [...categoryKeys.detail(item.id), "live"],
    queryFn: () => categoryService.getById(item.id),
    enabled: Boolean(item.id),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    retry: false
  });

  // 키워드 드로어에 초기값 제공용 rule 조회
  const ruleQuery = useQuery({
    queryKey: ruleKeys.detail(item.id),
    queryFn: () => ruleService.getCategoryRule(item.id),
    staleTime: 30_000,
    retry: (failureCount, error) => {
      if ((error as { status?: number })?.status === 404) return false;
      return failureCount < 2;
    },
  });

  // 구성 탭: 소스 목록 (lazy fetch)
  const sourcesQuery = useQuery({
    queryKey: sourceKeys.listsByCategoryId(item.id),
    queryFn: () => sourceService.getAll(item.id),
    enabled: activeTab === "config",
  });

  // 발송이력 탭: 발송 로그 (lazy fetch)
  const deliveryLogsQuery = useQuery({
    queryKey: deliveryKeys.logsList({ categoryId: item.id }),
    queryFn: () => {
      const params = new URLSearchParams({ categoryId: item.id, size: "20" });
      return deliveryService.listLogs(params);
    },
    enabled: activeTab === "delivery",
  });

  return (
    <div className="flex flex-col gap-5">
      {/* 편집 presence / 변경 감지 */}
      <ChangeDetectionStrip
        initialUpdatedAt={item.updatedAt}
        currentUpdatedAt={liveCategory?.updatedAt ?? item.updatedAt}
        onReload={async () => {
          await queryClient.invalidateQueries({ queryKey: categoryKeys.all });
        }}
      />

      {/* 상단: 상태 뱃지 + 이름 + 설명 */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold",
              style.bg,
              style.text
            )}
          >
            <status.icon className="h-3.5 w-3.5" />
            {status.label}
          </span>
          {otherEditors.length > 0 && <EditingPresenceBadge editors={otherEditors} />}
        </div>
        <h3 className="text-lg font-semibold text-foreground leading-snug">
          {item.name}
        </h3>
        {item.description && (
          <p className="text-sm text-muted-foreground">{item.description}</p>
        )}
      </div>

      {/* 2x2 메트릭 그리드 */}
      <div className="grid grid-cols-2 gap-3">
        <MetricCard label="소스" value={`${item.sourceCount}개`} />
        <MetricCard label="구독자" value={`${item.subscriberCount}명`} />
        <MetricCard label="최근 발송" value={formatRelativeDate(item.lastDeliveryAt)} />
        <MetricCard label="최대 기사" value={`${item.maxItems}건`} />
      </div>

      {/* 소스 건강 요약 */}
      {item.errorSourceCount > 0 && (
        <div className="rounded-lg bg-destructive/5 border-l-3 border-destructive px-4 py-3">
          <p className="text-sm text-foreground">
            전체 {item.sourceCount}개 소스 중{" "}
            <span className="font-semibold text-destructive">{item.errorSourceCount}개</span> 수집 오류
          </p>
        </div>
      )}

      {/* 탭 네비게이션 */}
      <div className="flex gap-1 border-b">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            className={cn(
              "px-3 py-2 text-sm font-medium transition-colors relative",
              activeTab === tab.value
                ? "text-foreground"
                : "text-muted-foreground hover:text-foreground",
            )}
            onClick={() => onTabChange(tab.value)}
          >
            {tab.label}
            {activeTab === tab.value && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
            )}
          </button>
        ))}
      </div>

      {/* 탭 컨텐츠 */}
      {activeTab === "config" && (
        <ConfigTab
          categoryId={item.id}
          sources={sourcesQuery.data ?? []}
          isLoading={sourcesQuery.isLoading}
        />
      )}

      {activeTab === "delivery" && (
        <DeliveryTab
          logs={deliveryLogsQuery.data?.content ?? []}
          isLoading={deliveryLogsQuery.isLoading}
          channelLabel={
            item.slackChannelId
              ? item.slackChannelId.toUpperCase().startsWith("D")
                ? "DM (개인 메시지)"
                : `#${item.slackChannelId}`
              : undefined
          }
        />
      )}

      {activeTab === "settings" && (
        <SettingsTab
          item={item}
          onEdit={onEdit}
          onTogglePublic={onTogglePublic}
          isWorking={isWorking}
          onOpenKeywordDrawer={() => setShowKeywordDrawer(true)}
        />
      )}

      {/* 하단 액션 */}
      <div className="flex gap-2 pt-2 border-t">
        {item.status === "PAUSED" ? (
          <Button
            variant="outline"
            size="sm"
            className="flex-1"
            disabled={isWorking}
            onClick={() => onResume(item.id)}
          >
            활성화
          </Button>
        ) : (
          <Button
            variant="outline"
            size="sm"
            className="flex-1"
            disabled={isWorking}
            onClick={() => onPause(item.id)}
          >
            일시정지
          </Button>
        )}
        <Button
          variant="destructive"
          size="sm"
          disabled={isWorking}
          onClick={() => setShowDeleteConfirm(true)}
        >
          삭제
        </Button>
      </div>

      <ConfirmModal
        open={showDeleteConfirm}
        onOpenChange={setShowDeleteConfirm}
        title="구독을 삭제하시겠어요?"
        description={`"${item.name}" 구독을 삭제하면 소스 ${item.sourceCount}개, 구독자 ${item.subscriberCount}명에 영향을 줍니다. 되돌릴 수 없어요.`}
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => onDelete(item.id)}
      />

      <KeywordRulesDrawer
        open={showKeywordDrawer}
        onOpenChange={setShowKeywordDrawer}
        categoryId={item.id}
        categoryName={item.name}
        initialRule={ruleQuery.data ?? null}
      />
    </div>
  );
}

// ── 탭 컨텐츠 컴포넌트 ──

interface SourceItem {
  id: string;
  name: string;
  isActive: boolean;
}

function ConfigTab({
  categoryId,
  sources,
  isLoading,
}: {
  categoryId: string;
  sources: SourceItem[];
  isLoading: boolean;
}) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {sources.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-6">
          연결된 소스가 없어요
        </p>
      ) : (
        <>
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium text-foreground">소스 ({sources.length})</h4>
            <Link
              to={`/admin/sources?categoryId=${encodeURIComponent(categoryId)}`}
              className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
            >
              소스 관리에서 보기
              <ExternalLink size={10} />
            </Link>
          </div>
          <div className="rounded-lg border border-border divide-y divide-border max-h-48 overflow-y-auto">
            {sources.map((src) => (
              <div key={src.id} className="flex items-center gap-2 px-3 py-2">
                <span
                  className={cn(
                    "h-1.5 w-1.5 rounded-full shrink-0",
                    src.isActive ? "bg-[var(--status-success-text)]" : "bg-muted-foreground",
                  )}
                />
                <span className="text-sm text-foreground truncate">{src.name}</span>
              </div>
            ))}
          </div>
        </>
      )}

      {/* 제외된 기사 섹션 */}
      <div className="border-t mt-2 pt-1">
        <ExcludedArticlesSection categoryId={categoryId} />
      </div>
    </div>
  );
}

function DeliveryTab({
  logs,
  isLoading,
  channelLabel,
}: {
  logs: DeliveryLogRecord[];
  isLoading: boolean;
  channelLabel?: string;
}) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (logs.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-6">
        발송 이력이 없어요
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium text-foreground">최근 발송 ({logs.length})</h4>
        {channelLabel && (
          <span className="text-xs text-muted-foreground">발송 대상: {channelLabel}</span>
        )}
      </div>
      <div className="rounded-lg border border-border divide-y divide-border max-h-60 overflow-y-auto">
        {logs.map((log) => (
          <div key={log.id} className="flex items-center justify-between px-3 py-2">
            <div className="flex flex-col">
              <span className="text-sm text-foreground">
                {log.deliveryDate} {log.deliveryHour}시
              </span>
              <span className="text-xs text-muted-foreground">{log.itemCount}건</span>
            </div>
            <span
              className={cn(
                "text-xs font-medium px-2 py-0.5 rounded-full",
                log.status === "SENT"
                  ? "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
                  : log.status === "FAILED"
                    ? "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
                    : "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]",
              )}
            >
              {log.status === "SENT" ? "성공" : log.status === "FAILED" ? "실패" : "알 수 없음"}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── 하위 컴포넌트 ──

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-muted/30 px-3 py-2.5">
      <p className="text-xs text-muted-foreground mb-0.5">{label}</p>
      <p className="text-sm font-semibold text-foreground">{value}</p>
    </div>
  );
}
