import { describe, expect, it } from "vitest";
import type { UserClippingRequest } from "../../types/admin";
import {
  requestDeliveryHint,
  requestReviewStatusLabel,
  requestDeliveryStatusLabel,
  requestDeliveryTone,
  compareUserRequestsForDisplay,
  compareApprovedUserRequests,
  compareUserRequestsForAdditionalSource
} from "../userRequestDelivery";

function makeRequest(overrides: Partial<UserClippingRequest>): UserClippingRequest {
  return {
    id: "r1",
    requesterUserId: "u1",
    requestName: "테스트",
    sourceName: "소스",
    sourceUrl: "",
    slackChannelId: "",
    personaName: "",
    personaPrompt: "",
    status: "APPROVED",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    deliveryState: "ACTIVE",
    collectingReady: true,
    totalSourceCount: 1,
    readySourceCount: 1,
    ...overrides
  };
}

describe("requestDeliveryHint", () => {
  it("ACTIVE with all sources ready", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "ACTIVE", totalSourceCount: 2, readySourceCount: 2 })
    );
    expect(hint).toContain("정상적으로");
  });

  it("ACTIVE with partial sources", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "ACTIVE", totalSourceCount: 3, readySourceCount: 2 })
    );
    expect(hint).toContain("2/3개 준비 완료");
  });

  it("ACTIVE with zero ready sources", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "ACTIVE", totalSourceCount: 0, readySourceCount: 0 })
    );
    expect(hint).toContain("정상적으로");
  });

  it("PAUSED with no sources", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "PAUSED", totalSourceCount: 0, readySourceCount: 0 })
    );
    expect(hint).toContain("보내지 않아요");
  });

  it("PAUSED with partial sources", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "PAUSED", totalSourceCount: 3, readySourceCount: 2 })
    );
    expect(hint).toContain("2개 출처부터 재개");
  });

  it("PAUSED with all sources ready", () => {
    const hint = requestDeliveryHint(
      makeRequest({ deliveryState: "PAUSED", totalSourceCount: 2, readySourceCount: 2 })
    );
    expect(hint).toContain("다시 켜면 다음 수집부터");
  });

  it("VERIFYING_SOURCE with sources", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "VERIFYING_SOURCE", totalSourceCount: 1 }));
    expect(hint).toContain("확인하고 있어요");
  });

  it("VERIFYING_SOURCE with no sources", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "VERIFYING_SOURCE", totalSourceCount: 0 }));
    expect(hint).toContain("아직 연결된 뉴스 출처가 없어요");
  });

  it("ACTION_REQUIRED with zero totalSourceCount", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "ACTION_REQUIRED", totalSourceCount: 0 }));
    expect(hint).toContain("연결된 뉴스 출처가 없어요");
  });

  it("ACTION_REQUIRED with known verification status", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "FEED_ERROR"
      })
    );
    expect(hint).toContain("RSS");
  });

  it("REJECTED hint", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "REJECTED" }));
    expect(hint).toContain("반려 사유");
  });

  it("WITHDRAWN hint", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "WITHDRAWN" }));
    expect(hint).toContain("철회된");
  });

  it("default (PENDING_REVIEW) hint", () => {
    const hint = requestDeliveryHint(makeRequest({ deliveryState: "PENDING_REVIEW" }));
    expect(hint).toContain("관리자가");
  });
});

describe("compareUserRequestsForDisplay", () => {
  it("PENDING comes before APPROVED/ACTIVE", () => {
    const pending = makeRequest({ status: "PENDING", deliveryState: "PENDING_REVIEW" });
    const active = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE" });
    expect(compareUserRequestsForDisplay(pending, active)).toBeLessThan(0);
  });

  it("same status sorts by updatedAt descending", () => {
    const older = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE", updatedAt: "2024-01-01T00:00:00Z" });
    const newer = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE", updatedAt: "2024-06-01T00:00:00Z" });
    expect(compareUserRequestsForDisplay(older, newer)).toBeGreaterThan(0);
  });

  it("REJECTED comes before ACTIVE", () => {
    const rejected = makeRequest({ status: "REJECTED", deliveryState: "REJECTED" });
    const active = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE" });
    expect(compareUserRequestsForDisplay(rejected, active)).toBeLessThan(0);
  });
});

describe("compareApprovedUserRequests", () => {
  it("ACTION_REQUIRED > VERIFYING > ACTIVE > PAUSED", () => {
    const actionReq = makeRequest({ deliveryState: "ACTION_REQUIRED" });
    const verifying = makeRequest({ deliveryState: "VERIFYING_SOURCE" });
    const active = makeRequest({ deliveryState: "ACTIVE" });
    const paused = makeRequest({ deliveryState: "PAUSED" });

    expect(compareApprovedUserRequests(actionReq, verifying)).toBeLessThan(0);
    expect(compareApprovedUserRequests(verifying, active)).toBeLessThan(0);
    expect(compareApprovedUserRequests(active, paused)).toBeLessThan(0);
  });

  it("same state sorts by updatedAt descending", () => {
    const older = makeRequest({ deliveryState: "ACTIVE", updatedAt: "2024-01-01T00:00:00Z" });
    const newer = makeRequest({ deliveryState: "ACTIVE", updatedAt: "2024-06-01T00:00:00Z" });
    expect(compareApprovedUserRequests(older, newer)).toBeGreaterThan(0);
  });
});

describe("requestReviewStatusLabel", () => {
  it("APPROVED => '사용하기 완료'", () => {
    expect(requestReviewStatusLabel("APPROVED")).toBe("사용하기 완료");
  });

  it("REJECTED => '반려'", () => {
    expect(requestReviewStatusLabel("REJECTED")).toBe("반려");
  });

  it("WITHDRAWN => '철회됨'", () => {
    expect(requestReviewStatusLabel("WITHDRAWN")).toBe("철회됨");
  });

  it("PENDING => '검토 대기'", () => {
    expect(requestReviewStatusLabel("PENDING")).toBe("검토 대기");
  });
});

describe("requestDeliveryStatusLabel", () => {
  it("ACTIVE => '구독 중'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "ACTIVE" }))).toBe("구독 중");
  });

  it("PAUSED => '일시정지'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "PAUSED" }))).toBe("일시정지");
  });

  it("VERIFYING_SOURCE => '연결 확인 중'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "VERIFYING_SOURCE" }))).toBe("연결 확인 중");
  });

  it("ACTION_REQUIRED => '설정 확인 필요'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "ACTION_REQUIRED" }))).toBe("설정 확인 필요");
  });

  it("REJECTED => '반려'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "REJECTED" }))).toBe("반려");
  });

  it("WITHDRAWN => '철회됨'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "WITHDRAWN" }))).toBe("철회됨");
  });

  it("PENDING_REVIEW (default) => '검토 중'", () => {
    expect(requestDeliveryStatusLabel(makeRequest({ deliveryState: "PENDING_REVIEW" }))).toBe("검토 중");
  });
});

describe("requestDeliveryTone", () => {
  it("ACTIVE => 'success'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "ACTIVE" }))).toBe("success");
  });

  it("PAUSED => 'paused'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "PAUSED" }))).toBe("paused");
  });

  it("VERIFYING_SOURCE => 'pending'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "VERIFYING_SOURCE" }))).toBe("pending");
  });

  it("PENDING_REVIEW => 'pending'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "PENDING_REVIEW" }))).toBe("pending");
  });

  it("ACTION_REQUIRED => 'warning'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "ACTION_REQUIRED" }))).toBe("warning");
  });

  it("REJECTED => 'danger'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "REJECTED" }))).toBe("danger");
  });

  it("WITHDRAWN => 'muted'", () => {
    expect(requestDeliveryTone(makeRequest({ deliveryState: "WITHDRAWN" }))).toBe("muted");
  });
});

describe("compareUserRequestsForAdditionalSource", () => {
  it("ACTIVE comes before VERIFYING_SOURCE", () => {
    const active = makeRequest({ deliveryState: "ACTIVE" });
    const verifying = makeRequest({ deliveryState: "VERIFYING_SOURCE" });
    expect(compareUserRequestsForAdditionalSource(active, verifying)).toBeLessThan(0);
  });

  it("VERIFYING_SOURCE comes before PAUSED", () => {
    const verifying = makeRequest({ deliveryState: "VERIFYING_SOURCE" });
    const paused = makeRequest({ deliveryState: "PAUSED" });
    expect(compareUserRequestsForAdditionalSource(verifying, paused)).toBeLessThan(0);
  });

  it("PAUSED comes before ACTION_REQUIRED", () => {
    const paused = makeRequest({ deliveryState: "PAUSED" });
    const actionReq = makeRequest({ deliveryState: "ACTION_REQUIRED" });
    expect(compareUserRequestsForAdditionalSource(paused, actionReq)).toBeLessThan(0);
  });

  it("same priority sorts by updatedAt descending", () => {
    const older = makeRequest({ deliveryState: "ACTIVE", updatedAt: "2024-01-01T00:00:00Z" });
    const newer = makeRequest({ deliveryState: "ACTIVE", updatedAt: "2024-06-01T00:00:00Z" });
    expect(compareUserRequestsForAdditionalSource(older, newer)).toBeGreaterThan(0);
  });
});

describe("requestDeliveryHint — additional SOURCE_ISSUE_HINT codes", () => {
  it("ROBOTS_BLOCKED hint", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "ROBOTS_BLOCKED"
      })
    );
    expect(hint).toContain("접근 제한");
  });

  it("TIMEOUT hint", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "TIMEOUT"
      })
    );
    expect(hint).toContain("응답이 지연");
  });

  it("BLOCKED_URL hint", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "BLOCKED_URL"
      })
    );
    expect(hint).toContain("보안 정책");
  });

  it("FAILED hint", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "FAILED"
      })
    );
    expect(hint).toContain("다시 확인해 주세요");
  });

  it("BLOCKED hint", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "BLOCKED"
      })
    );
    expect(hint).toContain("차단");
  });

  it("unknown status falls back to generic message", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: "UNKNOWN_CODE"
      })
    );
    expect(hint).toContain("다시 확인해 주세요");
  });

  it("null representativeSourceVerificationStatus falls back to generic message", () => {
    const hint = requestDeliveryHint(
      makeRequest({
        deliveryState: "ACTION_REQUIRED",
        totalSourceCount: 1,
        representativeSourceVerificationStatus: null
      })
    );
    expect(hint).toContain("다시 확인해 주세요");
  });
});

describe("compareUserRequestsForDisplay — additional priorities", () => {
  it("WITHDRAWN comes after ACTIVE", () => {
    const withdrawn = makeRequest({ status: "WITHDRAWN", deliveryState: "WITHDRAWN" });
    const active = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE" });
    expect(compareUserRequestsForDisplay(withdrawn, active)).toBeGreaterThan(0);
  });

  it("ACTION_REQUIRED comes before ACTIVE", () => {
    const actionReq = makeRequest({ status: "APPROVED", deliveryState: "ACTION_REQUIRED" });
    const active = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE" });
    expect(compareUserRequestsForDisplay(actionReq, active)).toBeLessThan(0);
  });

  it("VERIFYING_SOURCE comes before ACTIVE", () => {
    const verifying = makeRequest({ status: "APPROVED", deliveryState: "VERIFYING_SOURCE" });
    const active = makeRequest({ status: "APPROVED", deliveryState: "ACTIVE" });
    expect(compareUserRequestsForDisplay(verifying, active)).toBeLessThan(0);
  });

  it("null updatedAt treated as epoch (oldest) in date comparison", () => {
    const withDate = makeRequest({ deliveryState: "ACTIVE", updatedAt: "2024-01-01T00:00:00Z" });
    const withNull = makeRequest({ deliveryState: "ACTIVE", updatedAt: null as unknown as string });
    // withDate is newer than null (epoch 0), so withNull should come after
    expect(compareUserRequestsForDisplay(withDate, withNull)).toBeLessThan(0);
  });
});
