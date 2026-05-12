import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CompetitorFormModal } from "../CompetitorFormModal";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { Competitor } from "@/types/competitor";

// Mock competitorService
vi.mock("@/services/competitorService", () => ({
  competitorService: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn().mockResolvedValue({ id: "new-1" }),
    update: vi.fn().mockResolvedValue({ id: "c1" }),
    delete: vi.fn().mockResolvedValue(undefined),
    previewKeywords: vi.fn().mockResolvedValue({ items: [], message: "" }),
  },
}));

function makeCompetitor(overrides: Partial<Competitor> = {}): Competitor {
  return {
    id: "c1",
    name: "테스트 경쟁사",
    aliases: ["AI", "클라우드"],
    excludeKeywords: [],
    tier: "DIRECT",
    isActive: true,
    rssFeeds: [{ id: "r1", feedUrl: "https://example.com/rss", label: "예시" }],
    articleCount: 42,
    last24hCount: 5,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

function renderModal(props: Partial<React.ComponentProps<typeof CompetitorFormModal>> = {}) {
  return render(
    <CompetitorFormModal
      open={true}
      onClose={vi.fn()}
      competitor={null}
      {...props}
    />,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("CompetitorFormModal", () => {
  describe("생성 모드", () => {
    it("빈 필드와 '경쟁사 추가' 타이틀로 렌더링된다", () => {
      renderModal();

      expect(screen.getByText("경쟁사 추가")).toBeInTheDocument();
      const nameInput = screen.getByPlaceholderText("경쟁사 이름");
      expect(nameInput).toHaveValue("");
    });

    it("저장 버튼이 표시된다", () => {
      renderModal();

      expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
    });
  });

  describe("수정 모드", () => {
    it("기존 데이터가 채워지고 '경쟁사 수정' 타이틀로 렌더링된다", () => {
      const competitor = makeCompetitor();
      renderModal({ competitor });

      expect(screen.getByText("경쟁사 수정")).toBeInTheDocument();
      const nameInput = screen.getByPlaceholderText("경쟁사 이름");
      expect(nameInput).toHaveValue("테스트 경쟁사");
    });

    it("기존 키워드가 뱃지로 표시된다", () => {
      const competitor = makeCompetitor({ aliases: ["AI", "클라우드"] });
      renderModal({ competitor });

      expect(screen.getByText("AI")).toBeInTheDocument();
      expect(screen.getByText("클라우드")).toBeInTheDocument();
    });
  });

  describe("이름 유효성 검사", () => {
    it("빈 이름으로 제출하면 에러 메시지를 표시한다", async () => {
      renderModal();

      const submitButton = screen.getByRole("button", { name: "저장" });
      fireEvent.click(submitButton);

      await waitFor(() => {
        // 공용 adminInputSchemas.competitorName은 "경쟁사 이름을 입력하세요" 메시지를 사용한다.
        expect(screen.getByText("경쟁사 이름을 입력하세요")).toBeInTheDocument();
      });
    });
  });

  describe("별칭 제한", () => {
    it("5개 별칭이 채워지면 입력 필드가 사라진다", async () => {
      const competitor = makeCompetitor({
        aliases: ["k1", "k2", "k3", "k4", "k5"],
      });
      renderModal({ competitor });

      // 5개가 모두 표시되고
      expect(screen.getByText("5/5")).toBeInTheDocument();
      // 입력 필드가 없어야 한다
      expect(screen.queryByPlaceholderText("영문명, 모회사명 등")).not.toBeInTheDocument();
    });

    it("별칭을 Enter로 추가할 수 있다", async () => {
      const user = userEvent.setup();
      renderModal();

      const aliasInput = screen.getByPlaceholderText("영문명, 모회사명 등");
      await user.type(aliasInput, "테스트별칭{Enter}");

      await waitFor(() => {
        expect(screen.getByText("테스트별칭")).toBeInTheDocument();
        expect(screen.getByText("1/5")).toBeInTheDocument();
      });
    });
  });

  describe("RSS 피드", () => {
    it("RSS 추가 버튼으로 입력 행을 추가할 수 있다", async () => {
      const user = userEvent.setup();
      renderModal();

      const addButton = screen.getByRole("button", { name: /RSS 추가/ });
      await user.click(addButton);

      expect(screen.getByPlaceholderText("https://example.com/rss")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("라벨 (선택)")).toBeInTheDocument();
    });
  });

  describe("모달이 닫혀 있을 때", () => {
    it("open=false이면 아무것도 렌더링하지 않는다", () => {
      renderModal({ open: false });

      expect(screen.queryByText("경쟁사 추가")).not.toBeInTheDocument();
    });
  });
});
