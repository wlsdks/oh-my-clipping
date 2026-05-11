import { api } from "@/lib/kyInstance";
import type {
  ArticleHistoryPage,
  ArticleDetail,
  BookmarkToggleResult,
  UserMonthlyStatRow,
  UndeliveredDigest,
} from "@/types/insight";

export const userHistoryService = {
  searchArticleHistory: (params: {
    categoryId?: string;
    keyword?: string;
    dateFrom?: string;
    dateTo?: string;
    bookmarkedOnly?: boolean;
    page?: number;
    size?: number;
  }): Promise<ArticleHistoryPage> => {
    const query = new URLSearchParams();
    if (params.categoryId) query.set("categoryId", params.categoryId);
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.dateFrom) query.set("dateFrom", params.dateFrom);
    if (params.dateTo) query.set("dateTo", params.dateTo);
    if (params.bookmarkedOnly) query.set("bookmarkedOnly", "true");
    if (typeof params.page === "number") query.set("page", String(params.page));
    if (typeof params.size === "number") query.set("size", String(params.size));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`user/history/articles${suffix}`).json();
  },

  getArticleDetail: (summaryId: string): Promise<ArticleDetail> =>
    api.get(`user/history/articles/${encodeURIComponent(summaryId)}`).json(),

  toggleArticleBookmark: (summaryId: string): Promise<BookmarkToggleResult> =>
    api.post(`user/history/articles/${encodeURIComponent(summaryId)}/bookmark`, { json: {} }).json(),

  getUserMonthlyStats: (yearMonth: string): Promise<UserMonthlyStatRow[]> => {
    const params = new URLSearchParams({ yearMonth });
    return api.get(`user/stats/monthly?${params.toString()}`).json();
  },

  getUndeliveredDigests: (): Promise<UndeliveredDigest[]> =>
    api.get("user/history/undelivered-digests").json(),
};
