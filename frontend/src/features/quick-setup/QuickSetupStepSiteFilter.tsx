import { useState, useRef, useEffect } from "react";
import { Check, X } from "lucide-react";
import { sourceService } from "@/services/sourceService";
import { userService } from "@/services/userService";
import type { NewsRegion, QuickSetupForm } from "./model/quickSetupTypes";
import { SITE_PRESETS } from "./model/quickSetupTypes";
import { cn } from "@/utils/cn";

interface QuickSetupStepSiteFilterProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled?: boolean;
  isUserMode?: boolean;
}

interface KnownSource {
  name: string;
  domain: string;
  region: string;
  aliases: string[];
}

const REGION_OPTIONS: { value: NewsRegion; label: string }[] = [
  { value: "domestic", label: "국내 뉴스" },
  { value: "international", label: "해외 뉴스" },
  { value: "both", label: "모두" }
];

function extractDomain(url: string): string {
  try {
    const u = new URL(url);
    return u.hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

/** 리전 값을 API 파라미터로 변환한다. */
function regionToApiParam(newsRegion: NewsRegion): string | undefined {
  if (newsRegion === "domestic") return "DOMESTIC";
  if (newsRegion === "international") return "GLOBAL";
  return undefined;
}

export function QuickSetupStepSiteFilter({ form, onChange, disabled, isUserMode }: QuickSetupStepSiteFilterProps) {
  const isDirectSource = form.isDirectSource && form.includeSource;
  const mode = form.siteSelectionMode || (isDirectSource || form.siteFilters.length > 0 ? "specific" : "all");

  // 검색 자동완성 상태
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<KnownSource[]>([]);
  const [searching, setSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const searchRequestSeqRef = useRef(0);

  // 커스텀 도메인 검증 상태 (검색 결과에 없는 사이트)
  const [validating, setValidating] = useState(false);
  const [validationMsg, setValidationMsg] = useState<{ ok: boolean; text: string; domain?: string } | null>(null);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => {
      document.removeEventListener("mousedown", handleClick);
      searchRequestSeqRef.current += 1;
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, []);

  function handleSearchChange(value: string) {
    setSearchQuery(value);
    setValidationMsg(null);

    if (debounceRef.current) clearTimeout(debounceRef.current);
    const requestSeq = searchRequestSeqRef.current + 1;
    searchRequestSeqRef.current = requestSeq;

    if (!value.trim()) {
      setSearchResults([]);
      setShowDropdown(false);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      try {
        const region = regionToApiParam(form.newsRegion);
        const results = await userService.searchKnownSources(value.trim(), region);
        if (searchRequestSeqRef.current !== requestSeq) return;
        // 이미 추가된 도메인은 제외
        const filtered = results.filter((r) => !form.siteFilters.includes(r.domain));
        setSearchResults(filtered);
        setShowDropdown(true);
      } catch {
        if (searchRequestSeqRef.current !== requestSeq) return;
        setSearchResults([]);
      } finally {
        if (searchRequestSeqRef.current === requestSeq) {
          setSearching(false);
        }
      }
    }, 300);
  }

  function selectSource(source: KnownSource) {
    if (!form.siteFilters.includes(source.domain)) {
      onChange({ siteFilters: [...form.siteFilters, source.domain] });
    }
    setSearchQuery("");
    setSearchResults([]);
    setShowDropdown(false);
    setValidationMsg(null);
  }

  async function validateAndAddCustomDomain() {
    const trimmed = searchQuery
      .trim()
      .replace(/^https?:\/\//, "")
      .replace(/^www\./, "")
      .replace(/\/.*$/, "");
    if (!trimmed) return;
    if (form.siteFilters.includes(trimmed)) {
      setValidationMsg({ ok: false, text: "이미 추가된 사이트예요." });
      return;
    }
    setValidating(true);
    setValidationMsg(null);
    try {
      const testUrl = `https://news.google.com/rss/search?q=site:${trimmed}&hl=ko&gl=KR&ceid=KR:ko`;
      const validate = isUserMode ? userService.validateSetupSourceUrl : sourceService.validateUrl;
      const result = await validate(testUrl);
      if (result.valid) {
        setValidationMsg({ ok: true, text: `${trimmed}에서 뉴스를 가져올 수 있어요!`, domain: trimmed });
      } else {
        setValidationMsg({
          ok: false,
          text: "이 사이트에서는 뉴스를 가져올 수 없어요. 이름을 다시 확인해 보세요."
        });
      }
    } catch {
      setValidationMsg({ ok: false, text: "확인 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요." });
    } finally {
      setValidating(false);
    }
  }

  function confirmAddValidatedDomain() {
    if (validationMsg?.ok && validationMsg.domain) {
      onChange({ siteFilters: [...form.siteFilters, validationMsg.domain] });
      setSearchQuery("");
      setValidationMsg(null);
    }
  }

  function toggleSite(domain: string) {
    const next = form.siteFilters.includes(domain)
      ? form.siteFilters.filter((d) => d !== domain)
      : [...form.siteFilters, domain];
    onChange({ siteFilters: next });
  }

  function removeSite(domain: string) {
    onChange({ siteFilters: form.siteFilters.filter((d) => d !== domain) });
  }

  function handleClearDirectSource() {
    onChange({
      isDirectSource: false,
      includeSource: false,
      sourceName: "",
      sourceUrl: "",
      siteSelectionMode: form.siteFilters.length > 0 ? "specific" : "all"
    });
  }

  const customDomains = form.siteFilters.filter((d) => !SITE_PRESETS.some((s) => s.domain === d));

  const filteredPresets = SITE_PRESETS.filter((s) =>
    form.newsRegion === "both" ? true
    : form.newsRegion === "international" ? s.region === "international"
    : s.region === "domestic"
  );

  return (
    <div className="space-y-4 py-2">
      <div>
        <h3 className="text-sm font-semibold mb-1">어디서 찾을까요?</h3>
        <p className="text-xs text-muted-foreground">뉴스 범위와 언론사를 선택하세요.</p>
      </div>

      <div className="flex gap-1">
        {REGION_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={cn(
              "flex-1 py-1.5 px-3 text-xs rounded-md border transition-colors",
              form.newsRegion === opt.value
                ? "bg-primary text-primary-foreground border-primary"
                : "bg-background text-foreground border-border hover:bg-muted"
            )}
            aria-pressed={form.newsRegion === opt.value}
            onClick={() => {
              const newRegion = opt.value;
              searchRequestSeqRef.current += 1;
              if (debounceRef.current) {
                clearTimeout(debounceRef.current);
              }
              const validDomains: string[] = SITE_PRESETS
                .filter((s) => newRegion === "both" ? true : newRegion === "international" ? s.region === "international" : s.region === "domestic")
                .map((s) => s.domain as string);
              const allPresetDomains: string[] = SITE_PRESETS.map((s) => s.domain as string);
              const kept = form.siteFilters.filter(
                (d) => validDomains.includes(d) || !allPresetDomains.includes(d)
              );
              onChange({ newsRegion: newRegion, siteFilters: kept });
            }}
            disabled={disabled}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          className={cn(
            "p-3 text-left rounded-lg border transition-colors",
            mode === "all" ? "border-primary bg-primary/5" : "border-border hover:bg-muted"
          )}
          aria-pressed={mode === "all"}
          onClick={() => {
            onChange({
              siteSelectionMode: "all",
              siteFilters: [],
              ...(isDirectSource ? { isDirectSource: false, includeSource: false, sourceName: "", sourceUrl: "" } : {})
            });
          }}
          disabled={disabled}
        >
          <div className="text-sm font-medium">모든 뉴스</div>
          <div className="text-xs text-muted-foreground mt-0.5">Google News에서 자동으로 찾아요</div>
        </button>

        <button
          type="button"
          className={cn(
            "p-3 text-left rounded-lg border transition-colors",
            mode === "specific" ? "border-primary bg-primary/5" : "border-border hover:bg-muted"
          )}
          aria-pressed={mode === "specific"}
          onClick={() => onChange({ siteSelectionMode: "specific" })}
          disabled={disabled}
        >
          <div className="text-sm font-medium">특정 사이트만</div>
          <div className="text-xs text-muted-foreground mt-0.5">원하는 언론사를 여러 개 선택하세요</div>
        </button>
      </div>

      {mode === "specific" && (
        <div className="space-y-3">
          {isDirectSource && (
            <div className="flex flex-wrap gap-1.5">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]">
                {form.sourceName || extractDomain(form.sourceUrl)}
                <span className="opacity-60">{extractDomain(form.sourceUrl)}</span>
                <button
                  type="button"
                  className="hover:opacity-70 transition-opacity"
                  onClick={handleClearDirectSource}
                  disabled={disabled}
                >
                  ×
                </button>
              </span>
            </div>
          )}

          {/* 인기 언론사 프리셋 뱃지 */}
          <div className="flex flex-wrap gap-1.5">
            {filteredPresets.map((site) => (
              <button
                key={site.domain}
                type="button"
                className={cn(
                  "px-2.5 py-1 text-xs rounded-full border transition-colors",
                  form.siteFilters.includes(site.domain)
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-background text-foreground border-border hover:bg-muted"
                )}
                aria-pressed={form.siteFilters.includes(site.domain)}
                onClick={() => toggleSite(site.domain)}
                disabled={disabled}
              >
                {site.label}
              </button>
            ))}
          </div>

          {/* 검색 자동완성 */}
          <div ref={searchRef} className="relative">
            <div className="flex gap-2">
              <input
                className="flex-1 px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                onFocus={() => { if (searchResults.length > 0) setShowDropdown(true); }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    if (searchResults.length > 0) {
                      selectSource(searchResults[0]);
                    } else if (searchQuery.trim()) {
                      validateAndAddCustomDomain();
                    }
                  }
                }}
                placeholder="언론사 이름으로 검색 (예: SearchCo, BBC, Example Economic Times)"
                disabled={disabled || validating}
              />
              {searchQuery.trim() && searchResults.length === 0 && !searching && !validationMsg?.ok && (
                <button
                  type="button"
                  className="px-3 py-2 text-xs border border-border rounded-md hover:bg-muted transition-colors whitespace-nowrap"
                  onClick={validateAndAddCustomDomain}
                  disabled={disabled || validating}
                >
                  {validating ? "확인 중…" : "직접 추가"}
                </button>
              )}
              {validationMsg?.ok && (
                <button
                  type="button"
                  className="px-3 py-2 text-xs bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors whitespace-nowrap"
                  onClick={confirmAddValidatedDomain}
                  disabled={disabled}
                >
                  추가
                </button>
              )}
            </div>

            {/* 검색 드롭다운 */}
            {showDropdown && searchResults.length > 0 && (
              <div className="absolute z-10 mt-1 w-full bg-background border border-border rounded-lg shadow-lg overflow-hidden">
                {searchResults.slice(0, 8).map((source) => (
                  <button
                    key={source.domain}
                    type="button"
                    className="w-full px-3 py-2.5 text-left hover:bg-muted transition-colors flex items-center justify-between"
                    onClick={() => selectSource(source)}
                  >
                    <div>
                      <span className="text-sm font-medium">{source.name}</span>
                      <span className="text-xs text-muted-foreground ml-2">{source.domain}</span>
                    </div>
                    <span className={cn(
                      "text-[10px] px-1.5 py-0.5 rounded-full",
                      source.region === "DOMESTIC"
                        ? "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]"
                        : "bg-primary/10 text-primary"
                    )}>
                      {source.region === "DOMESTIC" ? "국내" : "해외"}
                    </span>
                  </button>
                ))}
              </div>
            )}

            {/* 검색 중 표시 */}
            {searching && (
              <div className="absolute z-10 mt-1 w-full bg-background border border-border rounded-lg shadow-lg px-3 py-2.5">
                <span className="text-xs text-muted-foreground">검색 중…</span>
              </div>
            )}

            {/* 검색 결과 없음 + 안내 */}
            {showDropdown && searchQuery.trim() && searchResults.length === 0 && !searching && !validationMsg && (
              <div className="absolute z-10 mt-1 w-full bg-background border border-border rounded-lg shadow-lg px-3 py-2.5">
                <p className="text-xs text-muted-foreground">
                  등록된 언론사에 없어요. "직접 추가" 버튼으로 뉴스 피드 검증 후 추가할 수 있어요.
                </p>
              </div>
            )}
          </div>

          {/* 검증 메시지 */}
          {validationMsg && (
            <p className={cn("text-xs", validationMsg.ok ? "text-[var(--status-success-text)]" : "text-destructive")}>
              {validationMsg.ok ? <Check className="inline h-3 w-3" /> : <X className="inline h-3 w-3" />} {validationMsg.text}
            </p>
          )}

          {/* 커스텀 도메인 태그 */}
          {customDomains.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {customDomains.map((domain) => (
                <span
                  key={domain}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary text-primary-foreground"
                >
                  {domain}
                  <button
                    type="button"
                    className="hover:opacity-70 transition-opacity"
                    onClick={() => removeSite(domain)}
                    disabled={disabled}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}

          <p className="text-xs text-muted-foreground">
            {isDirectSource && form.siteFilters.length === 0
              ? "외부 사이트에서 직접 수집 중이에요."
              : form.siteFilters.length > 0
                ? `${form.siteFilters.length}개 사이트에서 뉴스를 찾아요.`
                : "사이트를 선택하거나 검색해서 추가하세요."}
          </p>
        </div>
      )}

      {/* 저작권 정책 안내 */}
      <div className="rounded-lg bg-[var(--status-neutral-bg)] px-3 py-2.5">
        <p className="text-xs text-[var(--status-neutral-text)]">
          모든 소스는 &quot;인용/요약만 허용&quot; 정책이 적용돼요.
          원문 링크가 포함된 요약만 발송되며, 전문 게재는 불가합니다.
        </p>
      </div>
    </div>
  );
}
