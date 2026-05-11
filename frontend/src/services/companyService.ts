import { api } from "@/lib/kyInstance";

export interface CompanySearchResult {
  corpCode: string;
  corpName: string;
  stockCode: string;
  /** 신규 — Backend Task 18. 경쟁사 워치리스트 매치 시 true. 기본 미정의/false. */
  isCompetitor?: boolean;
}

export const companyService = {
  searchAdminCompanies: (q: string, limit = 20): Promise<CompanySearchResult[]> => {
    const params = new URLSearchParams({ q, limit: String(limit) });
    return api.get(`admin/companies?${params.toString()}`).json();
  },

  searchUserCompanies: (q: string, limit = 20): Promise<CompanySearchResult[]> => {
    const params = new URLSearchParams({ q, limit: String(limit) });
    return api.get(`user/companies?${params.toString()}`).json();
  }
};
