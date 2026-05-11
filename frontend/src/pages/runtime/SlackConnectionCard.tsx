import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CheckCircle2, XCircle, Loader2, Wifi } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/utils/cn";
import { runtimeService } from "@/services/runtimeService";
import type { RuntimeSettings, SlackConnectionVerifyResult } from "@/types/runtime";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";

// 백엔드와 동일하게 채널(C) / 그룹(G) / DM(D) / 유저(U) 프리픽스를 모두 허용한다.
// 운영 로그/요청 알림을 DM 또는 비공개 그룹 채널로 받고 싶을 수 있다.
const CHANNEL_ID_RE = /^[CGDU][A-Z0-9]{8,}$/;
const channelIdField = z
  .string()
  .trim()
  .optional()
  .refine(
    (v) => !v || CHANNEL_ID_RE.test(v),
    "C·G·D·U로 시작하는 채널 ID 형식이어야 해요 (예: C0123456789)",
  );

const schema = z.object({
  slackBotToken: z
    .string()
    .optional()
    .refine(
      (v) => !v || v.trim().startsWith("xoxb-"),
      "봇 토큰은 xoxb-로 시작해야 해요",
    ),
  slackDailyChannelMessageLimit: z.coerce
    .number({ invalid_type_error: "숫자를 입력해 주세요" })
    .int("정수만 입력할 수 있어요")
    .min(1, "1건 이상 설정해 주세요")
    .max(1000, "최대 1000건까지 설정할 수 있어요"),
  opsLogChannelId: channelIdField,
  opsRequestChannelId: channelIdField,
  // 토큰 만료·쿼터 등 CRITICAL 전용 채널 (F8). 빈 값이면 opsLog 채널로 폴백된다.
  securityAlertChannelId: channelIdField,
});

type FormValues = z.infer<typeof schema>;

interface Props {
  settings: RuntimeSettings;
  isSaving: boolean;
  onSave: (data: RuntimeSettingsUpdateRequest) => void;
}

export function SlackConnectionCard({ settings, isSaving, onSave }: Props) {
  const [verifying, setVerifying] = useState(false);
  const [verifyResult, setVerifyResult] = useState<SlackConnectionVerifyResult | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      slackBotToken: "",
      slackDailyChannelMessageLimit: settings.slackDailyChannelMessageLimit,
      opsLogChannelId: settings.opsLogChannelId ?? "",
      opsRequestChannelId: settings.opsRequestChannelId ?? "",
      securityAlertChannelId: settings.securityAlertChannelId ?? "",
    },
  });

  const tokenInput = watch("slackBotToken");

  async function handleVerify() {
    setVerifying(true);
    setVerifyResult(null);
    try {
      const result = await runtimeService.verifySlackConnection({
        slackBotToken: tokenInput?.trim() || undefined,
      });
      setVerifyResult(result);
    } catch {
      setVerifyResult({ ok: false, message: "연결 검증에 실패했어요. 네트워크를 확인하세요." });
    } finally {
      setVerifying(false);
    }
  }

  function onSubmit(values: FormValues) {
    onSave({
      slackBotToken: values.slackBotToken?.trim() || undefined,
      slackDailyChannelMessageLimit: values.slackDailyChannelMessageLimit,
      opsLogChannelId: values.opsLogChannelId?.trim() ?? "",
      opsRequestChannelId: values.opsRequestChannelId?.trim() ?? "",
      securityAlertChannelId: values.securityAlertChannelId?.trim() ?? "",
    });
    setVerifyResult(null);
  }

  return (
    <section className="rounded-lg border bg-card p-5 space-y-4">
      <div className="flex items-center gap-2">
        <h3 className="font-semibold text-sm">Slack 연결</h3>
        {settings.slackBotTokenConfigured && (
          <Badge variant="secondary" className="text-xs font-normal">
            현재 설정됨
          </Badge>
        )}
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-3">
          <div className="space-y-1">
            <Label htmlFor="slackBotToken">봇 토큰</Label>
            <div className="flex gap-2">
              <Input
                id="slackBotToken"
                type="password"
                placeholder="xoxb-... (변경 시에만 입력)"
                className="flex-1"
                {...register("slackBotToken")}
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="shrink-0 gap-1.5 h-9"
                disabled={verifying || (!settings.slackBotTokenConfigured && !tokenInput?.trim())}
                onClick={handleVerify}
              >
                {verifying ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Wifi className="h-3.5 w-3.5" />
                )}
                {verifying ? "검증 중..." : "연결 검증"}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              {settings.slackBotTokenConfigured
                ? "현재 토큰이 설정되어 있어요. 변경할 때만 입력하세요."
                : "Slack 봇 토큰을 입력하면 Slack 연동이 활성화돼요."}
            </p>
            {errors.slackBotToken && (
              <p className="text-xs text-destructive">{errors.slackBotToken.message}</p>
            )}
          </div>

          {/* 연결 검증 결과 */}
          {verifyResult && (
            <div
              className={cn(
                "rounded-lg border px-4 py-3 text-sm",
                verifyResult.ok
                  ? "border-[var(--status-success-bg)] bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
                  : "border-[var(--status-danger-bg)] bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
              )}
            >
              <div className="flex items-center gap-2">
                {verifyResult.ok ? (
                  <CheckCircle2 className="h-4 w-4 shrink-0" />
                ) : (
                  <XCircle className="h-4 w-4 shrink-0" />
                )}
                <span className="font-medium">
                  {verifyResult.ok ? "연결 성공" : "연결 실패"}
                </span>
              </div>
              {(verifyResult.botUser || verifyResult.team) && (
                <p className="mt-1.5 text-xs opacity-80">
                  {verifyResult.botUser && <span>봇: {verifyResult.botUser}</span>}
                  {verifyResult.botUser && verifyResult.team && <span> · </span>}
                  {verifyResult.team && <span>워크스페이스: {verifyResult.team}</span>}
                </p>
              )}
              {!verifyResult.ok && (
                <p className="mt-1.5 text-xs opacity-80">{verifyResult.message}</p>
              )}
              {!verifyResult.ok && verifyResult.neededScopes && (
                <p className="mt-1 text-xs opacity-80">
                  필요한 권한: <code className="font-mono">{verifyResult.neededScopes}</code>
                  <span className="ml-1">— Slack 앱 OAuth Scopes에 추가하세요.</span>
                </p>
              )}
              {verifyResult.warning && (
                <p className="mt-1 text-xs opacity-70">{verifyResult.warning}</p>
              )}
            </div>
          )}

          <div className="space-y-1">
            <Label htmlFor="slackDailyChannelMessageLimit">채널별 일일 최대 메시지 수</Label>
            <Input
              id="slackDailyChannelMessageLimit"
              type="number"
              {...register("slackDailyChannelMessageLimit")}
            />
            <p className="text-xs text-muted-foreground">
              하루에 한 채널로 보낼 수 있는 메시지 수 상한이에요.
            </p>
            {errors.slackDailyChannelMessageLimit && (
              <p className="text-xs text-destructive">
                {errors.slackDailyChannelMessageLimit.message}
              </p>
            )}
          </div>

          <div className="space-y-1">
            <Label htmlFor="opsLogChannelId">운영 로그 채널 ID</Label>
            <Input
              id="opsLogChannelId"
              placeholder="C0123456789 (선택)"
              {...register("opsLogChannelId")}
            />
            <p className="text-xs text-muted-foreground">
              파이프라인 실행 요약을 받을 Slack 채널이에요. 비워두면 로그를 보내지 않아요.
            </p>
            {errors.opsLogChannelId && (
              <p className="text-xs text-destructive">{errors.opsLogChannelId.message}</p>
            )}
          </div>

          <div className="space-y-1">
            <Label htmlFor="opsRequestChannelId">운영 요청 알림 채널 ID</Label>
            <Input
              id="opsRequestChannelId"
              placeholder="C0123456789 (선택)"
              {...register("opsRequestChannelId")}
            />
            <p className="text-xs text-muted-foreground">
              가입/승인/반려/비밀번호 초기화 알림을 받을 Slack 채널이에요. 비워두면 알림을 보내지 않아요.
            </p>
            {errors.opsRequestChannelId && (
              <p className="text-xs text-destructive">{errors.opsRequestChannelId.message}</p>
            )}
          </div>

          <div className="space-y-1">
            <Label htmlFor="securityAlertChannelId">보안 알림 채널 ID (선택)</Label>
            <Input
              id="securityAlertChannelId"
              placeholder="C0123456789 (선택)"
              {...register("securityAlertChannelId")}
            />
            <p className="text-xs text-muted-foreground">
              토큰 만료·쿼터 소진 등 즉시 조치가 필요한 알림 전용 채널이에요. 비워두면 운영 로그 채널로 보내요.
            </p>
            {errors.securityAlertChannelId && (
              <p className="text-xs text-destructive">{errors.securityAlertChannelId.message}</p>
            )}
          </div>
        </div>

        <Button type="submit" disabled={isSaving || !isDirty}>
          {isSaving ? "저장 중..." : "저장"}
        </Button>
      </form>
    </section>
  );
}
