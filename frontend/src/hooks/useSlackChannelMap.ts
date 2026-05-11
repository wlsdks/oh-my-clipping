import { useQuery } from "@tanstack/react-query";
import { runtimeService } from "@/services/runtimeService";

/**
 * Slack 채널 ID → 채널 이름 매핑을 반환하는 훅.
 * 공개 채널과 비공개 채널을 합쳐서 하나의 Map 으로 반환한다.
 *
 * - 백엔드에서 5분 캐시가 걸려 있어 부담이 적다.
 * - 404 등으로 한쪽이 실패해도 다른 한쪽은 사용 가능하다.
 */
export function useSlackChannelMap() {
  // 관리자 콘솔이므로 admin 전용 엔드포인트 사용 (user setup 경로는 USER 권한만 허용)
  const publicQuery = useQuery({
    queryKey: ["slack-channels", "admin", "public_channel"] as const,
    queryFn: () => runtimeService.listAdminSlackChannels("public_channel"),
    staleTime: 5 * 60_000,
    retry: false,
  });

  const privateQuery = useQuery({
    queryKey: ["slack-channels", "admin", "private_channel"] as const,
    queryFn: () => runtimeService.listAdminSlackChannels("private_channel"),
    staleTime: 5 * 60_000,
    retry: false,
  });

  const channelMap = new Map<string, { name: string; isPrivate: boolean }>();
  for (const ch of publicQuery.data?.channels ?? []) {
    channelMap.set(ch.id, { name: ch.name, isPrivate: ch.isPrivate });
  }
  for (const ch of privateQuery.data?.channels ?? []) {
    channelMap.set(ch.id, { name: ch.name, isPrivate: ch.isPrivate });
  }

  const isLoading = publicQuery.isLoading || privateQuery.isLoading;
  const hasLoaded =
    (publicQuery.isSuccess || publicQuery.isError) &&
    (privateQuery.isSuccess || privateQuery.isError);

  /**
   * 채널 ID → 표시 문자열 변환.
   * - DM 채널 (D로 시작): "DM (개인 메시지)"
   * - 로딩 중: 원본 ID (flicker 방지)
   * - 매핑 성공: "#channel-name"
   * - 매핑 실패(봇 미가입/시드 더미 ID): "알 수 없는 채널"
   *
   * @param opts.dmIfBlank 사용자 요청 컨텍스트에서 빈 channelId 가 "DM 의도"를
   *   의미할 때 true 로 호출한다. 위자드의 DM 모드는 slackChannelId="" 로 저장되므로
   *   카테고리 레벨("채널 미설정"="-")과 구분이 필요.
   */
  function formatChannel(
    channelId: string | null | undefined,
    opts?: { dmIfBlank?: boolean }
  ): string {
    if (!channelId) return opts?.dmIfBlank ? "Slack DM" : "-";
    if (channelId.toUpperCase().startsWith("D")) return "DM (개인 메시지)";
    const entry = channelMap.get(channelId);
    if (entry) return `#${entry.name}`;
    if (!hasLoaded) return channelId;
    return "알 수 없는 채널";
  }

  return {
    channelMap,
    formatChannel,
    isLoading,
    hasLoaded,
  };
}
