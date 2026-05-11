// frontend/src/pages/source-quality/components/__tests__/SourceQualityTable.test.tsx
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { SourceQualityTable } from "../SourceQualityTable";
import type { SourceQualityRow } from "@/types/sourceQuality";

// Mock row helper — 기본값은 statusLabel="normal" + clickRate 10%
const row = (overrides: Partial<SourceQualityRow> = {}): SourceQualityRow => ({
  sourceId: "s1",
  sourceName: "Test Source",
  delivered: 100,
  uniqueUserClicks: 10,
  clickRatePct: 10,
  likes: 1,
  dislikes: 0,
  likeRatePct: 1,
  statusLabel: "normal",
  isActive: true,
  updatedAt: "2026-04-20T00:00:00Z",
  ...overrides,
});

// 헤더 컬럼의 버튼 텍스트 — 아이콘/표시 텍스트가 섞이므로 regex 로 매칭
const sortButton = (name: RegExp) =>
  screen.getByRole("button", { name });

describe("SourceQualityTable", () => {
  it("1. 기본 렌더 — activeAll 필터 + 클릭률 asc 정렬 (나쁜 순 위)", () => {
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", clickRatePct: 20 }),
      row({ sourceId: "s2", sourceName: "Beta", clickRatePct: 5 }),
      row({ sourceId: "s3", sourceName: "Gamma", clickRatePct: 12 }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    const dataRows = screen.getAllByTestId("source-row");
    expect(dataRows).toHaveLength(3);
    // 기본 sort: clickRate asc → Beta(5) → Gamma(12) → Alpha(20)
    expect(within(dataRows[0]).getByText("Beta")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Gamma")).toBeInTheDocument();
    expect(within(dataRows[2]).getByText("Alpha")).toBeInTheDocument();
  });

  it("2. 헤더 클릭 → 같은 컬럼 정렬 방향 토글 (asc → desc)", async () => {
    const user = userEvent.setup();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", clickRatePct: 20 }),
      row({ sourceId: "s2", sourceName: "Beta", clickRatePct: 5 }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    // 기본 = clickRate asc
    let dataRows = screen.getAllByTestId("source-row");
    expect(within(dataRows[0]).getByText("Beta")).toBeInTheDocument();

    // 클릭률 헤더 클릭 → desc 토글
    await user.click(sortButton(/클릭률/));

    dataRows = screen.getAllByTestId("source-row");
    expect(within(dataRows[0]).getByText("Alpha")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Beta")).toBeInTheDocument();
  });

  it("3. 다른 헤더 클릭 → 정렬 컬럼 변경 (asc 로 시작)", async () => {
    const user = userEvent.setup();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", delivered: 300 }),
      row({ sourceId: "s2", sourceName: "Beta", delivered: 100 }),
      row({ sourceId: "s3", sourceName: "Gamma", delivered: 200 }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    // 발송 헤더 클릭 → delivered asc
    await user.click(sortButton(/발송/));

    const dataRows = screen.getAllByTestId("source-row");
    expect(within(dataRows[0]).getByText("Beta")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Gamma")).toBeInTheDocument();
    expect(within(dataRows[2]).getByText("Alpha")).toBeInTheDocument();
  });

  it("4. 상태 필터 '검토 필요' 클릭 → review 만 렌더", async () => {
    const user = userEvent.setup();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", statusLabel: "normal" }),
      row({ sourceId: "s2", sourceName: "Beta", statusLabel: "review" }),
      row({ sourceId: "s3", sourceName: "Gamma", statusLabel: "review" }),
      row({ sourceId: "s4", sourceName: "Delta", statusLabel: "default" }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("radio", { name: "검토 필요" }));

    const dataRows = screen.getAllByTestId("source-row");
    expect(dataRows).toHaveLength(2);
    const names = dataRows.map((r) => within(r).getByText(/^(Alpha|Beta|Gamma|Delta)$/).textContent);
    expect(names).toEqual(expect.arrayContaining(["Beta", "Gamma"]));
  });

  it("5. 필터 + 정렬 동시 — 검토 필요 + 클릭률 asc 유지", async () => {
    const user = userEvent.setup();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", statusLabel: "normal", clickRatePct: 1 }),
      row({ sourceId: "s2", sourceName: "Beta", statusLabel: "review", clickRatePct: 8 }),
      row({ sourceId: "s3", sourceName: "Gamma", statusLabel: "review", clickRatePct: 2 }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("radio", { name: "검토 필요" }));

    const dataRows = screen.getAllByTestId("source-row");
    expect(dataRows).toHaveLength(2);
    // clickRate asc: Gamma(2) → Beta(8)
    expect(within(dataRows[0]).getByText("Gamma")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Beta")).toBeInTheDocument();
  });

  it("6. 편집 버튼 클릭 → onEdit(sourceId) 호출", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s-42", sourceName: "Target" }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={onEdit}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    const editBtn = screen.getByRole("button", { name: /Target 편집/ });
    await user.click(editBtn);

    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onEdit).toHaveBeenCalledWith("s-42");
  });

  it("7. 수집 일시중지 버튼 → onDeactivate({ sourceId, sourceName, expectedUpdatedAt })", async () => {
    const user = userEvent.setup();
    const onDeactivate = vi.fn();
    const rows: SourceQualityRow[] = [
      row({
        sourceId: "s-77",
        sourceName: "PauseMe",
        updatedAt: "2026-04-19T12:00:00Z",
      }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={onDeactivate}
        onActivate={vi.fn()}
      />,
    );

    const pauseBtn = screen.getByRole("button", { name: /PauseMe 수집 일시중지/ });
    await user.click(pauseBtn);

    expect(onDeactivate).toHaveBeenCalledTimes(1);
    expect(onDeactivate).toHaveBeenCalledWith({
      sourceId: "s-77",
      sourceName: "PauseMe",
      expectedUpdatedAt: "2026-04-19T12:00:00Z",
    });
  });

  it("8. sourceId=null (수동 URL) 행 → 모든 액션 버튼 disabled + tooltip", () => {
    const rows: SourceQualityRow[] = [
      row({ sourceId: null, sourceName: "Manual URL Bundle", updatedAt: "1970-01-01T00:00:00Z" }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    const editBtn = screen.getByRole("button", { name: /Manual URL Bundle 편집/ });
    const pauseBtn = screen.getByRole("button", { name: /Manual URL Bundle 수집 일시중지/ });

    expect(editBtn).toBeDisabled();
    expect(pauseBtn).toBeDisabled();
    expect(editBtn).toHaveAttribute("title", "수동 URL 은 편집 불가");
    expect(pauseBtn).toHaveAttribute("title", "수동 URL 은 편집 불가");
  });

  it("9. null clickRate 행은 정렬 시 맨 뒤 (asc/desc 모두)", async () => {
    const user = userEvent.setup();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Alpha", clickRatePct: 20 }),
      row({ sourceId: "s2", sourceName: "Null1", clickRatePct: null, statusLabel: "default" }),
      row({ sourceId: "s3", sourceName: "Beta", clickRatePct: 5 }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    // 기본 = clickRate asc → Beta(5), Alpha(20), Null1(null)
    let dataRows = screen.getAllByTestId("source-row");
    expect(within(dataRows[0]).getByText("Beta")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Alpha")).toBeInTheDocument();
    expect(within(dataRows[2]).getByText("Null1")).toBeInTheDocument();

    // desc 로 토글 → Alpha(20), Beta(5), Null1(null)  — null 은 여전히 맨 뒤
    await user.click(sortButton(/클릭률/));
    dataRows = screen.getAllByTestId("source-row");
    expect(within(dataRows[0]).getByText("Alpha")).toBeInTheDocument();
    expect(within(dataRows[1]).getByText("Beta")).toBeInTheDocument();
    expect(within(dataRows[2]).getByText("Null1")).toBeInTheDocument();
  });

  it("10. statusFilter='inactive' → 비활성 소스만 렌더 + 버튼 라벨 '활성화'", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    const rows: SourceQualityRow[] = [
      row({ sourceId: "s1", sourceName: "Active1", isActive: true }),
      row({
        sourceId: "s2",
        sourceName: "Inactive1",
        isActive: false,
        updatedAt: "2026-04-15T10:00:00Z",
      }),
      row({ sourceId: "s3", sourceName: "Active2", isActive: true }),
    ];
    render(
      <SourceQualityTable
        rows={rows}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={onActivate}
      />,
    );

    await user.click(screen.getByRole("radio", { name: "비활성" }));

    const dataRows = screen.getAllByTestId("source-row");
    expect(dataRows).toHaveLength(1);
    expect(within(dataRows[0]).getByText("Inactive1")).toBeInTheDocument();

    // "수집 일시중지" 버튼은 더 이상 없어야 함
    expect(
      screen.queryByRole("button", { name: /Inactive1 수집 일시중지/ }),
    ).not.toBeInTheDocument();

    // "활성화" 버튼이 있어야 함
    const activateBtn = screen.getByRole("button", { name: /Inactive1 활성화/ });
    await user.click(activateBtn);

    expect(onActivate).toHaveBeenCalledTimes(1);
    expect(onActivate).toHaveBeenCalledWith({
      sourceId: "s2",
      sourceName: "Inactive1",
      expectedUpdatedAt: "2026-04-15T10:00:00Z",
    });
  });

  it("상태 필터 chips 는 role='radiogroup' 컨테이너 안에 role='radio' 로 구성", () => {
    render(
      <SourceQualityTable
        rows={[]}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    const group = screen.getByRole("radiogroup");
    expect(group).toBeInTheDocument();

    const chips = within(group).getAllByRole("radio");
    // 5 개: 전체(활성)/정상/검토 필요/신호 부족/비활성
    expect(chips).toHaveLength(5);
    // 기본 선택은 "전체 (활성)"
    const selected = chips.find((c) => c.getAttribute("aria-checked") === "true");
    expect(selected?.textContent).toMatch(/전체/);
  });

  it("정렬 가능 헤더는 aria-sort 속성을 가진다", () => {
    render(
      <SourceQualityTable
        rows={[]}
        onEdit={vi.fn()}
        onDeactivate={vi.fn()}
        onActivate={vi.fn()}
      />,
    );

    // 기본 정렬 = clickRate asc → 해당 헤더는 aria-sort=ascending, 나머지는 none
    const clickRateHeader = screen.getByRole("columnheader", { name: /클릭률/ });
    expect(clickRateHeader).toHaveAttribute("aria-sort", "ascending");

    const deliveredHeader = screen.getByRole("columnheader", { name: /발송/ });
    expect(deliveredHeader).toHaveAttribute("aria-sort", "none");
  });
});
