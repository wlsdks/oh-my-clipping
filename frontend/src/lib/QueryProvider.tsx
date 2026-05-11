import { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HTTPError } from "ky";
import { STALE_TIMES } from "./queryConfig";

function shouldThrowError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === "AbortError") {
    return false;
  }
  if (error instanceof HTTPError) {
    return [401, 403, 404].includes(error.response.status);
  }
  return false;
}

// eslint-disable-next-line react-refresh/only-export-components
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 전역 기본값: FREQUENT(30초). 개별 쿼리에서 STALE_TIMES.*로 재정의 가능.
      // 정책 상세: src/lib/queryConfig.ts 참고.
      staleTime: STALE_TIMES.FREQUENT,
      gcTime: 1000 * 60 * 10,
      retry: 1,
      refetchOnWindowFocus: false,
      throwOnError: shouldThrowError
    },
    mutations: {
      retry: 0,
      throwOnError: shouldThrowError
    }
  }
});

interface QueryProviderProps {
  children: ReactNode;
}

export default function QueryProvider({ children }: QueryProviderProps) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
