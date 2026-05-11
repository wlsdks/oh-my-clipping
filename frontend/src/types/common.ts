export interface PaginatedResponse<T> {
  content: T[];
  totalCount: number;
  page: number;
  size: number;
}

export interface ApiErrorShape {
  message: string;
  traceId?: string;
  code?: string;
}

export type SortDirection = "ASC" | "DESC";
