import { useEffect, useRef, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Menu } from "lucide-react";
import { Sidebar } from "./Sidebar";
import { BottomNavigation } from "./BottomNavigation";
import { ClippingLogo } from "./ClippingLogo";
import { MaintenanceBanner } from "./MaintenanceBanner";
import { TokenHealthBanner } from "./TokenHealthBanner";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useRouteChangeFocus } from "@/hooks/useRouteChangeFocus";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { tracker } from "@/shared/lib/tracker";

export function AdminLayout() {
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
        <Sidebar />
      </div>

      {/* Mobile sidebar (Sheet drawer) */}
      <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="sr-only">내비게이션 메뉴</SheetTitle>
          <Sidebar onNavigate={() => setSidebarOpen(false)} />
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
        <TokenHealthBanner />
        <div className="flex-1 overflow-y-auto pb-[72px] lg:pb-0">
          <div key={pathname} className="animate-in fade-in-0 duration-150">
            <Outlet />
          </div>
        </div>
      </main>

      {/* 모바일 하단 네비게이션 — 데스크탑에서는 자동 숨김 */}
      <BottomNavigation />
    </div>
  );
}
