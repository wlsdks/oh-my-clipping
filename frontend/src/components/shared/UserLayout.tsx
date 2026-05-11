import { useEffect, useRef, useState } from "react";
import { Link, useLocation, Outlet } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { LayoutDashboard, ClipboardList, RefreshCw, Newspaper, FileText, Compass, LogOut, UserX, Key, Menu, Download, UserCog } from "lucide-react";
import { ClippingLogo } from "./ClippingLogo";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/utils/cn";
import { ThemeToggle } from "./ThemeToggle";
import { MaintenanceBanner } from "./MaintenanceBanner";
import { UserBottomNavigation } from "./UserBottomNavigation";
import { authService } from "@/services/authService";
import { authStore } from "@/store/authStore";
import { userService } from "@/services/userService";
import { userKeys } from "@/queries/userKeys";
import { WithdrawAccountDialog } from "@/components/shared/WithdrawAccountDialog";
import { DataExportDialog } from "@/components/shared/DataExportDialog";
import { ChangePasswordModal } from "@/components/shared/ChangePasswordModal";
import { ProfileEditModal } from "@/components/shared/ProfileEditModal";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useRouteChangeFocus } from "@/hooks/useRouteChangeFocus";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { tracker } from "@/shared/lib/tracker";

interface UserNavItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

const userNavItems: UserNavItem[] = [
  { label: "홈", href: "/user", icon: LayoutDashboard },
  { label: "내 구독 관리", href: "/user/manage", icon: ClipboardList },
  { label: "구독 가능한 주제", href: "/user/browse", icon: Compass },
  { label: "신청 내역", href: "/user/history", icon: RefreshCw },
  { label: "뉴스 리포트", href: "/user/news-report", icon: Newspaper },
  { label: "내 기사 목록", href: "/user/articles", icon: FileText }
];

interface UserSidebarContentProps {
  onNavigate?: () => void;
}

function UserSidebarContent({ onNavigate }: UserSidebarContentProps) {
  const { pathname } = useLocation();
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [dataExportOpen, setDataExportOpen] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [profileEditOpen, setProfileEditOpen] = useState(false);
  const user = authStore.getState().user;

  const { mutate: logout } = useMutation({
    mutationFn: authService.logout,
    onSettled: () => {
      authStore.getState().logout();
      window.location.assign("/login");
    }
  });

  const { data: clippingRequests = [] } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests(),
  });

  const pendingCount = clippingRequests.filter((r: { status: string }) => r.status === "PENDING").length;

  return (
    <>
      <aside className="flex h-screen w-64 flex-col bg-sidebar text-sidebar-foreground border-r border-sidebar-border" aria-label="사이드바 내비게이션">
        <div className="flex items-center justify-between px-4 py-3.5 border-b border-sidebar-border">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg">
              <ClippingLogo size={20} className="text-primary" />
            </div>
            <div>
              <div className="text-sm font-bold text-sidebar-foreground leading-tight">Clipping</div>
              <div className="text-[10px] text-sidebar-foreground/70">당신의 뉴스를 밝히다</div>
            </div>
          </div>
          <ThemeToggle />
        </div>
        <nav className="flex-1 overflow-y-auto py-4 space-y-1">
          {userNavItems.map((item) => {
            const isHistory = item.href === "/user/history";
            const isActive =
              pathname === item.href || (item.href !== "/user" && pathname.startsWith(item.href));
            return (
              <Link
                key={item.href}
                to={item.href}
                onClick={onNavigate}
                className={cn(
                  "flex items-center gap-2.5 px-4 py-2.5 text-sm transition-colors hover:bg-sidebar-accent",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                    : "text-sidebar-foreground"
                )}
              >
                <item.icon size={18} />
                <span className="flex-1">{item.label}</span>
                {isHistory && pendingCount > 0 && (
                  <span className="bg-primary text-primary-foreground rounded-full text-[10px] px-1.5 min-w-[18px] h-[18px] flex items-center justify-center font-medium">
                    {pendingCount}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-sidebar-border p-4 space-y-1">
          <button
            onClick={() => setProfileEditOpen(true)}
            className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
          >
            <UserCog size={18} />
            <span>프로필 편집</span>
          </button>
          <button
            onClick={() => setShowChangePassword(true)}
            className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
          >
            <Key size={18} />
            <span>비밀번호 변경</span>
          </button>
          <button
            onClick={() => setDataExportOpen(true)}
            className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
          >
            <Download size={18} />
            <span>개인정보 내려받기</span>
          </button>
          <button
            onClick={() => logout()}
            className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
          >
            <LogOut size={18} />
            <span>로그아웃</span>
          </button>
          <button
            onClick={() => setWithdrawOpen(true)}
            className="flex items-center gap-2.5 w-full text-left text-sm text-sidebar-foreground/60 hover:text-destructive transition-colors py-2 px-2 rounded hover:bg-sidebar-accent"
          >
            <UserX size={16} />
            <span>회원 탈퇴</span>
          </button>
          <p className="text-xs text-sidebar-foreground/60 text-center px-2 pt-1">© 2026 Aslan</p>
        </div>
      </aside>

      {user && withdrawOpen && (
        <WithdrawAccountDialog
          open={withdrawOpen}
          userId={user.id}
          onClose={() => setWithdrawOpen(false)}
        />
      )}

      <DataExportDialog
        open={dataExportOpen}
        onClose={() => setDataExportOpen(false)}
      />

      <ChangePasswordModal
        open={showChangePassword}
        onClose={() => setShowChangePassword(false)}
        onSuccess={() => setShowChangePassword(false)}
      />

      <ProfileEditModal
        open={profileEditOpen}
        initialDepartmentId={user?.departmentId ?? null}
        initialTeamId={user?.teamId ?? null}
        initialDepartment={user?.department ?? null}
        initialTeam={user?.team ?? null}
        onClose={() => setProfileEditOpen(false)}
        onSaved={(result) => {
          // 저장 성공 시 authStore 의 FK + legacy 이름 캐시를 모두 JOIN 결과로 동기화한다.
          const current = authStore.getState().user;
          if (current) {
            authStore.getState().login({
              ...current,
              departmentId: result.departmentId,
              departmentName: result.departmentName,
              teamId: result.teamId,
              teamName: result.teamName,
              department: result.departmentName,
              team: result.teamName,
            });
          }
        }}
      />
    </>
  );
}

export function UserLayout() {
  const mainRef = useRef<HTMLElement>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { pathname } = useLocation();
  useDocumentTitle();
  useRouteChangeFocus(mainRef);

  useEffect(() => {
    tracker.pageView(pathname);
  }, [pathname]);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-4 focus:bg-background focus:text-foreground focus:rounded-md focus:shadow-lg">
        본문으로 건너뛰기
      </a>

      {/* Desktop sidebar */}
      <div className="hidden lg:block">
        <UserSidebarContent />
      </div>

      {/* Mobile sidebar (Sheet drawer) */}
      <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="sr-only">내비게이션 메뉴</SheetTitle>
          <UserSidebarContent onNavigate={() => setSidebarOpen(false)} />
        </SheetContent>
      </Sheet>

      <main ref={mainRef} id="main-content" tabIndex={-1} className="flex-1 flex flex-col overflow-hidden outline-none">
        {/* Mobile header with hamburger */}
        <div className="flex items-center gap-3 border-b border-border px-4 py-2 lg:hidden">
          <Button variant="ghost" size="sm" className="h-9 w-9 p-0" onClick={() => setSidebarOpen(true)} aria-label="메뉴 열기">
            <Menu size={20} />
          </Button>
          <div className="flex items-center gap-2">
            <ClippingLogo size={20} className="text-primary" />
            <span className="text-sm font-bold">Clipping</span>
          </div>
        </div>
        <MaintenanceBanner />
        <div className="flex-1 overflow-y-auto pb-[72px] lg:pb-0">
          <div key={pathname} className="animate-in fade-in-0 duration-150">
            <Outlet />
          </div>
        </div>
      </main>

      {/* 모바일 하단 네비게이션 — 데스크탑에서는 자동 숨김 */}
      <UserBottomNavigation />
    </div>
  );
}
