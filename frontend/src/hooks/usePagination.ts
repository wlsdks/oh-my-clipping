import { useState } from "react";

interface PaginationState {
  page: number;
  size: number;
  totalPages: number;
}

export function usePagination(initialSize = 20) {
  const [state, setState] = useState<PaginationState>({
    page: 0,
    size: initialSize,
    totalPages: 0
  });

  const setPage = (page: number) => setState((prev) => ({ ...prev, page }));
  const setTotalPages = (totalPages: number) => setState((prev) => ({ ...prev, totalPages }));

  return { ...state, setPage, setTotalPages };
}
