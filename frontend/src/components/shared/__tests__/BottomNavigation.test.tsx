import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BottomNavigation, __BOTTOM_NAV_ITEMS_FOR_TEST } from "../BottomNavigation";

function renderAt(pathname: string) {
  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <BottomNavigation />
    </MemoryRouter>,
  );
}

describe("BottomNavigation (admin)", () => {
  it("renders exactly 5 navigation items", () => {
    renderAt("/admin");
    const nav = screen.getByRole("navigation", { name: "모바일 하단 내비게이션" });
    const links = nav.querySelectorAll("a");
    expect(links.length).toBe(5);
    expect(__BOTTOM_NAV_ITEMS_FOR_TEST.length).toBe(5);
  });

  it("marks home as current when pathname is /admin", () => {
    renderAt("/admin");
    const homeLink = screen.getByRole("link", { name: /홈/ });
    expect(homeLink).toHaveAttribute("aria-current", "page");
  });

  it("does NOT mark home as current when pathname is /admin/sources", () => {
    renderAt("/admin/sources");
    const homeLink = screen.getByRole("link", { name: /홈/ });
    expect(homeLink).not.toHaveAttribute("aria-current");
  });

  it("marks 콘텐츠 tab as current for prefix match /admin/sources", () => {
    renderAt("/admin/sources");
    const contentLink = screen.getByRole("link", { name: /콘텐츠/ });
    expect(contentLink).toHaveAttribute("aria-current", "page");
  });

  it("marks 콘텐츠 tab as current for sub-route /admin/sources/123", () => {
    renderAt("/admin/sources/abc-123");
    const contentLink = screen.getByRole("link", { name: /콘텐츠/ });
    expect(contentLink).toHaveAttribute("aria-current", "page");
  });

  it("marks 시스템 tab as current for /admin/system-status", () => {
    renderAt("/admin/system-status");
    const systemLink = screen.getByRole("link", { name: /시스템/ });
    expect(systemLink).toHaveAttribute("aria-current", "page");
  });

  it("does NOT match prefix that only shares characters but not path boundary", () => {
    // /admin/sourcesx should not match /admin/sources
    renderAt("/admin/sourcesx");
    const contentLink = screen.getByRole("link", { name: /콘텐츠/ });
    expect(contentLink).not.toHaveAttribute("aria-current");
  });

  it("uses lg:hidden class so it shows through tablet + hides on desktop", () => {
    // lg 브레이크포인트(1024+) 이상에서만 숨긴다. 768~1023px (아이패드 세로) 에서는
    // 사이드바가 이미 접혀 있으므로 이 구간에도 바텀 네비가 필요하다 (PR #422 교훈).
    renderAt("/admin");
    const nav = screen.getByRole("navigation", { name: "모바일 하단 내비게이션" });
    expect(nav.className).toMatch(/lg:hidden/);
    expect(nav.className).not.toMatch(/md:hidden/);
  });
});
