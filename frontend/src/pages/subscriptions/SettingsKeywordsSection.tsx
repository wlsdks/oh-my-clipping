import { useQuery } from "@tanstack/react-query";
import { Key } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ruleService } from "@/services/ruleService";
import { ruleKeys } from "@/queries/ruleKeys";
import { ReadonlyRow } from "./SettingsTabRows";

/** 키워드 목록을 읽기 쉬운 요약 문자열로 변환 */
function summarizeKeywords(keywords: string[], maxVisible = 3): string {
  if (!keywords || keywords.length === 0) return "—";
  if (keywords.length <= maxVisible) return keywords.join(", ");
  const visible = keywords.slice(0, maxVisible).join(", ");
  const remaining = keywords.length - maxVisible;
  return `${visible} 외 ${remaining}개`;
}

interface SettingsKeywordsSectionProps {
  categoryId: string;
  onOpenKeywordDrawer: () => void;
}

/**
 * 키워드 규칙 요약 섹션.
 * 포함/제외 키워드를 간단히 노출하고, "키워드 관리" 버튼으로 Drawer 를 연다.
 * 404(규칙 미생성) 는 에러가 아닌 정상 상태로 처리한다.
 */
export function SettingsKeywordsSection({
  categoryId,
  onOpenKeywordDrawer,
}: SettingsKeywordsSectionProps) {
  const ruleQuery = useQuery({
    queryKey: ruleKeys.detail(categoryId),
    queryFn: () => ruleService.getCategoryRule(categoryId),
    staleTime: 30_000,
    retry: (failureCount, error) => {
      if ((error as { status?: number })?.status === 404) return false;
      return failureCount < 2;
    },
  });

  const rule = ruleQuery.data;
  const ruleError = ruleQuery.error;
  const is404 = (ruleError as { status?: number })?.status === 404;
  const hasKeywords =
    rule && (rule.includeKeywords.length > 0 || rule.excludeKeywords.length > 0);
  const hasRuleError = ruleError && !is404;

  return (
    <>
      <div className="rounded-lg border border-border bg-muted/30 divide-y divide-border">
        {hasRuleError ? (
          <div className="flex items-center gap-3 px-3 py-2.5">
            <span className="text-xs text-muted-foreground w-16 shrink-0">포함 키워드</span>
            <span className="text-sm text-[var(--status-danger-text)]">불러오기 실패</span>
          </div>
        ) : (
          <>
            <ReadonlyRow
              label="포함 키워드"
              value={rule ? summarizeKeywords(rule.includeKeywords) : "—"}
            />
            <ReadonlyRow
              label="제외 키워드"
              value={rule ? summarizeKeywords(rule.excludeKeywords) : "—"}
            />
          </>
        )}
      </div>

      {!hasKeywords && !hasRuleError && (
        <p className="text-xs text-muted-foreground px-1">
          키워드를 설정하면 불필요한 기사를 자동으로 걸러낼 수 있어요
        </p>
      )}

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="self-start gap-1.5"
        onClick={onOpenKeywordDrawer}
      >
        <Key className="h-3.5 w-3.5" />
        키워드 관리
      </Button>
    </>
  );
}
