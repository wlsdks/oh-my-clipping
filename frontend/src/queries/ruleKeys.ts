export const ruleKeys = {
  all: ["rules"] as const,
  lists: () => [...ruleKeys.all, "list"] as const,
  detail: (id: string) => [...ruleKeys.all, "detail", id] as const,
  stats: (days?: number) => [...ruleKeys.all, "stats", days ?? 7] as const,
  excludedItems: (categoryId: string) => [...ruleKeys.all, "excluded", categoryId] as const
};
