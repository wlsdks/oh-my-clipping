export const personaKeys = {
  all: ["personas"] as const,
  lists: () => [...personaKeys.all, "list"] as const,
  detail: (id: string) => [...personaKeys.all, "detail", id] as const,
  userLists: () => [...personaKeys.all, "user-list"] as const,
  versions: (id: string) => [...personaKeys.all, "versions", id] as const,
  stats: () => [...personaKeys.all, "stats"] as const,
  presets: () => [...personaKeys.all, "presets"] as const
};
