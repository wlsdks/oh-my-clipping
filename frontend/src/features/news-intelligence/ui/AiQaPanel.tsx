import { useState, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { userFriendlyMessage } from "../../../shared/lib/httpError";
import { cn } from "@/utils/cn";
import type { AiQaResponse } from "../../../shared/types/admin";

interface AiQaPanelProps {
  days?: number;
  categoryId?: string;
}

const SUGGESTED_QUESTIONS = [
  "이번 주 가장 주목할 뉴스 트렌드는?",
  "부정적 기사가 증가한 이유는?",
  "경쟁사 관련 주요 이슈 요약",
  "향후 주목할 키워드 예측",
];

/**
 * AI 뉴스 Q&A 패널.
 * 사용자가 자유 질문 또는 추천 질문을 선택하면
 * 백엔드 AI에게 뉴스 기반 답변을 요청하고 결과를 표시한다.
 */
export function AiQaPanel({ days, categoryId }: AiQaPanelProps) {
  const [input, setInput] = useState("");
  const [answer, setAnswer] = useState<AiQaResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const mutation = useMutation({
    mutationFn: (question: string) =>
      newsIntelligenceService.askAiQuestion({ question, days, categoryId }),
    onSuccess: (result) => {
      setAnswer(result);
      setErrorMsg(null);
      setInput("");
    },
    onError: (err) => {
      setErrorMsg(userFriendlyMessage(err, "질문을 처리하지 못했어요"));
    },
  });

  function handleAsk(question: string) {
    const q = question.trim();
    if (!q || mutation.isPending) return;
    mutation.mutate(q);
  }

  function handleRetry() {
    if (answer?.question) {
      handleAsk(answer.question);
    }
  }

  const isLoading = mutation.isPending;

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>AI 뉴스 Q&amp;A</h3>
      </div>

      {/* 추천 질문 칩 */}
      <div className="flex flex-wrap gap-2 mb-4">
        {SUGGESTED_QUESTIONS.map((q) => (
          <button
            key={q}
            className="chip-btn"
            disabled={isLoading}
            aria-label={`추천 질문: ${q}`}
            onClick={() => {
              setInput(q);
              handleAsk(q);
            }}
          >
            {q}
          </button>
        ))}
      </div>

      {/* 입력 영역 */}
      <div className="flex gap-2 mb-5">
        <input
          ref={inputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") handleAsk(input);
          }}
          placeholder="뉴스에 대해 궁금한 점을 질문하세요..."
          disabled={isLoading}
          className="flex-1 px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none transition-shadow duration-150 focus:ring-2 focus:ring-ring/20 bg-input"
        />
        <button
          onClick={() => handleAsk(input)}
          disabled={isLoading || !input.trim()}
          aria-label="질문 전송"
          className={cn(
            "bg-primary text-primary-foreground border-none rounded-xl px-5 py-2.5 text-sm font-semibold whitespace-nowrap transition-opacity duration-150",
            isLoading || !input.trim() ? "opacity-50 cursor-not-allowed" : "cursor-pointer"
          )}
        >
          {isLoading ? "분석 중..." : "질문하기"}
        </button>
      </div>

      {/* 로딩 */}
      {isLoading && (
        <div className="flex items-center gap-2 p-5 text-muted-foreground text-sm">
          <span className="spinner w-[18px] h-[18px] border-2" />
          분석 중...
        </div>
      )}

      {/* 에러 */}
      {errorMsg && !isLoading && (
        <div className="px-5 py-4 bg-[var(--status-danger-bg)] rounded-[14px] text-destructive text-sm flex items-center justify-between">
          <span>{errorMsg}</span>
          <button
            onClick={handleRetry}
            aria-label="질문 다시 시도"
            className="bg-transparent border border-destructive rounded-lg text-destructive px-3 py-1 text-[13px] cursor-pointer hover:bg-destructive/10 transition-colors"
          >
            다시 시도
          </button>
        </div>
      )}

      {/* 답변 */}
      {answer && !isLoading && (
        <div>
          {/* 질문 표시 */}
          <div className="text-[13px] text-muted-foreground mb-2">
            Q. {answer.question}
            {answer.contextArticleCount > 0 && (
              <span className="ml-2 px-2 py-0.5 bg-secondary rounded-full text-[11px]">
                {answer.contextArticleCount}건 기사 참고
              </span>
            )}
          </div>

          {/* 답변 본문 */}
          <div
            className={cn(
              "p-5 bg-muted rounded-[14px] leading-relaxed text-sm text-foreground/80 dark:text-foreground/70 whitespace-pre-wrap",
              answer.relatedArticles.length > 0 && "mb-4"
            )}
          >
            {answer.answer}
          </div>

          {/* 관련 기사 */}
          {answer.relatedArticles.length > 0 && (
            <div>
              <div className="text-[13px] font-semibold text-foreground mb-2">
                관련 기사
              </div>
              <div className="flex flex-col gap-1.5">
                {answer.relatedArticles.map((article) => (
                  <a
                    key={article.summaryId}
                    href={article.sourceLink}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block px-3.5 py-2.5 bg-secondary rounded-[10px] no-underline text-foreground text-[13px] transition-colors duration-150 hover:bg-secondary/70"
                  >
                    <div className="font-medium mb-0.5">{article.title}</div>
                    <div className="text-xs text-muted-foreground">
                      {article.relevanceReason}
                    </div>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
