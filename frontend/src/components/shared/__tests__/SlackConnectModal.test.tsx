import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { SlackConnectModal } from "../SlackConnectModal";

describe("SlackConnectModal", () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    onSubmit: vi.fn().mockResolvedValue(undefined),
    isSubmitting: false
  };

  it("모달이 열리면 제목과 가이드가 표시된다", () => {
    render(<SlackConnectModal {...defaultProps} />);
    expect(screen.getByText("Slack DM을 받으려면 멤버 ID가 필요해요")).toBeInTheDocument();
    expect(screen.getByText(/멤버 ID 찾는 법/)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("U01AB2CD3EF")).toBeInTheDocument();
  });

  it("빈 값으로 제출하면 에러 메시지가 표시된다", async () => {
    render(<SlackConnectModal {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "연결하고 구독하기" }));
    await waitFor(() => {
      expect(screen.getByText("Slack 멤버 ID를 입력해 주세요")).toBeInTheDocument();
    });
    expect(defaultProps.onSubmit).not.toHaveBeenCalled();
  });

  it("잘못된 형식이면 에러 메시지가 표시된다", async () => {
    render(<SlackConnectModal {...defaultProps} />);
    fireEvent.change(screen.getByPlaceholderText("U01AB2CD3EF"), {
      target: { value: "invalid" }
    });
    fireEvent.click(screen.getByRole("button", { name: "연결하고 구독하기" }));
    await waitFor(() => {
      expect(screen.getByText(/U로 시작하는 멤버 ID를 입력해 주세요/)).toBeInTheDocument();
    });
    expect(defaultProps.onSubmit).not.toHaveBeenCalled();
  });

  it("유효한 ID로 제출하면 onSubmit이 호출된다", async () => {
    render(<SlackConnectModal {...defaultProps} />);
    fireEvent.change(screen.getByPlaceholderText("U01AB2CD3EF"), {
      target: { value: "U01AB2CD3EF" }
    });
    fireEvent.click(screen.getByRole("button", { name: "연결하고 구독하기" }));
    await waitFor(() => {
      expect(defaultProps.onSubmit).toHaveBeenCalledWith("U01AB2CD3EF");
    });
  });

  it("소문자 입력을 대문자로 변환해 제출한다", async () => {
    render(<SlackConnectModal {...defaultProps} />);
    fireEvent.change(screen.getByPlaceholderText("U01AB2CD3EF"), {
      target: { value: "u01ab2cd3ef" }
    });
    fireEvent.click(screen.getByRole("button", { name: "연결하고 구독하기" }));
    await waitFor(() => {
      expect(defaultProps.onSubmit).toHaveBeenCalledWith("U01AB2CD3EF");
    });
  });

  it("제출 중일 때 버튼이 비활성화된다", () => {
    render(<SlackConnectModal {...defaultProps} isSubmitting={true} />);
    expect(screen.getByRole("button", { name: /연결 중/ })).toBeDisabled();
  });

  it("private-channel 컨텍스트에서 다른 텍스트가 표시된다", () => {
    render(<SlackConnectModal {...defaultProps} context="private-channel" />);
    expect(screen.getByText("비공개 채널을 보려면 Slack 연동이 필요해요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "연동하기" })).toBeInTheDocument();
  });
});
