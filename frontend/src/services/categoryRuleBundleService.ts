import { api } from "@/lib/kyInstance";

/** 백엔드 RuleBundleRequest 와 필드명 일치 — ADR-032. */
export interface RuleBundleRequest {
  excludeEventTypes: string[];
  includeKeywords: string[];
  organizationIds: string[];
  accountBasedDigestEnabled: boolean;
  shadowModeEnabled: boolean;
}

export const categoryRuleBundleService = {
  update: (categoryId: string, body: RuleBundleRequest): Promise<void> =>
    api
      .put(`admin/categories/${encodeURIComponent(categoryId)}/rule-bundle`, { json: body })
      .then(() => undefined),
};
