/** 키워드 항목 타입: 기업 검색으로 추가했는지, 키워드 직접 입력인지 구분 */
export type KeywordEntryType = "company" | "keyword";

export interface KeywordEntry {
  value: string;
  type: KeywordEntryType;
  /** 기업인 경우 종목 코드 */
  stockCode?: string;
}

/** 뉴스 범위: 국내 / 해외 / 모두 */
export type NewsRegion = "domestic" | "international" | "both";

/** Slack 수신 방식: 채널 또는 DM */
export type SlackDeliveryMode = "channel" | "dm";

/** Slack 채널 유형: 공개 / 비공개 */
export type SlackChannelType = "public_channel" | "private_channel";

export interface QuickSetupForm {
  // Step 1: 키워드 기반 소스 설정
  entries: KeywordEntry[];
  newsRegion: NewsRegion;
  siteSelectionMode?: "all" | "specific";
  siteFilters: string[];
  categoryName: string;
  categoryDescription: string;
  slackChannelId: string;
  slackChannelType?: SlackChannelType;
  maxItems: number | string;
  // 수동 RSS 입력 (고급)
  includeSource: boolean;
  sourceName: string;
  sourceUrl: string;
  autoApproveSource: boolean;
  // Step 2: 요약 스타일
  createPersona: boolean;
  selectedPersonaId?: string;
  selectedPresetId?: string;
  personaName: string;
  personaDescription: string;
  personaSummaryStyle: string;
  personaTargetAudience: string;
  personaPrompt: string;
  // Step 4: Slack 연결 + 뉴스 옵션
  slackDeliveryMode: SlackDeliveryMode;
  slackChannelConfirmed: boolean;
  excludeKeywords: string[];
  // 발송 시간 설정
  deliveryPreset: "WEEKDAYS" | "EVERYDAY" | "CUSTOM";
  deliveryDays: string[];
  deliveryHour: number;
  // 수정 모드 전용
  editRequestId?: string;
  editCurrentSlackChannel?: string;
  /** 직접 RSS 소스 (Google News가 아닌 외부 URL) */
  isDirectSource?: boolean;
}

export interface QuickSetupResult {
  categoryId: string;
  categoryName: string;
  personaName?: string;
  sourceName?: string;
  sourceReady?: boolean;
  sourceMessage?: string;
  sourceCount?: number;
  prefSaveWarning?: string;
}

export interface SetupProgressStep {
  id: string;
  label: string;
  status: "pending" | "running" | "done" | "error";
  errorMessage?: string;
}

export const DEFAULT_PERSONA_PROMPT = [
  "당신은 뉴스 요약 도우미입니다.",
  "각 기사마다 아래 형식으로 작성하세요.",
  "1) 핵심 내용 3줄",
  "2) 왜 중요한지 1~2줄",
  "3) 지금 할 수 있는 행동 1줄",
  "어려운 용어는 쉬운 말로 바꿔주세요."
].join("\n");

/** 프리셋 키워드 칩 */
export const KEYWORD_PRESETS = [
  // 교육·HR·정책
  "기업교육",
  "HRD·인재개발",
  "리스킬링·업스킬링",
  "이러닝·에듀테크",
  "리더십·조직문화",
  "사업주훈련",
  "K-디지털 트레이닝",
  "평생교육",
  "NCS·자격제도",
  "HR테크",
  // 영업·비즈니스
  "B2B 영업",
  "디지털 전환",
  "고객경험·CX",
  "ESG·지속가능경영",
  "SaaS",
  // 테크·트렌드
  "AI·인공지능",
  "클라우드",
  "데이터 분석",
  "핀테크",
  "바이오·헬스케어"
] as const;

/**
 * 사이트 필터 프리셋 — Google News RSS 수집 검증 완료 (2026-04-03)
 * region: "domestic" = 국내, "international" = 해외
 */
export const SITE_PRESETS = [
  // 국내 (placeholder — 운영 환경에 맞는 사이트를 추가하세요)
  { label: "Example Domestic A", domain: "example.com", region: "domestic" as const },
  { label: "Example Domestic B", domain: "example.org", region: "domestic" as const },
  // 해외 — 종합
  { label: "Reuters", domain: "reuters.com", region: "international" as const },
  { label: "AP News", domain: "apnews.com", region: "international" as const },
  { label: "BBC", domain: "bbc.com", region: "international" as const },
  { label: "CNN", domain: "cnn.com", region: "international" as const },
  { label: "NYT", domain: "nytimes.com", region: "international" as const },
  { label: "The Guardian", domain: "theguardian.com", region: "international" as const },
  // 해외 — 경제·비즈
  { label: "Bloomberg", domain: "bloomberg.com", region: "international" as const },
  { label: "CNBC", domain: "cnbc.com", region: "international" as const },
  { label: "WSJ", domain: "wsj.com", region: "international" as const },
  { label: "Forbes", domain: "forbes.com", region: "international" as const },
  // 해외 — 테크
  { label: "The Verge", domain: "theverge.com", region: "international" as const },
  { label: "TechCrunch", domain: "techcrunch.com", region: "international" as const },
  { label: "Wired", domain: "wired.com", region: "international" as const },
  { label: "Ars Technica", domain: "arstechnica.com", region: "international" as const },
  // 해외 — 교육·HRD
  { label: "eLearning Industry", domain: "elearningindustry.com", region: "international" as const },
  { label: "EdSurge", domain: "edsurge.com", region: "international" as const },
  { label: "ATD", domain: "td.org", region: "international" as const },
] as const;

export function createQuickSetupForm(): QuickSetupForm {
  return {
    entries: [],
    newsRegion: "domestic",
    siteSelectionMode: "all",
    siteFilters: [],
    categoryName: "",
    categoryDescription: "",
    slackChannelId: "",
    slackChannelType: "public_channel",
    maxItems: 3,
    includeSource: false,
    sourceName: "",
    sourceUrl: "",
    autoApproveSource: true,
    createPersona: true,
    selectedPersonaId: undefined,
    personaName: "기본 요약 스타일",
    personaDescription: "비개발 실무자에게 쉬운 말로 전달",
    personaSummaryStyle: "핵심 3줄 + 의미 1줄 + 행동 1줄",
    personaTargetAudience: "비개발 실무자",
    personaPrompt: DEFAULT_PERSONA_PROMPT,
    slackDeliveryMode: "channel",
    slackChannelConfirmed: false,
    excludeKeywords: [],
    deliveryPreset: "WEEKDAYS",
    deliveryDays: ["MON", "TUE", "WED", "THU", "FRI"],
    deliveryHour: 8
  };
}

export function isValidHttpUrl(value: string): boolean {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

/** 지역별 Google News RSS 파라미터 */
const REGION_PARAMS: Record<"domestic" | "international", string> = {
  domestic: "hl=ko&gl=KR&ceid=KR:ko",
  international: "hl=en&gl=US&ceid=US:en"
};

/**
 * 키워드, 사이트 필터(복수), 뉴스 지역으로 Google News RSS URL을 생성한다.
 * 사이트가 여러 개면 OR 조합으로 합친다: (site:a.com OR site:b.com)
 * region이 "both"인 경우 호출측에서 domestic/international 각각 호출해야 한다.
 */
export function buildGoogleNewsRssUrl(
  keyword: string,
  sites?: string[],
  region: "domestic" | "international" = "domestic"
): string {
  let query = keyword;
  if (sites && sites.length === 1) {
    query = `${keyword} site:${sites[0]}`;
  } else if (sites && sites.length > 1) {
    const siteGroup = sites.map((s) => `site:${s}`).join(" OR ");
    query = `${keyword} (${siteGroup})`;
  }
  return `https://news.google.com/rss/search?q=${encodeURIComponent(query)}&${REGION_PARAMS[region]}`;
}

/**
 * 키워드 항목과 지역으로 소스 이름을 자동 생성한다.
 */
export function entryToSourceName(entry: KeywordEntry, region?: "domestic" | "international"): string {
  const suffix = region === "international" ? " (해외)" : "";
  return `${entry.value} 뉴스${suffix}`;
}

/**
 * sourceUrl에서 뉴스 지역과 사이트 필터를 역추출한다.
 * Google News RSS URL이면 파라미터를 파싱하고, 아니면 외부 사이트로 간주한다.
 */
interface ParsedSourceUrl {
  newsRegion: NewsRegion;
  siteFilters: string[];
  isDirectSource: boolean;
  keyword: string | null;
}

function extractGoogleNewsKeyword(sourceUrl: string): string | null {
  try {
    const url = new URL(sourceUrl);
    const rawQuery = url.searchParams.get("q");
    if (!rawQuery) return null;

    const withoutGroupedSites = rawQuery.replace(/\((?:\s*site:[^)]+)+\s*\)/g, "");
    const withoutInlineSites = withoutGroupedSites.replace(/\s*site:[^\s)]+/g, "");
    const normalized = withoutInlineSites.replace(/\s+/g, " ").trim();
    return normalized || null;
  } catch {
    return null;
  }
}

export function parseSourceUrl(sourceUrl: string): ParsedSourceUrl {
  const defaults = {
    newsRegion: "domestic" as NewsRegion,
    siteFilters: [] as string[],
    isDirectSource: false,
    keyword: null
  };
  if (!sourceUrl) return defaults;

  // Google News RSS가 아닌 경우 — 외부 직접 RSS 소스
  if (!sourceUrl.includes("news.google.com/rss")) {
    return { ...defaults, isDirectSource: true };
  }

  // 지역 추출
  let newsRegion: NewsRegion = "domestic";
  if (sourceUrl.includes("hl=en") && sourceUrl.includes("gl=US")) {
    newsRegion = "international";
  }

  // 사이트 필터 추출: site:xxx.com 패턴
  const siteFilters: string[] = [];
  const decoded = decodeURIComponent(sourceUrl);
  const siteMatches = decoded.matchAll(/site:([^\s)]+)/g);
  for (const m of siteMatches) {
    if (m[1]) siteFilters.push(m[1]);
  }

  return {
    newsRegion,
    siteFilters,
    isDirectSource: false,
    keyword: extractGoogleNewsKeyword(sourceUrl)
  };
}

/**
 * 키워드 항목 목록에서 주제 이름을 자동 생성한다.
 */
export function autoTopicName(entries: KeywordEntry[]): string {
  if (entries.length === 0) return "";
  if (entries.length === 1) return `${entries[0].value} 뉴스`;
  const joined = entries.map((e) => e.value).join("·");
  return `${joined} 뉴스`;
}
