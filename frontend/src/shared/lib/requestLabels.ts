import type { UserClippingRequest } from "../types/admin";
import { formatKoreanDateTime } from "./dateTime";
import { formatSlackDestinationLabel } from "./slackChannel";

function requestNameKey(name: string): string {
  return name.trim();
}

/**
 * 같은 요청명이 여러 개일 때만 보조 식별 라벨을 붙일 수 있도록 이름 빈도를 계산한다.
 */
export function buildUserRequestNameCountMap(
  requests: Pick<UserClippingRequest, "requestName">[]
): Record<string, number> {
  return requests.reduce<Record<string, number>>((acc, request) => {
    const key = requestNameKey(request.requestName);
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
}

/**
 * 사용자에게 보여줄 요청 제목을 정리한다.
 */
export function formatUserRequestName(request: Pick<UserClippingRequest, "requestName">): string {
  return request.requestName.trim();
}

/**
 * 동일한 요청명이 중복될 때만 채널/출처 기준의 보조 구분 라벨을 만든다.
 */
export function formatUserRequestDisambiguation(
  request: Pick<UserClippingRequest, "requestName" | "slackChannelId" | "sourceName" | "createdAt">,
  nameCounts?: Record<string, number>
): string | null {
  const duplicateCount = nameCounts?.[requestNameKey(request.requestName)] ?? 1;
  if (duplicateCount <= 1) return null;

  const destination = formatSlackDestinationLabel(request.slackChannelId, {
    blankLabel: "Slack DM",
    genericChannelLabel: "Slack 채널"
  });
  const sourceName = request.sourceName.trim();
  if (sourceName.length > 0) {
    return `${destination} · ${sourceName}`;
  }

  const formattedCreatedAt = formatKoreanDateTime(request.createdAt);
  return `${destination} · ${formattedCreatedAt === "-" ? "시각 미상" : formattedCreatedAt.slice(0, 16)}`;
}

/**
 * 화면 노출용 requestNote에서 내부 메타데이터 토큰을 제거한다.
 */
export function formatUserRequestNote(note?: string | null): string | null {
  const cleaned = note?.replace(/\[baseRequestId=[^\]]+\]/g, "").trim() ?? "";
  return cleaned.length > 0 ? cleaned : null;
}
