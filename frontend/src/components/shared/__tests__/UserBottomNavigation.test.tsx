import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import {
  UserBottomNavigation,
  __USER_BOTTOM_NAV_ITEMS_FOR_TEST,
} from "../UserBottomNavigation";

function renderAt(pathname: string) {
  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <UserBottomNavigation />
    </MemoryRouter>,
  );
}

describe("UserBottomNavigation", () => {
  it("renders exactly 5 navigation items", () => {
    renderAt("/user");
    const nav = screen.getByRole("navigation", { name: "모바일 하단 내비게이션" });
    const links = nav.querySelectorAll("a");
    expect(links.length).toBe(5);
    expect(__USER_BOTTOM_NAV_ITEMS_FOR_TEST.length).toBe(5);
  });

  it("marks home as current when pathname is /user", () => {
    renderAt("/user");
    const homeLink = screen.getByRole("link", { name: /홈/ });
    expect(homeLink).toHaveAttribute("aria-current", "page");
  });

  it("does NOT mark home when on /user/manage", () => {
    renderAt("/user/manage");
    const homeLink = screen.getByRole("link", { name: /홈/ });
    expect(homeLink).not.toHaveAttribute("aria-current");
  });

  it("marks 구독 tab for /user/manage prefix", () => {
    renderAt("/user/manage");
    const subLink = screen.getByRole("link", { name: /구독/ });
    expect(subLink).toHaveAttribute("aria-current", "page");
  });

  it("matches sub-paths: 리포트 tab for /user/news-report/today", () => {
    renderAt("/user/news-report/today");
    const reportLink = screen.getByRole("link", { name: /리포트/ });
    expect(reportLink).toHaveAttribute("aria-current", "page");
  });

  it("does NOT match partial segments without path boundary", () => {
    renderAt("/user/managementx");
    const subLink = screen.getByRole("link", { name: /구독/ });
    expect(subLink).not.toHaveAttribute("aria-current");
  });

  it("uses lg:hidden class so it shows through tablet + hides on desktop", () => {
    // lg 브레이크포인트(1024+) 이상에서만 숨긴다. 768~1023px (아이패드 세로) 에서도
    // 바텀 네비가 보여야 한다 — 해당 구간에선 사이드바가 접혀 있기 때문 (PR #422 교훈).
    renderAt("/user");
    const nav = screen.getByRole("navigation", { name: "모바일 하단 내비게이션" });
    expect(nav.className).toMatch(/lg:hidden/);
    expect(nav.className).not.toMatch(/md:hidden/);
  });
});
