export interface SlackChannelValidationResult {
  isValid: boolean;
  normalized: string;
  message: string | null;
}

const CHANNEL_ID_PATTERN = /^[CG][A-Z0-9]{8,}$/;

function extractChannelFromUrl(raw: string): string | null {
  try {
    const parsed = new URL(raw);
    const segments = parsed.pathname.split("/").filter(Boolean);
    const archiveIndex = segments.findIndex((segment) => segment.toLowerCase() === "archives");

    if (archiveIndex >= 0 && archiveIndex + 1 < segments.length) {
      return segments[archiveIndex + 1] || null;
    }

    const queryChannel = parsed.searchParams.get("channel");
    if (queryChannel) return queryChannel;

    if (parsed.hash.includes("channel=")) {
      const hashParams = new URLSearchParams(parsed.hash.replace(/^#/, ""));
      const hashChannel = hashParams.get("channel");
      if (hashChannel) return hashChannel;
    }
  } catch {
    return null;
  }

  return null;
}

function cleanupCandidate(raw: string): string {
  const mentionMatch = raw.match(/<#([CG][A-Z0-9]{8,})\|/i);
  if (mentionMatch?.[1]) {
    return mentionMatch[1];
  }

  const idLikeMatch = raw.match(/([CG][A-Z0-9]{8,})/i);
  if (idLikeMatch?.[1]) {
    return idLikeMatch[1];
  }

  return raw;
}

/**
 * 사용자 입력(채널 ID, Slack 링크, ID:Cxxxx, <#Cxxxx|name>)에서 채널 ID를 추출합니다.
 */
export function normalizeSlackChannelInput(raw: string): string | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0) return null;

  const fromUrl =
    trimmed.startsWith("http://") || trimmed.startsWith("https://") ? extractChannelFromUrl(trimmed) : null;
  const normalized = cleanupCandidate(fromUrl || trimmed)
    .trim()
    .replace(/^#/, "")
    .replace(/^id:/i, "")
    .trim()
    .toUpperCase();

  return CHANNEL_ID_PATTERN.test(normalized) ? normalized : null;
}

export function validateSlackChannelInput(
  raw: string,
  options?: { allowBlank?: boolean; fieldLabel?: string }
): SlackChannelValidationResult {
  const allowBlank = options?.allowBlank ?? true;
  const fieldLabel = options?.fieldLabel ?? "Slack 채널";
  const trimmed = raw.trim();

  if (trimmed.length === 0) {
    return {
      isValid: allowBlank,
      normalized: "",
      message: allowBlank ? null : `${fieldLabel}을(를) 입력하세요.`
    };
  }

  const normalized = normalizeSlackChannelInput(trimmed);
  if (!normalized) {
    return {
      isValid: false,
      normalized: "",
      message: `${fieldLabel}은 채널 ID 형식(C/G로 시작) 또는 Slack 채널 링크를 입력하세요.`
    };
  }

  return {
    isValid: true,
    normalized,
    message: null
  };
}

export function slackChannelNormalizationHint(raw: string): string | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0) return null;

  const normalized = normalizeSlackChannelInput(trimmed);
  if (!normalized) return null;

  if (trimmed.toUpperCase() === normalized) return null;
  return `입력값에서 채널 ID ${normalized}로 인식했어요.`;
}

export function isSlackDirectMessageDestination(raw?: string | null): boolean {
  const trimmed = raw?.trim() ?? "";
  return trimmed.length === 0 || trimmed.toUpperCase().startsWith("D") || trimmed.toUpperCase().startsWith("U");
}

export function formatSlackDestinationLabel(
  raw?: string | null,
  options?: { blankLabel?: string; genericChannelLabel?: string }
): string {
  const trimmed = raw?.trim() ?? "";
  if (trimmed.length === 0) {
    return options?.blankLabel ?? "Slack DM";
  }
  if (trimmed.toUpperCase().startsWith("D") || trimmed.toUpperCase().startsWith("U")) {
    return "Slack DM";
  }
  if (trimmed.startsWith("#")) {
    return trimmed;
  }
  if (options?.genericChannelLabel) {
    return options.genericChannelLabel;
  }
  return `#${trimmed}`;
}

export function formatSlackDestinationDescription(
  raw?: string | null,
  options?: { blankLabel?: string; genericChannelLabel?: string }
): string {
  if (isSlackDirectMessageDestination(raw)) {
    return options?.blankLabel ?? "Slack DM으로 수신";
  }
  return `${formatSlackDestinationLabel(raw, options)}로 수신`;
}
