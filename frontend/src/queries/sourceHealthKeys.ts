export const sourceHealthKeys = {
  all: ["source-health"] as const,
  health: () => [...sourceHealthKeys.all, "summary"] as const,
};
