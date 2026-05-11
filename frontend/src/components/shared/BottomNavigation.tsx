import { Link, useLocation } from "react-router-dom";
import { LayoutDashboard, Rss, ClipboardList, BarChart2, Settings } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/utils/cn";

/**
 * 모바일(<768px)용 하단 탭 네비게이션 항목.
 * 사이드바 5개 그룹을 모바일에서 5개 핵심 탭으로 축약한다.
 */
interface BottomNavItem {
  /** 표시 라벨 */
  label: string;
  /** 라우트 경로 */
  href: string;
  /** 활성화 매칭 시 prefix 허용 여부 */
  matchPrefix: boolean;
  icon: LucideIcon;
}

/** 모바일 탭에 노출할 5개 핵심 라우트. */
const BOTTOM_NAV_ITEMS: BottomNavItem[] = [
  { label: "홈", href: "/admin", matchPrefix: false, icon: LayoutDashboard },
  { label: "콘텐츠", href: "/admin/sources", matchPrefix: true, icon: Rss },
  { label: "운영", href: "/admin/subscriptions", matchPrefix: true, icon: ClipboardList },
  { label: "분석", href: "/admin/analytics", matchPrefix: true, icon: BarChart2 },
  { label: "시스템", href: "/admin/system-status", matchPrefix: true, icon: Settings },
];

/**
 * 현재 경로가 네비게이션 탭과 일치하는지 판정한다.
 * 홈 탭은 정확 경로만 활성, 그 외 탭은 prefix 매칭을 허용한다.
 */
function isNavItemActive(pathname: string, item: BottomNavItem): boolean {
  if (!item.matchPrefix) return pathname === item.href;
  if (pathname === item.href) return true;
  if (!pathname.startsWith(item.href)) return false;
  const nextChar = pathname[item.href.length];
  return nextChar === undefined || nextChar === "/";
}

/**
 * 모바일 하단 탭 네비게이션.
 * 데스크탑(≥md)에서는 sidebar가 담당하므로 렌더되지 않는다.
 */
export function BottomNavigation() {
  const { pathname } = useLocation();

  return (
    <nav
      role="navigation"
      aria-label="모바일 하단 내비게이션"
      className="lg:hidden fixed inset-x-0 bottom-0 z-40 flex border-t border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80"
    >
      {BOTTOM_NAV_ITEMS.map((item) => {
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

/** 테스트용으로 노출 */
export const __BOTTOM_NAV_ITEMS_FOR_TEST = BOTTOM_NAV_ITEMS;
