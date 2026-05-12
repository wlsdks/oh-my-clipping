import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { companyService, type CompanySearchResult } from "@/services/companyService";
import { userService, type CategoryBrowseItem } from "@/services/userService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { matchesKoreanSearch } from "@/utils/search";
import { useAuthStore } from "@/store/authStore";
import { SlackConnectModal } from "@/components/shared/SlackConnectModal";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { KeywordEntry, QuickSetupForm } from "./model/quickSetupTypes";
import { KEYWORD_PRESETS, autoTopicName } from "./model/quickSetupTypes";
import { cn } from "@/utils/cn";

function shuffleArray<T>(arr: readonly T[]): T[] {
  const result = [...arr];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

/** 모듈 로드 시 한 번만 셔플 — 스텝 이동으로 리마운트돼도 순서가 유지된다 */
const SHUFFLED_PRESETS = shuffleArray(KEYWORD_PRESETS);

interface QuickSetupStepSourceProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled?: boolean;
  isUserMode?: boolean;
  onSubscribeDm?: (categoryId: string, categoryName: string) => void;
}

type InputTab = "company" | "keyword";

export function QuickSetupStepSource({
  form,
  onChange,
  disabled,
  isUserMode,
  onSubscribeDm
}: QuickSetupStepSourceProps) {
  const shuffledPresets = SHUFFLED_PRESETS;
  const [tab, setTab] = useState<InputTab>("keyword");
  const [input, setInput] = useState("");
  const [suggestions, setSuggestions] = useState<CompanySearchResult[]>([]);
  const [, setShowDropdown] = useState(false);
  const [highlightIdx, setHighlightIdx] = useState(-1);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const dropdownListRef = useRef<HTMLDivElement>(null);

  // 기존 카테고리 검색 (유저 모드에서만 활성화)
  const [browseCategories, setBrowseCategories] = useState<CategoryBrowseItem[]>([]);
  const [subscribingId, setSubscribingId] = useState<string | null>(null);
  const [slackModalCategoryId, setSlackModalCategoryId] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!isUserMode) return;
    userService
      .browseCategories()
      .then(setBrowseCategories)
      .catch((err) => {
        toast.error(userFriendlyMessage(err, "주제 목록을 불러오지 못했어요"));
      });
  }, [isUserMode]);

  function fetchSuggestions(query: string) {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (query.trim().length < 1) {
      setSuggestions([]);
      setShowDropdown(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const searchFn = isUserMode ? companyService.searchUserCompanies : companyService.searchAdminCompanies;
        const results = await searchFn(query.trim());
        setSuggestions(results);
        setShowDropdown(true);
        setHighlightIdx(-1);
      } catch {
        setSuggestions([]);
        setShowDropdown(false);
      }
    }, 300);
  }

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, []);

  function handleTabChange(newTab: InputTab) {
    setTab(newTab);
    setInput("");
    setSuggestions([]);
    setShowDropdown(false);
  }

  function addEntry(entry: KeywordEntry) {
    if (form.entries.some((e) => e.value === entry.value)) return;
    const next = [...form.entries, entry];
    onChange({ entries: next, categoryName: autoTopicName(next) });
  }

  function removeEntry(value: string) {
    const next = form.entries.filter((e) => e.value !== value);
    onChange({
      entries: next,
      categoryName: form.categoryName === autoTopicName(form.entries) ? autoTopicName(next) : form.categoryName
    });
  }

  function handleKeywordKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" && input.trim()) {
      e.preventDefault();
      addEntry({ value: input.trim(), type: "keyword" });
      setInput("");
    }
  }

  function selectCompany(company: CompanySearchResult) {
    addEntry({ value: company.corpName, type: "company", stockCode: company.stockCode || undefined });
    setInput("");
    setShowDropdown(false);
    setHighlightIdx(-1);
  }

  useEffect(() => {
    if (highlightIdx < 0 || !dropdownListRef.current) return;
    const items = dropdownListRef.current.children;
    if (items[highlightIdx]) {
      (items[highlightIdx] as HTMLElement).scrollIntoView({ block: "nearest" });
    }
  }, [highlightIdx]);

  function handleCompanyKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown" && suggestions.length > 0) {
      e.preventDefault();
      setHighlightIdx((prev) => (prev < suggestions.length - 1 ? prev + 1 : 0));
    } else if (e.key === "ArrowUp" && suggestions.length > 0) {
      e.preventDefault();
      setHighlightIdx((prev) => (prev > 0 ? prev - 1 : suggestions.length - 1));
    } else if (e.key === "Enter" && highlightIdx >= 0) {
      e.preventDefault();
      const item = suggestions[highlightIdx];
      if (item?.isCompetitor) return; // 경쟁사는 키보드로도 선택 불가
      selectCompany(item);
    } else if (e.key === "Enter" && input.trim() && highlightIdx < 0) {
      e.preventDefault();
      addEntry({ value: input.trim(), type: "company" });
      setInput("");
      setShowDropdown(false);
    }
  }

  function handlePresetClick(preset: string) {
    const existing = form.entries.find((e) => e.value === preset);
    if (existing) removeEntry(preset);
    else addEntry({ value: preset, type: "keyword" });
  }

  // 입력된 키워드와 매칭되는 기존 카테고리 필터링
  const matchingCategories = (() => {
    if (!isUserMode || browseCategories.length === 0 || form.entries.length === 0) return [];
    return browseCategories
      .filter((cat) => form.entries.some((entry) => matchesKoreanSearch(cat.name, entry.value)))
      .slice(0, 5);
  })();

  return (
    <div className="space-y-4 py-2">
      <div>
        <h3 className="text-sm font-semibold mb-1">어떤 뉴스를 받고 싶으세요?</h3>
        <p className="text-xs text-muted-foreground">관심 키워드나 기업명을 추가하세요.</p>
      </div>

      <div className="space-y-2">
        <div className="flex gap-1">
          <button
            type="button"
            className={cn(
              "flex-1 py-1.5 px-3 text-xs rounded-md border transition-colors",
              tab === "keyword"
                ? "bg-primary text-primary-foreground border-primary"
                : "bg-background text-foreground border-border hover:bg-muted"
            )}
            aria-pressed={tab === "keyword"}
            onClick={() => handleTabChange("keyword")}
            disabled={disabled}
          >
            키워드 입력
          </button>
          <button
            type="button"
            className={cn(
              "flex-1 py-1.5 px-3 text-xs rounded-md border transition-colors",
              tab === "company"
                ? "bg-primary text-primary-foreground border-primary"
                : "bg-background text-foreground border-border hover:bg-muted"
            )}
            aria-pressed={tab === "company"}
            onClick={() => handleTabChange("company")}
            disabled={disabled}
          >
            기업 검색
            {tab !== "company" && <span className="text-[10px] ml-1 opacity-70">MegaCorp, SemiCorp 등</span>}
          </button>
        </div>

        <div className="relative" ref={dropdownRef}>
          {tab === "company" ? (
            <>
              <input
                className="w-full px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
                value={input}
                onChange={(e) => {
                  setInput(e.target.value);
                  fetchSuggestions(e.target.value);
                }}
                onKeyDown={handleCompanyKeyDown}
                placeholder="기업명을 입력하세요 (예: MegaCorp)"
                disabled={disabled}
              />
              <p className="mt-2 text-xs text-muted-foreground">
                주제와 함께 입력하면 두 조건이 모두 맞는 뉴스만 보여드려요. 기업만 보고 싶으면 주제 탭을 비워두세요.
              </p>
              {tab === "company" && input.trim().length > 0 && (
                <div
                  className="absolute z-50 w-full mt-1 bg-popover border border-border rounded-md shadow-md max-h-48 overflow-y-auto"
                  ref={dropdownListRef}
                >
                  {suggestions.map((c, idx) => {
                    const isCompetitor = c.isCompetitor === true;
                    const item = (
                      <button
                        key={c.corpCode}
                        type="button"
                        disabled={isCompetitor}
                        className={cn(
                          "w-full text-left px-3 py-2 text-sm flex items-center justify-between hover:bg-accent",
                          idx === highlightIdx && !isCompetitor && "bg-accent",
                          isCompetitor && "opacity-50 cursor-not-allowed"
                        )}
                        onClick={() => { if (!isCompetitor) selectCompany(c); }}
                        onMouseEnter={() => { if (!isCompetitor) setHighlightIdx(idx); }}
                      >
                        <span>{c.corpName}</span>
                        {c.stockCode && <span className="text-xs text-muted-foreground">({c.stockCode})</span>}
                      </button>
                    );
                    if (isCompetitor) {
                      return (
                        <TooltipProvider key={c.corpCode}>
                          <Tooltip>
                            <TooltipTrigger asChild>{item}</TooltipTrigger>
                            <TooltipContent>경쟁사 워치리스트로 관리 중이에요. 관리자에게 문의하세요.</TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      );
                    }
                    return <span key={c.corpCode}>{item}</span>;
                  })}
                  {suggestions.length >= 20 && (
                    <div className="px-3 py-2 text-xs text-muted-foreground">
                      결과가 더 있어요. 이름을 더 정확히 입력해 보세요.
                    </div>
                  )}
                  {!form.entries.some((e) => e.value === input.trim()) && (
                    <button
                      type="button"
                      className="w-full text-left px-3 py-2 text-sm hover:bg-accent border-t border-border flex items-center gap-2 text-primary"
                      onClick={() => {
                        addEntry({ value: input.trim(), type: "company" });
                        setInput("");
                        setShowDropdown(false);
                      }}
                    >
                      <span>+</span>
                      <span>&ldquo;{input.trim()}&rdquo; 직접 추가</span>
                    </button>
                  )}
                </div>
              )}
            </>
          ) : (
            <input
              className="w-full px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeywordKeyDown}
              placeholder="키워드를 입력하고 Enter (예: AI, 반도체)"
              disabled={disabled}
            />
          )}
        </div>
      </div>

      {form.entries.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {form.entries.map((entry) => (
            <span
              key={entry.value}
              className={cn(
                "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium",
                entry.type === "company"
                  ? "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]"
                  : "bg-primary/10 text-primary"
              )}
            >
              {entry.value}
              <button
                type="button"
                className="ml-0.5 hover:opacity-70 transition-opacity"
                onClick={() => removeEntry(entry.value)}
                disabled={disabled}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">키워드를 입력하거나 아래 추천에서 선택해 주세요</p>
      )}

      {isUserMode && onSubscribeDm && matchingCategories.length > 0 && (
        <div className="space-y-2">
          <span className="text-xs font-medium text-muted-foreground">이미 있는 주제</span>
          <div className="space-y-1.5">
            {matchingCategories.map((cat) => (
              <div
                key={cat.id}
                className="flex items-center justify-between gap-2 p-2.5 rounded-lg border border-border bg-muted/30"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{cat.name}</p>
                  {cat.description && <p className="text-xs text-muted-foreground truncate">{cat.description}</p>}
                  <p className="text-xs text-muted-foreground">
                    구독자 {cat.subscriberCount}명{cat.isSubscribed && " · 구독 중"}
                  </p>
                </div>
                {cat.isSubscribed ? (
                  <span className="shrink-0 px-2.5 py-1 text-xs rounded-full bg-[var(--status-success-bg)] text-[var(--status-success-text)]">
                    구독 중
                  </span>
                ) : (
                  <button
                    type="button"
                    className="shrink-0 px-3 py-1.5 text-xs font-medium rounded-full bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                    disabled={disabled || subscribingId === cat.id}
                    onClick={() => {
                      if (user?.hasSlackDm === true) {
                        setSubscribingId(cat.id);
                        userService.subscribeCategoryDm(cat.id)
                          .then(() => onSubscribeDm(cat.id, cat.name))
                          .catch(() => setSubscribingId(null));
                      } else {
                        setSlackModalCategoryId(cat.id);
                      }
                    }}
                  >
                    {subscribingId === cat.id ? "구독 중..." : "DM으로 받기"}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="space-y-2">
        <span className="text-xs font-medium text-muted-foreground">추천 키워드</span>
        <div className="flex flex-wrap gap-1.5">
          {shuffledPresets.map((preset) => (
            <button
              key={preset}
              type="button"
              className={cn(
                "px-2.5 py-1 text-xs rounded-full border transition-colors",
                form.entries.some((e) => e.value === preset)
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-background text-foreground border-border hover:bg-muted"
              )}
              aria-pressed={form.entries.some((e) => e.value === preset)}
              onClick={() => handlePresetClick(preset)}
              disabled={disabled}
            >
              {preset}
            </button>
          ))}
        </div>
      </div>

      {/* Slack 연결 모달 */}
      <SlackConnectModal
        open={slackModalCategoryId !== null}
        onOpenChange={(open) => { if (!open) setSlackModalCategoryId(null); }}
        onSubmit={async (slackMemberId) => {
          if (!slackModalCategoryId) return;
          const targetId = slackModalCategoryId;
          setIsConnecting(true);
          try {
            await userService.updateSlackMemberId(slackMemberId);
            const currentUser = useAuthStore.getState().user;
            if (currentUser) {
              useAuthStore.getState().login({ ...currentUser, hasSlackDm: true });
            }
            setSlackModalCategoryId(null);
            setSubscribingId(targetId);
            await userService.subscribeCategoryDm(targetId);
            onSubscribeDm?.(targetId, browseCategories.find((c) => c.id === targetId)?.name ?? "");
          } catch (err) {
            toast.error(userFriendlyMessage(err, "Slack 연결에 실패했어요"));
            setSubscribingId(null);
          } finally {
            setIsConnecting(false);
          }
        }}
        isSubmitting={isConnecting}
      />
    </div>
  );
}
