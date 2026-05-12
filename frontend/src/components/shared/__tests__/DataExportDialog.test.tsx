import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import type { ReactElement } from "react";

import { DataExportDialog } from "@/components/shared/DataExportDialog";
import { createQueryClientWrapper } from "@/test/queryClient";

// userService 는 네트워크를 타므로 mock 한다.
vi.mock("@/services/userService", () => ({
  userService: {
    downloadPersonalData: vi.fn()
  }
}));

// toast 는 실제 DOM 에 의존하지 않도록 stub 한다.
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() }
}));

import { userService } from "@/services/userService";
import { toast } from "sonner";

function renderWithQueryClient(ui: ReactElement) {
  return render(ui, { wrapper: createQueryClientWrapper() });
}

describe("DataExportDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    // jsdom 의 기본 createObjectURL 구현이 없어 수동 stub.
    if (!("createObjectURL" in URL)) {
      Object.defineProperty(URL, "createObjectURL", {
        configurable: true,
        value: vi.fn(() => "blob:mock")
      });
    } else {
      vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:mock");
    }
    if (!("revokeObjectURL" in URL)) {
      Object.defineProperty(URL, "revokeObjectURL", {
        configurable: true,
        value: vi.fn()
      });
    } else {
      vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    }
  });

  it("다이얼로그가 열리면 제목과 법적 근거 안내가 보인다", () => {
    renderWithQueryClient(<DataExportDialog open onClose={vi.fn()} />);

    expect(screen.getByText("개인정보 내려받기")).toBeInTheDocument();
    expect(screen.getByText(/개인정보보호법 제35조/)).toBeInTheDocument();
    expect(screen.getByText(/하루 최대 3회/)).toBeInTheDocument();
    // JSON / CSV 포맷 옵션이 있어야 한다.
    expect(screen.getByRole("button", { name: /JSON/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /CSV/ })).toBeInTheDocument();
  });

  it("JSON 기본 선택 상태에서 다운로드 버튼을 누르면 json 포맷으로 요청한다", async () => {
    const onClose = vi.fn();
    const blob = new Blob(["{\"ok\":true}"], { type: "application/json" });
    (userService.downloadPersonalData as ReturnType<typeof vi.fn>).mockResolvedValue({
      blob,
      filename: "personal_data_2026-04-17.json"
    });

    renderWithQueryClient(<DataExportDialog open onClose={onClose} />);

    fireEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    await waitFor(() => {
      expect(userService.downloadPersonalData).toHaveBeenCalledWith("json");
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock");
  });

  it("CSV 옵션을 선택하면 csv 포맷으로 다운로드 요청한다", async () => {
    const blob = new Blob(["section,field,value\n"], { type: "text/csv" });
    (userService.downloadPersonalData as ReturnType<typeof vi.fn>).mockResolvedValue({
      blob,
      filename: "personal_data_2026-04-17.csv"
    });

    renderWithQueryClient(<DataExportDialog open onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /CSV/ }));
    fireEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    await waitFor(() => {
      expect(userService.downloadPersonalData).toHaveBeenCalledWith("csv");
    });
  });

  it("다운로드 실패 시 에러 toast 가 뜨고 onClose 는 호출되지 않는다", async () => {
    const onClose = vi.fn();
    (userService.downloadPersonalData as ReturnType<typeof vi.fn>).mockRejectedValue(
      new Error("rate limit")
    );

    renderWithQueryClient(<DataExportDialog open onClose={onClose} />);

    fireEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled();
    });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("취소 버튼을 누르면 onClose 가 호출된다", () => {
    const onClose = vi.fn();
    renderWithQueryClient(<DataExportDialog open onClose={onClose} />);
    fireEvent.click(screen.getByRole("button", { name: /취소/ }));
    expect(onClose).toHaveBeenCalled();
  });
});
