import { Link } from "react-router-dom";
import { ArrowRight, FileText, Inbox, Send } from "lucide-react";

import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { cn } from "@/utils/cn";

interface PipelineStatusCardProps {
  collected: number;
  summarized: number;
  sent: number;
  lastSuccessAt: string | null;
}

/** ISO datetime → 상대 시간 */
function formatRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return "방금 전";
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  return `${Math.floor(hr / 24)}일 전`;
}

interface StageProps {
  icon: React.ComponentType<{ className?: string; "aria-hidden"?: boolean }>;
  label: string;
  value: number;
}

/** 파이프라인 단계 1개를 렌더하는 작은 헬퍼. */
function Stage({ icon: Icon, label, value }: StageProps) {
  return (
    <div className="flex flex-col items-center gap-1">
      <Icon className="h-4 w-4 text-muted-foreground" aria-hidden />
      <span className="text-xs font-normal text-muted-foreground">{label}</span>
      <span className="text-2xl font-bold tabular-nums text-foreground leading-none">{value}</span>
    </div>
  );
}

/**
 * 오늘의 파이프라인 단계별 카운트를 보여주는 카드.
 * 수집 → 요약 → 발송 흐름을 세로 카드 내부에 컴팩트하게 정리한다.
 */
export function PipelineStatusCard({ collected, summarized, sent, lastSuccessAt }: PipelineStatusCardProps) {
  const isEmpty = collected === 0 && summarized === 0 && sent === 0;

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <h2 className="text-base font-semibold tracking-tight leading-none">파이프라인 상태</h2>
        <Link
          to="/admin/pipeline"
          className={cn(
            "flex items-center gap-1 text-xs font-semibold text-primary",
            "hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded",
          )}
        >
          전체 보기 <ArrowRight size={12} aria-hidden="true" />
        </Link>
      </CardHeader>
      <CardContent>
        {isEmpty ? (
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground">오늘 아직 실행되지 않았어요</p>
            {lastSuccessAt && (
              <p className="text-xs text-muted-foreground tabular-nums">
                마지막 실행: {formatRelative(lastSuccessAt)}
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-2">
              <Stage icon={Inbox} label="수집" value={collected} />
              <span className="text-muted-foreground" aria-hidden="true">
                →
              </span>
              <Stage icon={FileText} label="요약" value={summarized} />
              <span className="text-muted-foreground" aria-hidden="true">
                →
              </span>
              <Stage icon={Send} label="발송" value={sent} />
            </div>
            {lastSuccessAt && (
              <p className="text-xs text-muted-foreground tabular-nums text-center">
                마지막 성공: {formatRelative(lastSuccessAt)}
              </p>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
