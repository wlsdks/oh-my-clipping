import type { SourceQualityPeriod } from "@/types/sourceQuality";

export const sourceQualityKeys = {
  all: ["sourceQuality"] as const,
  summary: (period: SourceQualityPeriod) =>
    [...sourceQualityKeys.all, "summary", period] as const,
};
