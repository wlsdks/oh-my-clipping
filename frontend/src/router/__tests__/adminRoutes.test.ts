import { describe, it, expect } from "vitest";
import { adminRoutes } from "../adminRoutes";
import type { AdminRouteId, AdminRouteGroup, AdminRouteItem } from "../adminRoutes";

/**
 * adminRoutes.ts — 사이드바 네비게이션의 단일 출처(single source of truth).
 *
 * 여기서 검증하는 것:
 *   - 모든 AdminRouteId enum 값이 adminRoutes 배열에 한 번씩 존재
 *   - href 가 유일 (중복 경로 없음)
 *   - 각 라우트가 5개 그룹(홈/콘텐츠/운영/분석/시스템) 중 하나에 속함
 *   - badgeVariant 가 "default" | "destructive" 만 사용
 *   - 필수 필드(id, group, label, href, summary, icon) 누락 금지
 */

const EXPECTED_GROUPS: AdminRouteGroup[] = ["home", "content", "ops", "analysis", "system"];

const EXPECTED_IDS: AdminRouteId[] = [
  "dashboard",
  "sources", "personas", "competitors", "organizations",
  "pipeline", "reviewQueue", "autoExcludeAudit", "delivery", "userAccounts", "subscriptions",
  "analytics", "cost", "engagement", "sourceQuality",
  "systemStatus", "auditLog", "runtime", "departments",
  "digestDiff", "dbHealth",
];

describe("adminRoutes — 배열 구조 무결성", () => {
  it("adminRoutes 배열은 비어있지 않다", () => {
    expect(adminRoutes.length).toBeGreaterThan(0);
  });

  it("모든 라우트 항목에 필수 필드(id, group, label, href, summary, icon) 가 존재한다", () => {
    for (const route of adminRoutes) {
      expect(route.id).toBeTruthy();
      expect(route.group).toBeTruthy();
      expect(route.label).toBeTruthy();
      expect(route.href).toBeTruthy();
      expect(route.summary).toBeTruthy();
      expect(route.icon).toBeTruthy();
    }
  });

  it("모든 라우트의 href 는 '/admin' 으로 시작한다", () => {
    for (const route of adminRoutes) {
      expect(route.href.startsWith("/admin")).toBe(true);
    }
  });
});

describe("adminRoutes — ID 유일성 및 enum 커버리지", () => {
  it("AdminRouteId enum 의 모든 값이 adminRoutes 에 정확히 한 번씩 존재한다", () => {
    const idsInRoutes = adminRoutes.map((r) => r.id);
    for (const expectedId of EXPECTED_IDS) {
      const count = idsInRoutes.filter((id) => id === expectedId).length;
      expect(count, `라우트 id '${expectedId}' 는 정확히 1번 나와야 한다`).toBe(1);
    }
  });

  it("adminRoutes 에는 EXPECTED_IDS 외에 알 수 없는 id 가 없다", () => {
    for (const route of adminRoutes) {
      expect(EXPECTED_IDS).toContain(route.id);
    }
  });

  it("총 라우트 개수는 EXPECTED_IDS 와 일치한다", () => {
    expect(adminRoutes.length).toBe(EXPECTED_IDS.length);
  });
});

describe("adminRoutes — href(경로) 유일성", () => {
  it("href 는 중복되지 않는다 — 각 경로는 한 라우트에만 매핑", () => {
    const hrefs = adminRoutes.map((r) => r.href);
    const unique = new Set(hrefs);
    expect(unique.size).toBe(hrefs.length);
  });
});

describe("adminRoutes — 그룹 구성", () => {
  it("모든 라우트는 5개 예상 그룹(home/content/ops/analysis/system) 중 하나에 속한다", () => {
    for (const route of adminRoutes) {
      expect(EXPECTED_GROUPS).toContain(route.group);
    }
  });

  it("5개 그룹 모두에 최소 1개 이상의 라우트가 있다", () => {
    const groups = new Set(adminRoutes.map((r) => r.group));
    for (const expectedGroup of EXPECTED_GROUPS) {
      expect(groups.has(expectedGroup), `그룹 '${expectedGroup}' 에 최소 1개 라우트가 있어야 한다`).toBe(true);
    }
  });

  it("home 그룹은 dashboard 1개만 포함한다", () => {
    const homeRoutes = adminRoutes.filter((r) => r.group === "home");
    expect(homeRoutes).toHaveLength(1);
    expect(homeRoutes[0].id).toBe("dashboard");
  });
});

describe("adminRoutes — badgeVariant 와 badgeQueryEnabled", () => {
  it("badgeVariant 는 'default' 또는 'destructive' 값만 허용한다", () => {
    for (const route of adminRoutes) {
      if (route.badgeVariant !== undefined) {
        expect(["default", "destructive"]).toContain(route.badgeVariant);
      }
    }
  });

  it("badgeQueryEnabled=true 인 라우트가 최소 1개 이상 존재한다 (Sidebar 뱃지 기능)", () => {
    const badgedRoutes = adminRoutes.filter((r) => r.badgeQueryEnabled === true);
    expect(badgedRoutes.length).toBeGreaterThan(0);
  });

  it("운영 그룹(ops) 의 핵심 라우트 — userAccounts, subscriptions, reviewQueue, delivery, pipeline — 에 뱃지 쿼리가 켜져 있다", () => {
    const opsIds = ["userAccounts", "subscriptions", "reviewQueue", "delivery", "pipeline"];
    for (const id of opsIds) {
      const route = adminRoutes.find((r) => r.id === id);
      // 라우트 존재 여부와 속성을 함께 단언한다 (null guard 가 아닌 실제 동작 검증)
      expect(route, `ops 라우트 '${id}' 가 존재해야 한다`).toMatchObject({
        id,
        badgeQueryEnabled: true,
      });
    }
  });

  it("delivery / pipeline 라우트는 실패 카운트를 위해 destructive 뱃지 변형을 사용한다", () => {
    const deliveryRoute = adminRoutes.find((r) => r.id === "delivery");
    const pipelineRoute = adminRoutes.find((r) => r.id === "pipeline");
    expect(deliveryRoute?.badgeVariant).toBe("destructive");
    expect(pipelineRoute?.badgeVariant).toBe("destructive");
  });
});

describe("adminRoutes — href 패턴 정합성", () => {
  const hrefById: Record<AdminRouteId, string> = {
    dashboard: "/admin",
    sources: "/admin/sources",
    personas: "/admin/personas",
    competitors: "/admin/competitors",
    organizations: "/admin/organizations",
    pipeline: "/admin/pipeline",
    reviewQueue: "/admin/review-queue",
    autoExcludeAudit: "/admin/review-queue/auto-exclude-audit",
    delivery: "/admin/delivery",
    userAccounts: "/admin/user-accounts",
    subscriptions: "/admin/subscriptions",
    analytics: "/admin/analytics",
    cost: "/admin/cost",
    engagement: "/admin/engagement",
    sourceQuality: "/admin/sources/quality",
    systemStatus: "/admin/system-status",
    auditLog: "/admin/audit-log",
    runtime: "/admin/runtime",
    departments: "/admin/departments",
    digestDiff: "/admin/digest-diff",
    dbHealth: "/admin/db-health",
  };

  it("각 라우트 id 는 예상 href 와 매핑된다 (라우팅 회귀 방지)", () => {
    for (const route of adminRoutes as AdminRouteItem[]) {
      const expected = hrefById[route.id];
      expect(route.href, `라우트 '${route.id}' 의 href 가 예상과 다르다`).toBe(expected);
    }
  });
});

describe("adminRoutes — 라벨 유일성", () => {
  it("label 값은 중복되지 않는다 — 사이드바에 같은 이름이 두 번 나타나지 않는다", () => {
    const labels = adminRoutes.map((r) => r.label);
    const unique = new Set(labels);
    expect(unique.size).toBe(labels.length);
  });
});
