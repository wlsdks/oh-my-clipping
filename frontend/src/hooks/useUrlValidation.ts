import { useState, useRef } from "react";
import { api } from "@/lib/kyInstance";

export interface UrlValidationResult {
  rssValid: boolean;
  robotsAllowed: boolean;
  domainBlocked: boolean;
  blockReason: string | null;
  existingSource: { name: string; legalBasis: string } | null;
}

export type ValidationStatus = "idle" | "loading" | "success" | "warning" | "error";

export interface UseUrlValidationReturn {
  status: ValidationStatus;
  result: UrlValidationResult | null;
  messages: string[];
  validate: (url: string) => Promise<void>;
  reset: () => void;
  validatedUrl: string | null;
}

/**
 * URL 사전 검증 훅.
 * stale 방지: 검증 성공 후 URL이 변경되면 validatedUrl과 불일치하여 검증 무효화.
 */
export function useUrlValidation(): UseUrlValidationReturn {
  const [status, setStatus] = useState<ValidationStatus>("idle");
  const [result, setResult] = useState<UrlValidationResult | null>(null);
  const [messages, setMessages] = useState<string[]>([]);
  const [validatedUrl, setValidatedUrl] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  function reset() {
    setStatus("idle");
    setResult(null);
    setMessages([]);
    setValidatedUrl(null);
  }

  async function validate(url: string) {
    // 이전 요청이 있으면 취소한다.
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setStatus("loading");
    setMessages(["차단 목록 확인 중..."]);
    setResult(null);
    setValidatedUrl(null);

    try {
      const res = await api
        .post("user/sources/validate-url", {
          json: { url },
          signal: controller.signal,
          timeout: 15000,
        })
        .json<UrlValidationResult>();

      if (controller.signal.aborted) return;

      setResult(res);

      const msgs: string[] = [];

      if (res.domainBlocked) {
        msgs.push(res.blockReason ?? "이 도메인은 사용이 금지되어 있어요.");
        setStatus("error");
        setMessages(msgs);
        return;
      }

      if (!res.rssValid) {
        msgs.push("이 주소에서 RSS 피드를 찾지 못했어요.");
        setStatus("error");
        setMessages(msgs);
        return;
      }

      // RSS 유효
      msgs.push("RSS 피드 확인됨");

      if (!res.robotsAllowed) {
        msgs.push("이 사이트가 자동 수집을 제한하고 있어요. 관리자가 검토합니다.");
        setStatus("warning");
      } else {
        msgs.push("수집 가능");
        setStatus("success");
      }

      if (res.existingSource) {
        msgs.push(`이미 등록된 소스: ${res.existingSource.name}`);
      } else {
        msgs.push("관리자가 저작권을 검토합니다");
      }

      setMessages(msgs);
      setValidatedUrl(url);
    } catch {
      if (controller.signal.aborted) return;
      setStatus("warning");
      setMessages(["검증을 완료하지 못했어요. 제출은 가능하지만 관리자가 확인합니다."]);
      setValidatedUrl(url);
    }
  }

  return { status, result, messages, validate, reset, validatedUrl };
}
