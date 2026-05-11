import type { OrganizationType } from "@/types/organization";

/** Organization 쿼리 키 팩토리. */
export const organizationKeys = {
  all: ["organizations"] as const,
  lists: () => [...organizationKeys.all, "list"] as const,
  list: (type?: OrganizationType) => [...organizationKeys.lists(), type ?? "all"] as const,
  detail: (id: string) => [...organizationKeys.all, "detail", id] as const,
  byCategory: (categoryId: string) =>
    [...organizationKeys.all, "byCategory", categoryId] as const,
};
