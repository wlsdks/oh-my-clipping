export const runtimeKeys = {
  all: ["runtime"] as const,
  health: () => [...runtimeKeys.all, "health"] as const,
  locks: () => [...runtimeKeys.all, "locks"] as const,
  configs: () => [...runtimeKeys.all, "configs"] as const,
  logs: (params?: Record<string, unknown>) =>
    params ? ([...runtimeKeys.all, "logs", params] as const) : ([...runtimeKeys.all, "logs"] as const),
  audits: (limit?: number) =>
    limit !== undefined ? ([...runtimeKeys.all, "audits", limit] as const) : ([...runtimeKeys.all, "audits"] as const),
  slackChannels: (type: "public_channel" | "private_channel") =>
    [...runtimeKeys.all, "slack-channels", type] as const,
  slackChannelInfo: (channelId: string) =>
    [...runtimeKeys.all, "slack-channel-info", channelId] as const,
  blockedChannels: () => [...runtimeKeys.all, "blocked-channels"] as const
};
