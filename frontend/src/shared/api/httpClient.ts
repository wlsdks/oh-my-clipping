import type { ApiErrorShape } from "../types/common";
import { redirectToLogin } from "@/lib/kyInstance";

export class ApiError extends Error {
  status: number;
  payload: ApiErrorShape | null;

  constructor(status: number, message: string, payload: ApiErrorShape | null = null) {
    super(message);
    this.status = status;
    this.payload = payload;
  }
}

export async function requestJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = {
    Accept: "application/json",
    ...(options.headers || {})
  } as Record<string, string>;

  const response = await fetch(url, {
    credentials: "include",
    ...options,
    headers
  });

  const raw = await response.text();
  const parsed = parseJsonSafely<ApiErrorShape>(raw);

  if (!response.ok) {
    if (response.status === 401) redirectToLogin();
    const traceText = parsed?.traceId ? ` (추적 ID: ${parsed.traceId})` : "";
    const message = (parsed?.message || raw || `요청 실패 (${response.status})`) + traceText;
    throw new ApiError(response.status, message, parsed);
  }

  return parseJsonSafely<T>(raw) as T;
}

function parseJsonSafely<T>(raw: string): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}
