export const userKeys = {
  all: ["users"] as const,
  accounts: (params?: Record<string, unknown>) =>
    params ? ([...userKeys.all, "accounts", params] as const) : ([...userKeys.all, "accounts"] as const),
  accountSummary: () => [...userKeys.all, "accounts", "summary"] as const,
  requests: (params?: Record<string, unknown>) =>
    params ? ([...userKeys.all, "requests", params] as const) : ([...userKeys.all, "requests"] as const),
  clippingRequests: () => [...userKeys.all, "clipping-requests"] as const,
  subscriptionPreferences: (requestId: string) => [...userKeys.all, "subscription-preferences", requestId] as const,
  deliverySchedule: () => [...userKeys.all, "delivery-schedule"] as const,
  categoryBrowse: () => [...userKeys.all, "category-browse"] as const
};
