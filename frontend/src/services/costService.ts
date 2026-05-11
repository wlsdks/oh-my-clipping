import { api } from "@/lib/kyInstance";
import type {
  CostOverview,
  HourlyCost,
  CostModels,
  CostReliability,
  CostDetail,
  BudgetSettings,
  BudgetSettingsRequest
} from "@/types/cost";

export interface CurrentBudgetAlertResponse {
  monthId: string;
  currentLevel: "CRITICAL_90" | "CRITICAL_100" | null;
  usagePct: number;
  remainingDays: number;
}

export const costService = {
  getOverview: (from: string, to: string, categoryId?: string) =>
    api
      .get("admin/costs/overview", {
        searchParams: { from, to, ...(categoryId && { categoryId }) }
      })
      .json<CostOverview>(),

  getHourly: (date: string, categoryId?: string) =>
    api
      .get("admin/costs/overview/hourly", {
        searchParams: { date, ...(categoryId && { categoryId }) }
      })
      .json<HourlyCost>(),

  getModels: (from: string, to: string, categoryId?: string) =>
    api
      .get("admin/costs/models", {
        searchParams: { from, to, ...(categoryId && { categoryId }) }
      })
      .json<CostModels>(),

  getReliability: (from: string, to: string, categoryId?: string) =>
    api
      .get("admin/costs/reliability", {
        searchParams: { from, to, ...(categoryId && { categoryId }) }
      })
      .json<CostReliability>(),

  getDetail: (from: string, to: string, categoryId?: string) =>
    api
      .get("admin/costs/detail", {
        searchParams: { from, to, ...(categoryId && { categoryId }) }
      })
      .json<CostDetail>(),

  getBudget: () => api.get("admin/costs/budget").json<BudgetSettings>(),

  updateBudget: (data: BudgetSettingsRequest) => api.put("admin/costs/budget", { json: data }).json<BudgetSettings>(),

  getCurrentBudgetAlert: () =>
    api.get("admin/costs/alerts/current").json<CurrentBudgetAlertResponse>(),
};
