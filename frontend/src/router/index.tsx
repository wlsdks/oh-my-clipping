import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import { ProtectedRoute } from "./ProtectedRoute";
import { AdminLayout } from "@/components/shared/AdminLayout";
import { UserLayout } from "@/components/shared/UserLayout";

// 로그인/가입 페이지도 route 단위 lazy split — 인증 후 사용자는 이 chunk 를 내려받지 않는다.
const LoginPage = lazy(() =>
  import("@/pages/auth/login/LoginPage").then((m) => ({ default: m.LoginPage }))
);
const SignupPage = lazy(() =>
  import("@/pages/auth/signup/SignupPage").then((m) => ({ default: m.SignupPage }))
);
const SourcesPage = lazy(() =>
  import("@/pages/sources/SourcesPage").then((m) => ({ default: m.SourcesPage }))
);
const AdminDashboardPage = lazy(() =>
  import("@/pages/dashboard/AdminDashboardPage").then((m) => ({ default: m.AdminDashboardPage }))
);
const AdminUserAccountsPage = lazy(() =>
  import("@/pages/user-accounts/AdminUserAccountsPage").then((m) => ({ default: m.AdminUserAccountsPage }))
);
const RuntimePage = lazy(() =>
  import("@/pages/runtime/RuntimePage").then((m) => ({ default: m.RuntimePage }))
);

// ── Lazy-loaded user pages (code-split to reduce initial bundle) ──
const UserHomePage = lazy(() =>
  import("@/pages/user-clipping/UserHomePage").then((m) => ({ default: m.UserHomePage }))
);
const UserManagePage = lazy(() =>
  import("@/pages/user-clipping/UserManagePage").then((m) => ({ default: m.UserManagePage }))
);
const UserStatusPage = lazy(() =>
  import("@/pages/user-clipping/UserStatusPage").then((m) => ({ default: m.UserStatusPage }))
);
const UserNewsReportPage = lazy(() =>
  import("@/pages/user-clipping/UserNewsReportPage").then((m) => ({ default: m.UserNewsReportPage }))
);
const UserArticlesPage = lazy(() =>
  import("@/pages/user-clipping/UserArticlesPage").then((m) => ({ default: m.UserArticlesPage }))
);
const CategoryBrowsePage = lazy(() =>
  import("@/pages/user-clipping/CategoryBrowsePage").then((m) => ({ default: m.CategoryBrowsePage }))
);

// ── Lazy-loaded route pages (code-split at route level) ──
const SubscriptionManagementPage = lazy(() =>
  import("@/pages/subscriptions/SubscriptionManagementPage").then((m) => ({ default: m.SubscriptionManagementPage }))
);
const PersonasPage = lazy(() =>
  import("@/pages/personas/PersonasPage").then((m) => ({ default: m.PersonasPage }))
);
const ReviewQueuePage = lazy(() =>
  import("@/pages/review-queue/ReviewQueuePage").then((m) => ({ default: m.ReviewQueuePage }))
);
const AutoExcludeAuditPage = lazy(() =>
  import("@/pages/review-queue/auto-exclude-audit/AutoExcludeAuditPage").then((m) => ({ default: m.AutoExcludeAuditPage }))
);
const PipelinePage = lazy(() =>
  import("@/pages/pipeline/PipelinePage").then((m) => ({ default: m.PipelinePage }))
);
const DeliveryPage = lazy(() =>
  import("@/pages/delivery/DeliveryPage").then((m) => ({ default: m.DeliveryPage }))
);
const AnalyticsPage = lazy(() =>
  import("@/pages/analytics/AnalyticsPage").then((m) => ({ default: m.AnalyticsPage }))
);
const CostPage = lazy(() =>
  import("@/pages/cost/CostPage").then((m) => ({ default: m.CostPage }))
);
const EngagementPage = lazy(() =>
  import("@/pages/engagement/EngagementPage").then((m) => ({ default: m.EngagementPage }))
);
const SourceQualityPage = lazy(() =>
  import("@/pages/source-quality/SourceQualityPage").then((m) => ({ default: m.SourceQualityPage }))
);
const AuditLogPage = lazy(() =>
  import("@/pages/audit-log/AuditLogPage").then((m) => ({ default: m.AuditLogPage }))
);
const SystemStatusPage = lazy(() =>
  import("@/pages/system-status/SystemStatusPage").then((m) => ({ default: m.SystemStatusPage }))
);
const CompetitorsPage = lazy(() =>
  import("@/pages/competitors/CompetitorsPage").then((m) => ({ default: m.CompetitorsPage }))
);
const OrganizationsPage = lazy(() =>
  import("@/pages/organizations/OrganizationsPage").then((m) => ({ default: m.OrganizationsPage }))
);
const DepartmentsPage = lazy(() =>
  import("@/pages/system/DepartmentsPage").then((m) => ({ default: m.DepartmentsPage }))
);
const DigestDiffPage = lazy(() =>
  import("@/pages/digest-diff/DigestDiffPage").then((m) => ({ default: m.DigestDiffPage }))
);
const DbHealthPage = lazy(() =>
  import("@/pages/db-health/DbHealthPage").then((m) => ({ default: m.DbHealthPage }))
);

const lazyFallback = <div className="p-8 text-center text-sm text-muted-foreground">불러오는 중...</div>;

export const router = createBrowserRouter(
  [
    {
      path: "/admin",
      element: <ProtectedRoute role="ADMIN" />,
      children: [
        {
          element: <AdminLayout />,
          children: [
            { index: true, element: <Suspense fallback={lazyFallback}><AdminDashboardPage /></Suspense>, handle: { title: "홈" } },
            { path: "sources", element: <Suspense fallback={lazyFallback}><SourcesPage /></Suspense>, handle: { title: "뉴스 소스" } },
            { path: "subscriptions", element: <Suspense fallback={lazyFallback}><SubscriptionManagementPage /></Suspense>, handle: { title: "구독 관리" } },
            { path: "categories", element: <Navigate to="/admin/subscriptions?filter=active" replace /> },
            { path: "personas", element: <Suspense fallback={lazyFallback}><PersonasPage /></Suspense>, handle: { title: "요약 스타일" } },
            { path: "competitors", element: <Suspense fallback={lazyFallback}><CompetitorsPage /></Suspense>, handle: { title: "경쟁사 관리" } },
            { path: "organizations", element: <Suspense fallback={lazyFallback}><OrganizationsPage /></Suspense>, handle: { title: "관심 기업" } },
            { path: "rules", element: <Navigate to="/admin/subscriptions" replace /> },
            { path: "review-queue", element: <Suspense fallback={lazyFallback}><ReviewQueuePage /></Suspense>, handle: { title: "뉴스 검토" } },
            { path: "review-queue/auto-exclude-audit", element: <Suspense fallback={lazyFallback}><AutoExcludeAuditPage /></Suspense>, handle: { title: "자동 제외 감사" } },
            { path: "pipeline", element: <Suspense fallback={lazyFallback}><PipelinePage /></Suspense>, handle: { title: "파이프라인" } },
            { path: "operations", element: <Navigate to="/admin/pipeline" replace /> },
            { path: "insights", element: <Navigate to="/admin/analytics?tab=quality" replace /> },
            { path: "costs", element: <Navigate to="/admin/cost" replace /> },
            { path: "news-report", element: <Navigate to="/admin/analytics?tab=insight" replace /> },
            { path: "visual-cards", element: <Navigate to="/admin/analytics?tab=insight" replace /> },
            { path: "user-accounts", element: <Suspense fallback={lazyFallback}><AdminUserAccountsPage /></Suspense>, handle: { title: "회원 관리" } },
            { path: "user-requests", element: <Navigate to="/admin/subscriptions" replace /> },
            { path: "runtime", element: <Suspense fallback={lazyFallback}><RuntimePage /></Suspense>, handle: { title: "시스템 설정" } },
            { path: "runtime/pipeline", element: <Navigate to="/admin/runtime" replace /> },
            { path: "trends", element: <Navigate to="/admin/analytics?tab=insight" replace /> },
            { path: "delivery", element: <Suspense fallback={lazyFallback}><DeliveryPage /></Suspense>, handle: { title: "발송 관리" } },
            { path: "analytics", element: <Suspense fallback={lazyFallback}><AnalyticsPage /></Suspense>, handle: { title: "통합 분석" } },
            { path: "cost", element: <Suspense fallback={lazyFallback}><CostPage /></Suspense>, handle: { title: "비용 관리" } },
            { path: "engagement", element: <Suspense fallback={lazyFallback}><EngagementPage /></Suspense>, handle: { title: "사용자 반응" } },
            { path: "sources/quality", element: <Suspense fallback={lazyFallback}><SourceQualityPage /></Suspense>, handle: { title: "RSS 소스 품질" } },
            { path: "content-levers", element: <Navigate to="/admin/sources/quality" replace /> },
            { path: "audit-log", element: <Suspense fallback={lazyFallback}><AuditLogPage /></Suspense>, handle: { title: "감사 로그" } },
            { path: "system-status", element: <Suspense fallback={lazyFallback}><SystemStatusPage /></Suspense>, handle: { title: "시스템 상태" } },
            { path: "departments", element: <Suspense fallback={lazyFallback}><DepartmentsPage /></Suspense>, handle: { title: "사내 부서·팀" } },
            { path: "digest-diff", element: <Suspense fallback={lazyFallback}><DigestDiffPage /></Suspense>, handle: { title: "발송 모드 diff" } },
            { path: "db-health", element: <Suspense fallback={lazyFallback}><DbHealthPage /></Suspense>, handle: { title: "DB 상태" } }
          ]
        }
      ]
    },
    {
      path: "/user",
      element: <ProtectedRoute role="USER" />,
      children: [
        {
          element: <UserLayout />,
          children: [
            { index: true, element: <Suspense fallback={lazyFallback}><UserHomePage /></Suspense>, handle: { title: "홈" } },
            { path: "manage", element: <Suspense fallback={lazyFallback}><UserManagePage /></Suspense>, handle: { title: "내 구독 관리" } },
            { path: "browse", element: <Suspense fallback={lazyFallback}><CategoryBrowsePage /></Suspense>, handle: { title: "구독 가능한 주제" } },
            { path: "request", element: <Navigate to="/user/manage" replace /> },
            { path: "status", element: <Navigate to="/user/history" replace /> },
            { path: "history", element: <Suspense fallback={lazyFallback}><UserStatusPage /></Suspense>, handle: { title: "신청 내역" } },
            { path: "stats", element: <Navigate to="/user/history" replace /> },
            { path: "news-report", element: <Suspense fallback={lazyFallback}><UserNewsReportPage /></Suspense>, handle: { title: "뉴스 리포트" } },
            { path: "articles", element: <Suspense fallback={lazyFallback}><UserArticlesPage /></Suspense>, handle: { title: "내 기사 목록" } }
          ]
        }
      ]
    },
    { path: "/login", element: <Suspense fallback={lazyFallback}><LoginPage /></Suspense> },
    { path: "/signup", element: <Suspense fallback={lazyFallback}><SignupPage /></Suspense> },
    { path: "*", element: <Navigate to="/login" replace /> }
  ],
  {
    basename: "/"
  }
);
