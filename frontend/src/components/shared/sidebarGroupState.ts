export const SIDEBAR_GROUPS_KEY = "clipping-sidebar-groups";

export function readGroupState(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem(SIDEBAR_GROUPS_KEY) ?? "{}");
  } catch {
    return {};
  }
}
