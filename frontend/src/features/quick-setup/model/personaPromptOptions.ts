/**
 * 페르소나(요약 스타일) 커스텀 편집 시 사용하는 프롬프트 옵션 상수.
 *
 * 4개 카테고리:
 * - PERSPECTIVE: 관점 (1개 선택, 빈 snippet = "일반 독자" 기본값)
 * - LENGTH: 분량 (1개 선택)
 * - TONE: 말투 (1개 선택)
 * - ADDON: 추가 옵션 (여러 개 선택 가능)
 */

export interface PromptOption {
  label: string;
  snippet: string;
}

export const LENGTH_OPTIONS: PromptOption[] = [
  {
    label: "한줄 요약",
    snippet: "핵심 내용을 50자 이내 한 문장으로 요약해주세요."
  },
  {
    label: "짧고 간결하게",
    snippet: "핵심만 3줄 이내로 짧게 정리해주세요."
  },
  {
    label: "보통 분량",
    snippet: "핵심 내용과 의미를 포함해 4~6줄로 요약해주세요."
  },
  {
    label: "자세하고 길게",
    snippet: "배경과 맥락을 포함해 8~12줄로 자세하게 작성해주세요."
  }
];

export const TONE_OPTIONS: PromptOption[] = [
  {
    label: "격식체",
    snippet: "보고서나 이메일에 적합한 격식 있는 문체(~입니다)로 작성해주세요."
  },
  {
    label: "편한 말투",
    snippet: "친구에게 설명하듯 편한 문체(~해요)로 작성해주세요."
  },
  {
    label: "분석 중심",
    snippet: "수치와 근거를 중심으로 논리적인 분석 문체로 작성해주세요."
  },
  {
    label: "뉴스 톤",
    snippet: "뉴스 앵커처럼 객관적인 보도 문체로 전달해주세요."
  }
];

export const ADDON_OPTIONS: PromptOption[] = [
  {
    label: "쉬운 말로",
    snippet: "어려운 용어는 쉬운 말로 풀어서 설명해주세요."
  },
  {
    label: "왜 중요한지",
    snippet: "요약 끝에 '왜 중요한가:' 한 줄을 덧붙여주세요."
  },
  {
    label: "할 일 제안",
    snippet: "요약 마지막에 '할 수 있는 것:' 1줄을 추가해주세요."
  },
  {
    label: "이모지 사용",
    snippet: "각 문단 앞에 적절한 이모지를 하나씩 붙여주세요."
  },
  {
    label: "핵심 수치 강조",
    snippet: "핵심 수치나 통계를 굵게 강조해서 표시해주세요."
  },
  {
    label: "한줄 결론",
    snippet: "요약 끝에 '결론:' 한 문장으로 마무리해주세요."
  },
  {
    label: "출처 명시",
    snippet: "인용이나 수치에는 원문에 명시된 출처를 함께 적어주세요. 출처가 없으면 생략하세요."
  }
];

export const PERSPECTIVE_OPTIONS: PromptOption[] = [
  {
    label: "일반 독자",
    snippet: ""
  },
  {
    label: "교육 기획",
    snippet: "기업교육·HRD·인재개발 관점에서 시사점을 분석해주세요."
  },
  {
    label: "영업·마케팅",
    snippet: "B2B 영업 기회와 마케팅 활용 관점에서 분석해주세요."
  },
  {
    label: "기술·개발",
    snippet: "기술 스택, 구현 가능성, 기술 트렌드 관점에서 분석해주세요."
  }
];

/**
 * 프롬프트 안에 지정된 snippet 이 한 줄로 존재하는지 확인한다.
 * 부분 일치는 false, 라인 단위 완전 일치만 true 로 판정한다.
 */
export function hasSnippet(prompt: string, snippet: string): boolean {
  return prompt.split("\n").some((line) => line.trim() === snippet.trim());
}
