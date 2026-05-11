export const categoryKeys = {
  all: ["categories"] as const,
  lists: () => [...categoryKeys.all, "list"] as const,
  list: (params?: Record<string, unknown>) =>
    [...categoryKeys.all, "list", params] as const,
  detail: (id: string) => [...categoryKeys.all, "detail", id] as const
};
