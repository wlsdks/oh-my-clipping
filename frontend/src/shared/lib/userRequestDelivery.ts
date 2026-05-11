import type { UserClippingRequest } from "../types/admin";

export type UserRequestDeliveryTone = "success" | "pending" | "warning" | "paused" | "danger" | "muted";

const SOURCE_ISSUE_HINT: Record<string, string> = {
  FEED_ERROR: "RSS 주소나 형식을 다시 확인해 주세요.",
  ROBOTS_BLOCKED: "사이트 접근 제한으로 수집할 수 없어요.",
  TIMEOUT: "뉴스 출처 응답이 지연되고 있어요.",
  BLOCKED_URL: "보안 정책으로 차단된 주소예요.",
  FAILED: "뉴스 출처 연결을 다시 확인해 주세요.",
  BLOCKED: "이 뉴스 출처는 현재 접근이 차단돼 있어요."
};

function actionRequiredSourceHint(request: UserClippingRequest): string {
  if (request.totalSourceCount === 0) {
    return "연결된 뉴스 출처가 없어요. 운영팀에 확인을 요청해 주세요.";
  }
  return request.representativeSourceVerificationStatus
    ? (SOURCE_ISSUE_HINT[request.representativeSourceVerificationStatus] ?? "뉴스 출처 설정을 다시 확인해 주세요.")
    : "뉴스 출처 설정을 다시 확인해 주세요.";
}

/**
 * 사용자 요청의 관리자 검토 상태를 읽기 쉬운 문구로 변환한다.
 */
export function requestReviewStatusLabel(status: UserClippingRequest["status"]): string {
  if (status === "APPROVED") return "사용하기 완료";
  if (status === "REJECTED") return "반려";
  if (status === "WITHDRAWN") return "철회됨";
  return "검토 대기";
}

/**
 * 사용자에게 보여줄 실제 전달 상태 라벨을 계산한다.
 */
export function requestDeliveryStatusLabel(request: UserClippingRequest): string {
  switch (request.deliveryState) {
    case "ACTIVE":
      return "구독 중";
    case "PAUSED":
      return "일시정지";
    case "VERIFYING_SOURCE":
      return "연결 확인 중";
    case "ACTION_REQUIRED":
      return "설정 확인 필요";
    case "REJECTED":
      return "반려";
    case "WITHDRAWN":
      return "철회됨";
    default:
      return "검토 중";
  }
}

/**
 * 상태 라벨에 맞는 톤을 반환한다.
 */
export function requestDeliveryTone(request: UserClippingRequest): UserRequestDeliveryTone {
  switch (request.deliveryState) {
    case "ACTIVE":
      return "success";
    case "PAUSED":
      return "paused";
    case "VERIFYING_SOURCE":
    case "PENDING_REVIEW":
      return "pending";
    case "ACTION_REQUIRED":
      return "warning";
    case "REJECTED":
      return "danger";
    case "WITHDRAWN":
      return "muted";
    default:
      return "pending";
  }
}

/**
 * 사용자와 운영자 화면에 공통으로 노출할 보조 설명을 만든다.
 */
export function requestDeliveryHint(request: UserClippingRequest): string | null {
  switch (request.deliveryState) {
    case "ACTIVE":
      if (request.totalSourceCount > request.readySourceCount) {
        return `뉴스 출처 ${request.readySourceCount}/${request.totalSourceCount}개 준비 완료, 뉴스가 전달되고 있어요.`;
      }
      return "뉴스 전달이 정상적으로 켜져 있어요.";
    case "PAUSED":
      if (request.totalSourceCount === 0 || request.readySourceCount === 0) {
        return `지금은 새 뉴스를 보내지 않아요. 다시 켜기 전에 ${actionRequiredSourceHint(request)}`;
      }
      if (request.totalSourceCount > request.readySourceCount) {
        return `지금은 새 뉴스를 보내지 않아요. 다시 켜면 연결된 ${request.readySourceCount}개 출처부터 재개돼요.`;
      }
      return "지금은 새 뉴스를 보내지 않아요. 다시 켜면 다음 수집부터 재개돼요.";
    case "VERIFYING_SOURCE":
      return request.totalSourceCount === 0
        ? "승인은 완료됐지만 아직 연결된 뉴스 출처가 없어요."
        : "승인은 완료됐고, 뉴스 출처 연결을 확인하고 있어요.";
    case "ACTION_REQUIRED":
      return actionRequiredSourceHint(request);
    case "REJECTED":
      return "반려 사유를 확인한 뒤 다시 요청할 수 있어요.";
    case "WITHDRAWN":
      return "철회된 요청은 다음 날 자동으로 목록에서 사라져요.";
    default:
      return "관리자가 내용을 확인하고 있어요.";
  }
}

function compareIsoDateDesc(left: string | null | undefined, right: string | null | undefined): number {
  const leftTime = left ? Date.parse(left) : 0;
  const rightTime = right ? Date.parse(right) : 0;
  return rightTime - leftTime;
}

function approvedRequestPriority(request: UserClippingRequest): number {
  switch (request.deliveryState) {
    case "ACTION_REQUIRED":
      return 0;
    case "VERIFYING_SOURCE":
      return 1;
    case "ACTIVE":
      return 2;
    case "PAUSED":
      return 3;
    default:
      return 4;
  }
}

function displayRequestPriority(request: UserClippingRequest): number {
  if (request.status === "PENDING") return 0;
  if (request.status === "REJECTED") return 1;
  if (request.deliveryState === "ACTION_REQUIRED") return 2;
  if (request.deliveryState === "VERIFYING_SOURCE") return 3;
  if (request.deliveryState === "ACTIVE") return 4;
  if (request.deliveryState === "PAUSED") return 5;
  if (request.status === "WITHDRAWN") return 6;
  return 7;
}

function additionalSourceBasePriority(request: UserClippingRequest): number {
  switch (request.deliveryState) {
    case "ACTIVE":
      return 0;
    case "VERIFYING_SOURCE":
      return 1;
    case "PAUSED":
      return 2;
    case "ACTION_REQUIRED":
      return 3;
    default:
      return 4;
  }
}

/**
 * 사용자 홈/관리 화면에서 승인된 구독을 상태 우선순위대로 정렬한다.
 */
export function compareApprovedUserRequests(left: UserClippingRequest, right: UserClippingRequest): number {
  const priorityDiff = approvedRequestPriority(left) - approvedRequestPriority(right);
  if (priorityDiff !== 0) return priorityDiff;
  return compareIsoDateDesc(left.updatedAt, right.updatedAt);
}

/**
 * 진행 상태 화면에서 사용자가 먼저 확인해야 할 요청이 위에 오도록 정렬한다.
 */
export function compareUserRequestsForDisplay(left: UserClippingRequest, right: UserClippingRequest): number {
  const priorityDiff = displayRequestPriority(left) - displayRequestPriority(right);
  if (priorityDiff !== 0) return priorityDiff;
  return compareIsoDateDesc(left.updatedAt, right.updatedAt);
}

/**
 * RSS 추가 요청의 기본 선택 주제는 이미 안정적으로 쓰는 구독이 먼저 오도록 정렬한다.
 */
export function compareUserRequestsForAdditionalSource(left: UserClippingRequest, right: UserClippingRequest): number {
  const priorityDiff = additionalSourceBasePriority(left) - additionalSourceBasePriority(right);
  if (priorityDiff !== 0) return priorityDiff;
  return compareIsoDateDesc(left.updatedAt, right.updatedAt);
}
