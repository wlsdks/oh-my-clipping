import type { UserClippingRequest } from "@/types/user";
import type { Category } from "@/types/category";

export type SubscriptionFilter =
  | "pending"
  | "active"
  | "warning"
  | "danger"
  | "inactive"
  | "public"
  | "private"
  | "rejected"
  | "withdrawn";

export type SubscriptionPanelItem =
  | { kind: "request"; data: UserClippingRequest }
  | { kind: "category"; data: Category };
