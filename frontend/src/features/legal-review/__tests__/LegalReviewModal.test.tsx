import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LegalReviewModal } from "../LegalReviewModal";

const mockRequest = {
  id: "req-1",
  sourceName: "TechCrunch",
  sourceUrl: "https://techcrunch.com/feed",
  requesterEmail: "user@example.com",
  createdAt: "2026-04-09T09:00:00Z",
};

describe("LegalReviewModal (approve-request mode)", () => {
  it("요청 정보를 readonly로 표시한다", () => {
    render(
      <LegalReviewModal
        mode="approve-request"
        open={true}
        onClose={() => {}}
        request={mockRequest}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByText(/TechCrunch/)).toBeInTheDocument();
    expect(screen.getByText(/techcrunch\.com\/feed/)).toBeInTheDocument();
  });

  it("법적 근거 인용만 표시가 있어야 한다", () => {
    render(
      <LegalReviewModal
        mode="approve-request"
        open={true}
        onClose={() => {}}
        request={mockRequest}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByText(/인용만/)).toBeInTheDocument();
  });

  it("승인 버튼이 기본적으로 활성화되어 있다", () => {
    render(
      <LegalReviewModal
        mode="approve-request"
        open={true}
        onClose={() => {}}
        request={mockRequest}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByRole("button", { name: "승인" })).not.toBeDisabled();
  });

  it("승인 클릭 시 onConfirm에 데이터 전달", () => {
    const onConfirm = vi.fn();
    render(
      <LegalReviewModal
        mode="approve-request"
        open={true}
        onClose={() => {}}
        request={mockRequest}
        onConfirm={onConfirm}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "승인" }));
    expect(onConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        legalBasis: "QUOTATION_ONLY",
        summaryAllowed: true,
        fulltextAllowed: false,
        responsibilityAcknowledged: true,
      }),
    );
  });
});
