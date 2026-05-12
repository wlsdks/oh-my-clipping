import { describe, it, expect, beforeEach, vi } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { createQueryClientWrapper } from "@/test/queryClient";

// Mocks must be hoisted before imports
vi.mock("sonner", () => ({
  toast: {
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  },
}));

vi.mock("@/services/userService", () => ({
  userService: {
    createRequestWithEntries: vi.fn(),
  },
}));

import { toast } from "sonner";
import { userService } from "@/services/userService";
import { useQuickSetupSubmit } from "../useQuickSetupSubmit";
import { createQuickSetupForm } from "../model/quickSetupTypes";
import type { QuickSetupForm } from "../model/quickSetupTypes";
import type { SubmitWithEntriesResponse } from "@/types/adminDto";

const SUBMITTED_OK: SubmitWithEntriesResponse = {
  requestId: "req-1",
  status: "submitted",
  errors: [],
};

const sampleForm: QuickSetupForm = {
  ...createQuickSetupForm(),
  categoryName: "에듀테크 영업",
  entries: [
    { value: "리스킬링", type: "keyword" },
    { value: "MegaCorp", type: "company", stockCode: "999930" },
  ],
};

describe("useQuickSetupSubmit", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(userService.createRequestWithEntries).mockResolvedValue(SUBMITTED_OK);
  });

  describe("요청 바디 구성", () => {
    it("entries 배열을 한 번에 POST", async () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      expect(userService.createRequestWithEntries).toHaveBeenCalledTimes(1);
      const body = vi.mocked(userService.createRequestWithEntries).mock.calls[0][0];
      expect(body.entries).toHaveLength(2);
      expect(body.categoryName).toBe("에듀테크 영업");
    });

    it("company 타입 항목에는 stockCode를 포함한다", async () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      const body = vi.mocked(userService.createRequestWithEntries).mock.calls[0][0];
      expect(body.entries[1].stockCode).toBe("999930");
    });

    it("keyword 타입 항목에는 stockCode를 포함하지 않는다", async () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      const body = vi.mocked(userService.createRequestWithEntries).mock.calls[0][0];
      expect(body.entries[0].type).toBe("keyword");
      expect(body.entries[0].stockCode).toBeUndefined();
    });
  });

  describe("submitted 응답", () => {
    it("toast를 호출하지 않는다", async () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      expect(toast.error).not.toHaveBeenCalled();
      expect(toast.warning).not.toHaveBeenCalled();
    });
  });

  describe("partial 응답", () => {
    it("toast.warning 호출 — 상위 3건 + 외 N건 메시지", async () => {
      vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
        requestId: "r",
        status: "partial",
        errors: [
          { index: 0, value: "A", reason: "DUPLICATE_IN_REQUEST" },
          { index: 1, value: "B", reason: "DUPLICATE_IN_REQUEST" },
          { index: 2, value: "C", reason: "DUPLICATE_IN_REQUEST" },
          { index: 3, value: "D", reason: "DUPLICATE_IN_REQUEST" },
        ],
      });

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      await waitFor(() => {
        expect(toast.warning).toHaveBeenCalled();
      });
      const [title, opts] = (toast.warning as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(title).toContain("4개 항목이 제외");
      expect(opts.description).toContain("외 1건");
    });

    it("3건 이하면 '외 N건' 접미사 없음", async () => {
      vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
        requestId: "r",
        status: "partial",
        errors: [{ index: 0, value: "X", reason: "VALIDATION_FAILED" }],
      });

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      await waitFor(() => expect(toast.warning).toHaveBeenCalled());
      const [, opts] = (toast.warning as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(opts.description).not.toContain("외");
    });

    it("partial 응답 시 toast.error는 호출되지 않는다", async () => {
      vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
        requestId: "r",
        status: "partial",
        errors: [{ index: 0, value: "X", reason: "VALIDATION_FAILED" }],
      });

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      expect(toast.error).not.toHaveBeenCalled();
    });
  });

  describe("rejected 응답", () => {
    it("toast.error 호출", async () => {
      vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
        requestId: "",
        status: "rejected",
        errors: [{ index: 0, value: "Coursera", reason: "COMPETITOR_WATCHLIST_CONFLICT" }],
      });

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      await waitFor(() => expect(toast.error).toHaveBeenCalled());
      const [title] = (toast.error as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(title).toContain("반려");
    });

    it("toast.warning은 호출되지 않는다 (조기 리턴)", async () => {
      vi.mocked(userService.createRequestWithEntries).mockResolvedValue({
        requestId: "",
        status: "rejected",
        errors: [{ index: 0, value: "A", reason: "VALIDATION_FAILED" }],
      });

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });

      await waitFor(() => expect(toast.error).toHaveBeenCalled());
      expect(toast.warning).not.toHaveBeenCalled();
    });
  });

  describe("에러 경로", () => {
    it("API 오류 시 toast.error 호출", async () => {
      vi.mocked(userService.createRequestWithEntries).mockRejectedValue(
        new Error("네트워크 오류")
      );

      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm).catch(() => {});
      });

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled();
      });
      // userFriendlyMessage 게이트웨이를 통과해 사용자 친화 메시지로 변환된다.
      // raw err.message ("네트워크 오류") 가 description 으로 노출되면 안 된다.
      const args = vi.mocked(toast.error).mock.calls[0];
      expect(args[0]).toContain("요청 전송에 실패했어요");
      expect(args[0]).not.toContain("네트워크 오류");
    });
  });

  describe("isPending 상태", () => {
    it("초기 상태는 isPending=false", () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      expect(result.current.isPending).toBe(false);
    });

    it("submit 후 isPending=false (완료 기준)", async () => {
      const { result } = renderHook(() => useQuickSetupSubmit(), { wrapper: createQueryClientWrapper() });
      await act(async () => {
        await result.current.submit(sampleForm);
      });
      expect(result.current.isPending).toBe(false);
    });
  });
});
