export interface FriendlyReason {
  friendly: string;
  detail: string;
}

interface ReasonPattern {
  regex: RegExp;
  friendly: string | ((match: RegExpMatchArray) => string);
}

const PATTERNS: ReasonPattern[] = [
  {
    regex: /^제외 키워드 일치: (.+)$/,
    friendly: (m) => `'${m[1]}' 키워드가 제외 목록에 있어요`,
  },
  {
    regex: /^포함 키워드 일치: (.+)$/,
    friendly: (m) => `'${m[1]}' 키워드가 포함 목록에 있어요`,
  },
  {
    regex: /^콘텐츠 안전성 자동 제외/,
    friendly: "중요도가 매우 낮아 자동으로 제외됐어요",
  },
  {
    regex: /^중요도 .+가 포함 임계치 .+ 이상$/,
    friendly: "중요도가 높아서 자동으로 포함됐어요",
  },
  {
    regex: /^중요도 .+가 검토 임계치 .+ 이상$/,
    friendly: "중요도가 애매해서 직접 확인이 필요해요",
  },
  {
    regex: /^자동 분류 확신 부족/,
    friendly: "AI가 판단하기 어려워서 직접 확인이 필요해요",
  },
  {
    regex: /^검토 임계치 미달 항목 자동 제외$/,
    friendly: "중요도가 낮아 자동으로 제외됐어요",
  },
  {
    regex: /^기본 정책에 따라 자동 포함$/,
    friendly: "기본 정책에 따라 자동으로 포함됐어요",
  },
];

export function toFriendlyReason(raw: string): FriendlyReason {
  if (!raw) return { friendly: "분류 사유 없음", detail: "" };

  for (const pattern of PATTERNS) {
    const match = raw.match(pattern.regex);
    if (match) {
      const friendly =
        typeof pattern.friendly === "function" ? pattern.friendly(match) : pattern.friendly;
      return { friendly, detail: raw };
    }
  }

  return { friendly: raw, detail: raw };
}
