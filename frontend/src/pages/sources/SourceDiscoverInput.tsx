import { useState, useRef, useEffect } from "react";
import { Check } from "lucide-react";
import { sourceService } from "@/services/sourceService";

interface DiscoverResult {
  knownMatch: { name: string; rssUrl: string; region: string } | null;
  discoveredFeeds: { url: string; title: string }[];
}

interface SourceDiscoverInputProps {
  onSelect: (result: { name: string; url: string; region: string }) => void;
}

export function SourceDiscoverInput({ onSelect }: SourceDiscoverInputProps) {
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<DiscoverResult | null>(null);
  const [open, setOpen] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function handleChange(value: string) {
    setQuery(value);
    if (timerRef.current) clearTimeout(timerRef.current);
    if (value.trim().length < 2) {
      setResults(null);
      setOpen(false);
      return;
    }
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await sourceService.discoverSource(value.trim());
        setResults(res);
        setOpen(true);
      } catch {
        setResults(null);
      } finally {
        setLoading(false);
      }
    }, 500);
  }

  function handleSelectKnown(match: { name: string; rssUrl: string; region: string }) {
    onSelect({ name: match.name, url: match.rssUrl, region: match.region });
    setQuery(match.name);
    setOpen(false);
  }

  function handleSelectFeed(feed: { url: string; title: string }) {
    onSelect({ name: feed.title, url: feed.url, region: "" });
    setQuery(feed.url);
    setOpen(false);
  }

  function handleDirectUrl() {
    onSelect({ name: "", url: query.trim(), region: "" });
    setOpen(false);
  }

  const hasResults = results && (results.knownMatch || results.discoveredFeeds.length > 0);

  return (
    <div className="relative" ref={wrapperRef}>
      <div className="relative">
        <svg
          className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" />
        </svg>
        <input
          type="text"
          className="w-full pl-9 pr-4 py-2 text-sm border rounded-md bg-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
          placeholder="Example Daily, techcrunch.com, https://... (2글자 이상)"
          value={query}
          onChange={(e) => handleChange(e.target.value)}
          onFocus={() => {
            if (hasResults) setOpen(true);
          }}
        />
        {loading && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        )}
      </div>

      {open && results && (
        <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover shadow-md">
          {results.knownMatch && (
            <button
              type="button"
              className="w-full text-left px-3 py-2.5 hover:bg-accent transition-colors border-b"
              onClick={() => handleSelectKnown(results.knownMatch!)}
            >
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-[var(--status-success-text)] bg-[var(--status-success-bg)] px-1.5 py-0.5 rounded inline-flex items-center gap-0.5"><Check className="h-3.5 w-3.5" /> 검증됨</span>
                <span className="text-sm font-medium">{results.knownMatch.name}</span>
              </div>
              <p className="text-xs text-muted-foreground mt-0.5 truncate">{results.knownMatch.rssUrl}</p>
            </button>
          )}

          {results.discoveredFeeds.map((feed, i) => (
            <button
              key={i}
              type="button"
              className="w-full text-left px-3 py-2.5 hover:bg-accent transition-colors border-b"
              onClick={() => handleSelectFeed(feed)}
            >
              <p className="text-sm font-medium">{feed.title}</p>
              <p className="text-xs text-muted-foreground truncate">{feed.url}</p>
            </button>
          ))}

          {!results.knownMatch && results.discoveredFeeds.length === 0 && (
            <div className="px-3 py-2.5 text-sm text-muted-foreground">매칭되는 소스를 찾지 못했어요</div>
          )}

          <button
            type="button"
            className="w-full text-left px-3 py-2.5 hover:bg-accent transition-colors text-sm text-primary font-medium"
            onClick={handleDirectUrl}
          >
            → 직접 URL로 사용하기
          </button>
        </div>
      )}
    </div>
  );
}
