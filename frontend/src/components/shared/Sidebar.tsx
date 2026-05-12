import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { LogOut, ChevronDown, Key } from "lucide-react";
import { cn } from "@/utils/cn";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { adminRoutes, type AdminRouteGroup } from "@/router/adminRoutes";
import { ThemeToggle } from "./ThemeToggle";
import { ClippingLogo } from "./ClippingLogo";
import { ChangePasswordModal } from "./ChangePasswordModal";
import { SIDEBAR_GROUPS_KEY, readGroupState } from "./sidebarGroupState";
import { authService } from "@/services/authService";
import { authStore } from "@/store/authStore";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import { reviewKeys } from "@/queries/reviewKeys";
import { reviewService } from "@/services/reviewService";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { deliveryService } from "@/services/deliveryService";
import { pipelineKeys } from "@/queries/pipelineKeys";
import { pipelineService } from "@/services/pipelineService";
import { sourceKeys } from "@/queries/sourceKeys";
import { sourceService } from "@/services/sourceService";
import { SidebarBadgeWithTooltip } from "./SidebarBadgeWithTooltip";
import { formatBadgeTooltip } from "./sidebarTooltips";
import {
  oldestIsoDate,
  isBadgeKind,
  type SidebarBadges,
} from "./sidebarBadges";

const GROUP_LABELS: Record<AdminRouteGroup, string> = {
  home: "",
  content: "콘텐츠 설정",
  ops: "운영",
  analysis: "분석",
  system: "시스템"
};

function isRouteActive(route: { href: string }, pathname: string): boolean {
  if (route.href === "/admin") return pathname === route.href;
  if (route.href === "/admin/runtime") return pathname.startsWith(route.href);
  // 리뷰 큐 상위 링크는 자동 제외 감사 하위 경로를 자기 active 로 가져가지 않도록 정확 일치로 처리.
  if (route.href === "/admin/review-queue") return pathname === route.href;
  if (!pathname.startsWith(route.href)) return false;
  const nextChar = pathname[route.href.length];
  return nextChar === undefined || nextChar === "/";
}

interface CollapsibleGroupProps {
  groupKey: string;
  label: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

function CollapsibleGroup({ groupKey, label, defaultOpen = false, children }: CollapsibleGroupProps) {
  const stored = readGroupState();
  const [open, setOpen] = useState<boolean>(groupKey in stored ? stored[groupKey] : defaultOpen);

  function toggle() {
    const next = !open;
    setOpen(next);
    const current = readGroupState();
    localStorage.setItem(SIDEBAR_GROUPS_KEY, JSON.stringify({ ...current, [groupKey]: next }));
  }

  return (
    <div>
      <button
        onClick={toggle}
        className="flex w-full items-center justify-between px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/60 hover:text-sidebar-foreground/80 transition-colors"
        aria-expanded={open}
      >
        <span>{label}</span>
        <ChevronDown
          size={14}
          className={cn(
            "transition-transform duration-200",
            open ? "rotate-0" : "-rotate-90"
          )}
        />
      </button>
      <div
        className={cn(
          "grid transition-[grid-template-rows] duration-200 ease-in-out",
          open ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
        )}
      >
        <div className="overflow-hidden">
          {children}
        </div>
      </div>
    </div>
  );
}

function useSidebarBadges(): SidebarBadges {
  const { data: pendingAccounts = [] } = useQuery({
    queryKey: userKeys.accounts({ status: "PENDING", badge: true }),
    queryFn: () => userService.listAdminUserAccounts("PENDING"),
    staleTime: 30_000,
  });
  const { data: reviewItems = [] } = useQuery({
    queryKey: reviewKeys.queue({ status: "REVIEW", badge: true }),
    queryFn: () => reviewService.listItems({ status: "REVIEW", limit: 100 }),
    staleTime: 30_000,
  });
  const { data: deliverySummary } = useQuery({
    queryKey: deliveryKeys.summary("badge"),
    queryFn: () => deliveryService.getSummary(),
    staleTime: 30_000,
  });
  // 발송 실패 배지의 최초 실패 시각을 얻기 위해 최근 24시간 FAILED 로그를 함께 조회한다.
  const failedDeliveryCount = deliverySummary?.failedCount ?? 0;
  const { data: failedDeliveryLogs } = useQuery({
    queryKey: deliveryKeys.logsList({ status: "FAILED", badge: true, within: "1d" }),
    queryFn: () =>
      deliveryService.listLogs(
        new URLSearchParams({ status: "FAILED", size: "100" }),
        "1d",
      ),
    staleTime: 30_000,
    enabled: failedDeliveryCount > 0,
  });
  // 파이프라인 실패 배지: 24h 내 실패 집합을 한 번에 받아 count + oldest 를 동시에 계산한다.
  const { data: failedRuns } = useQuery({
    queryKey: pipelineKeys.runsList({ status: "FAILED", badge: true, within: "1d" }),
    queryFn: () =>
      pipelineService.listRuns(
        new URLSearchParams({ status: "FAILED", size: "100" }),
        "1d",
      ),
    staleTime: 30_000,
  });
  const { data: pendingRequests = [] } = useQuery({
    queryKey: userKeys.requests({ status: "PENDING", badge: true }),
    queryFn: () => userService.listAdminClippingRequests("PENDING"),
    staleTime: 30_000,
  });
  // 소스 저작권 재검토 필요 건수. 만료/만료 임박/미검토를 합산한 값이 서버에서 반환된다.
  const { data: complianceSummary } = useQuery({
    queryKey: sourceKeys.complianceSummary(),
    queryFn: () => sourceService.getComplianceSummary(),
    staleTime: 60_000,
  });

  const pipelineRunsContent = failedRuns?.content ?? [];
  const pipelineOldest = oldestIsoDate(pipelineRunsContent.map((r) => r.startedAt));

  const deliveryFailedContent = failedDeliveryLogs?.content ?? [];
  const deliveryOldest = oldestIsoDate(deliveryFailedContent.map((r) => r.createdAt));

  return {
    userAccounts: {
      count: pendingAccounts.length,
      oldestCreatedAt: oldestIsoDate(pendingAccounts.map((a) => a.createdAt)),
    },
    reviewQueue: {
      count: reviewItems.length,
      oldestCreatedAt: oldestIsoDate(reviewItems.map((r) => r.createdAt)),
    },
    delivery: {
      count: failedDeliveryCount,
      oldestCreatedAt: deliveryOldest,
    },
    pipeline: {
      count: failedRuns?.totalCount ?? 0,
      oldestCreatedAt: pipelineOldest,
    },
    subscriptions: {
      count: pendingRequests.length,
      oldestCreatedAt: oldestIsoDate(pendingRequests.map((r) => r.createdAt)),
    },
    sources: {
      count: complianceSummary?.attentionCount ?? 0,
      oldestCreatedAt: null,
    },
  } satisfies SidebarBadges;
}

interface SidebarProps {
  /** Called after a nav link is clicked (used by mobile drawer to close itself) */
  onNavigate?: () => void;
}

export function Sidebar({ onNavigate }: SidebarProps) {
  const { pathname } = useLocation();
  const groups: AdminRouteGroup[] = ["home", "content", "ops", "analysis", "system"];
  const badgeCounts = useSidebarBadges();
  const logoClickCount = useRef(0);
  const logoClickTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const logoSwingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [swinging, setSwinging] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);

  function handleLogoClick() {
    logoClickCount.current += 1;
    if (logoClickTimer.current) clearTimeout(logoClickTimer.current);
    if (logoClickCount.current >= 5) {
      logoClickCount.current = 0;
      setSwinging(true);
      if (logoSwingTimer.current) clearTimeout(logoSwingTimer.current);
      logoSwingTimer.current = setTimeout(() => {
        setSwinging(false);
        logoSwingTimer.current = null;
      }, 1200);
    } else {
      logoClickTimer.current = setTimeout(() => {
        logoClickCount.current = 0;
        logoClickTimer.current = null;
      }, 1500);
    }
  }

  useEffect(() => {
    return () => {
      if (logoClickTimer.current) clearTimeout(logoClickTimer.current);
      if (logoSwingTimer.current) clearTimeout(logoSwingTimer.current);
    };
  }, []);

  const { mutate: logout } = useMutation({
    mutationFn: authService.logout,
    onSettled: () => {
      authStore.getState().logout();
      window.location.assign("/login");
    }
  });

  function renderBadge(route: (typeof adminRoutes)[number]) {
    if (!route.badgeQueryEnabled) return null;
    if (!isBadgeKind(route.id)) {
      if (import.meta.env.DEV) {
        console.warn(
          `[Sidebar] route "${route.id}" has badgeQueryEnabled but is not a BadgeKind. ` +
          `Add it to BadgeKind in sidebarTooltips.ts to surface a badge + tooltip.`
        );
      }
      return null;
    }
    const data = badgeCounts[route.id];
    if (data.count === 0) return null;
    const tooltip = formatBadgeTooltip(route.id, data.count, data.oldestCreatedAt, new Date());
    return (
      <SidebarBadgeWithTooltip
        count={data.count}
        tooltipText={tooltip}
        variant={route.badgeVariant === "destructive" ? "destructive" : "default"}
        ariaLabel={`${route.label} ${data.count}건`}
      />
    );
  }

  return (
    <TooltipProvider delayDuration={200} skipDelayDuration={300}>
    <aside className="flex h-screen w-64 flex-col bg-sidebar text-sidebar-foreground border-r border-sidebar-border" aria-label="사이드바 내비게이션">
      <div className="flex items-center justify-between px-4 py-3.5 border-b border-sidebar-border">
        <button type="button" className="flex items-center gap-2.5 cursor-pointer bg-transparent border-none p-0" onClick={handleLogoClick} aria-label="Clipping">
          <div
            className={cn(
              "flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg",
              swinging && "animate-[clipping-swing_1.2s_ease-in-out]"
            )}
            style={{ animation: swinging ? undefined : "clipping-ignite 0.6s ease-out forwards" }}
          >
            <ClippingLogo size={20} className="text-primary" />
          </div>
          <div>
            <div className="text-sm font-bold text-sidebar-foreground leading-tight">Clipping</div>
            <div className="text-[10px] text-sidebar-foreground/70">당신의 뉴스를 밝히다</div>
          </div>
        </button>
        <ThemeToggle />
      </div>
      <nav className="flex-1 overflow-y-auto py-2">
        {groups.map((group) => {
          const items = adminRoutes.filter((r) => r.group === group);
          if (!items.length) return null;

          if (group === "home") {
            return (
              <div key={group} className="px-2 pb-2">
                {items.map((route) => (
                  <Tooltip key={route.id}>
                    <TooltipTrigger asChild>
                      <Link
                        to={route.href}
                        onClick={onNavigate}
                        className={cn(
                          "flex items-center gap-2.5 px-3 py-2 text-sm rounded-lg transition-colors hover:bg-sidebar-accent",
                          isRouteActive(route, pathname)
                            ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                            : "text-sidebar-foreground"
                        )}
                      >
                        <route.icon size={16} />
                        <span>{route.label}</span>
                        {renderBadge(route)}
                      </Link>
                    </TooltipTrigger>
                    <TooltipContent side="right">{route.summary}</TooltipContent>
                  </Tooltip>
                ))}
              </div>
            );
          }

          return (
            <CollapsibleGroup key={group} groupKey={group} label={GROUP_LABELS[group]} defaultOpen>
              <div className="px-2 pb-2">
                {items.map((route) => (
                  <Tooltip key={route.id}>
                    <TooltipTrigger asChild>
                      <Link
                        to={route.href}
                        onClick={onNavigate}
                        className={cn(
                          "flex items-center gap-2.5 px-3 py-2 text-sm rounded-lg transition-colors hover:bg-sidebar-accent",
                          isRouteActive(route, pathname)
                            ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                            : "text-sidebar-foreground"
                        )}
                      >
                        <route.icon size={16} />
                        <span>{route.label}</span>
                        {renderBadge(route)}
                      </Link>
                    </TooltipTrigger>
                    <TooltipContent side="right">{route.summary}</TooltipContent>
                  </Tooltip>
                ))}
              </div>
            </CollapsibleGroup>
          );
        })}
      </nav>
      <div className="border-t border-sidebar-border p-4 space-y-2">
        <button
          onClick={() => setShowChangePassword(true)}
          className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
        >
          <Key size={18} />
          <span>비밀번호 변경</span>
        </button>
        <button
          onClick={() => logout()}
          className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
        >
          <LogOut size={18} />
          <span>로그아웃</span>
        </button>
        <p className="text-xs text-sidebar-foreground/60 text-center px-2">© 2026 Aslan</p>
      </div>
      <ChangePasswordModal
        open={showChangePassword}
        onClose={() => setShowChangePassword(false)}
        onSuccess={() => setShowChangePassword(false)}
      />
    </aside>
    </TooltipProvider>
  );
}
