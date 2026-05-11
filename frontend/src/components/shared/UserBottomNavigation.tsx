import { Link, useLocation } from "react-router-dom";
import {
  LayoutDashboard,
  ClipboardList,
  Compass,
  Newspaper,
  FileText,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/utils/cn";

/**
 * 사용자(User) 뷰 모바일 하단 탭 네비게이션 항목.
 */
interface UserBottomNavItem {
  label: string;
  href: string;
  matchPrefix: boolean;
  icon: LucideIcon;
}

/** 사용자 모바일 탭 5개 — 사이드바 주요 메뉴 가운데 자주 쓰는 것만 골랐다. */
const USER_BOTTOM_NAV_ITEMS: UserBottomNavItem[] = [
  { label: "홈", href: "/user", matchPrefix: false, icon: LayoutDashboard },
  { label: "구독", href: "/user/manage", matchPrefix: true, icon: ClipboardList },
  { label: "탐색", href: "/user/browse", matchPrefix: true, icon: Compass },
  { label: "리포트", href: "/user/news-report", matchPrefix: true, icon: Newspaper },
  { label: "기사", href: "/user/articles", matchPrefix: true, icon: FileText },
];

/** 현재 경로와 탭이 일치하는지 판정한다. */
function isNavItemActive(pathname: string, item: UserBottomNavItem): boolean {
  if (!item.matchPrefix) return pathname === item.href;
  if (pathname === item.href) return true;
  if (!pathname.startsWith(item.href)) return false;
  const nextChar = pathname[item.href.length];
  return nextChar === undefined || nextChar === "/";
}

/**
 * 사용자 뷰 모바일 하단 네비게이션.
 * 데스크탑(≥md)에서는 sidebar가 담당하므로 렌더되지 않는다.
 */
export function UserBottomNavigation() {
  const { pathname } = useLocation();

  return (
    <nav
      role="navigation"
      aria-label="모바일 하단 내비게이션"
      className="lg:hidden fixed inset-x-0 bottom-0 z-40 flex border-t border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80"
    >
      {USER_BOTTOM_NAV_ITEMS.map((item) => {
        const active = isNavItemActive(pathname, item);
        return (
          <Link
            key={item.href}
            to={item.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex flex-1 flex-col items-center justify-center gap-1 py-2 text-[11px] font-medium transition-colors",
              active
                ? "text-primary"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            <item.icon
              size={20}
              aria-hidden="true"
              className={cn(active && "scale-110 transition-transform")}
            />
            <span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

export const __USER_BOTTOM_NAV_ITEMS_FOR_TEST = USER_BOTTOM_NAV_ITEMS;
