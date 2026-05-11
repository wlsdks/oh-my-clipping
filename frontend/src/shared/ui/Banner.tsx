import type { PropsWithChildren } from "react";

type BannerType = "info" | "success" | "warning" | "error";

interface BannerProps extends PropsWithChildren {
  type?: BannerType;
}

export function Banner({ type = "info", children }: BannerProps) {
  if (!children) return null;
  return <div className={`banner banner-${type}`}>{children}</div>;
}
