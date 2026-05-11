import type { LucideIcon } from "lucide-react";
import {
  LayoutDashboard,
  Rss,
  Sparkles,
  Crosshair,
  ClipboardCheck,
  ClipboardList,
  ShieldOff,
  Workflow,
  BarChart2,
  Send,
  UserCheck,
  FileText,
  Activity,
  Settings,
  CreditCard,
  ThumbsUp,
  Gauge,
  Users,
  Building2,
  GitCompareArrows,
  Database,
} from "lucide-react";

export type AdminRouteId =
  | "dashboard"
  | "sources" | "personas" | "competitors" | "organizations"
  | "pipeline" | "reviewQueue" | "autoExcludeAudit" | "delivery" | "userAccounts" | "subscriptions"
  | "analytics" | "cost" | "engagement" | "sourceQuality"
  | "systemStatus" | "auditLog" | "runtime" | "departments"
  | "digestDiff" | "dbHealth";

export type AdminRouteGroup = "home" | "content" | "ops" | "analysis" | "system";

export interface AdminRouteItem {
  id: AdminRouteId;
  group: AdminRouteGroup;
  label: string;
  href: string;
  summary: string;
  icon: LucideIcon;
  badgeQueryEnabled?: boolean;
  badgeVariant?: "default" | "destructive";
}

export const adminRoutes: AdminRouteItem[] = [
  {
    id: "dashboard",
    group: "home",
    label: "홈",
    href: "/admin",
    summary: "한눈에 보는 현황",
    icon: LayoutDashboard
  },
  {
    id: "sources",
    group: "content",
    label: "뉴스 소스",
    href: "/admin/sources",
    summary: "구독 중인 뉴스 소스 관리",
    icon: Rss,
    badgeQueryEnabled: true,
    badgeVariant: "default"
  },
  {
    id: "personas",
    group: "content",
    label: "요약스타일",
    href: "/admin/personas",
    summary: "AI 요약 말투 설정",
    icon: Sparkles
  },
  {
    id: "competitors",
    group: "content",
    label: "경쟁사 관리",
    href: "/admin/competitors",
    summary: "경쟁사 뉴스 수집 관리",
    icon: Crosshair,
  },
  {
    id: "organizations",
    group: "content",
    label: "관심 기업",
    href: "/admin/organizations",
    summary: "뉴스 추적 대상 경쟁사/고객사/파트너 기업 마스터",
    icon: Building2,
  },
  {
    id: "userAccounts",
    group: "ops",
    label: "회원관리",
    href: "/admin/user-accounts",
    summary: "가입 요청 관리",
    icon: UserCheck,
    badgeQueryEnabled: true
  },
  {
    id: "subscriptions",
    group: "ops",
    label: "구독 관리",
    href: "/admin/subscriptions",
    summary: "구독 심사 + 운영 통합 관리",
    icon: ClipboardList,
    badgeQueryEnabled: true,
    badgeVariant: "default"
  },
  {
    id: "reviewQueue",
    group: "ops",
    label: "뉴스 검토",
    href: "/admin/review-queue",
    summary: "분류 결과 확인",
    icon: ClipboardCheck,
    badgeQueryEnabled: true
  },
  {
    id: "autoExcludeAudit",
    group: "ops",
    label: "자동 제외 감사",
    href: "/admin/review-queue/auto-exclude-audit",
    summary: "룰 엔진이 자동으로 제외한 기사 검토 + 복구",
    icon: ShieldOff
  },
  {
    id: "delivery",
    group: "ops",
    label: "발송 관리",
    href: "/admin/delivery",
    summary: "발송 이력과 실패 재발송",
    icon: Send,
    badgeQueryEnabled: true,
    badgeVariant: "destructive"
  },
  {
    id: "pipeline",
    group: "ops",
    label: "파이프라인",
    href: "/admin/pipeline",
    summary: "파이프라인 실행과 모니터링",
    icon: Workflow,
    badgeQueryEnabled: true,
    badgeVariant: "destructive"
  },
  {
    id: "analytics",
    group: "analysis",
    label: "통합 분석",
    href: "/admin/analytics",
    summary: "서비스 성과와 품질을 한눈에 분석",
    icon: BarChart2
  },
  {
    id: "cost",
    group: "analysis",
    label: "비용 관리",
    href: "/admin/cost",
    summary: "LLM 비용 현황과 예산 사용률",
    icon: CreditCard
  },
  {
    id: "engagement",
    group: "analysis",
    label: "사용자 반응",
    href: "/admin/engagement",
    summary: "클릭·피드백·전환 지표",
    icon: ThumbsUp
  },
  {
    id: "sourceQuality",
    group: "content",
    label: "RSS 소스 품질",
    href: "/admin/sources/quality",
    summary: "RSS 소스의 클릭률·반응 품질 지표",
    icon: Gauge
  },
  {
    id: "systemStatus",
    group: "system",
    label: "시스템 상태",
    href: "/admin/system-status",
    summary: "서버·DB·Slack 상태",
    icon: Activity
  },
  {
    id: "auditLog",
    group: "system",
    label: "감사 로그",
    href: "/admin/audit-log",
    summary: "관리자 활동 이력",
    icon: FileText
  },
  {
    id: "runtime",
    group: "system",
    label: "시스템 설정",
    href: "/admin/runtime",
    summary: "Slack 연결과 기본 수집 규칙",
    icon: Settings
  },
  {
    id: "departments",
    group: "system",
    label: "사내 부서·팀",
    href: "/admin/departments",
    summary: "회원가입 드롭다운 옵션 관리",
    icon: Users
  },
  {
    id: "digestDiff",
    group: "system",
    label: "발송 모드 diff",
    href: "/admin/digest-diff",
    summary: "Shadow 모드 비교 기록",
    icon: GitCompareArrows
  },
  {
    id: "dbHealth",
    group: "system",
    label: "DB 상태",
    href: "/admin/db-health",
    summary: "DB 용량·테이블 크기·보관 정리 예상",
    icon: Database
  }
];
