import type { StylePreset } from "./PersonaPresetGrid";

interface PersonaPreviewProps {
  /** 선택된 프리셋 (미리보기 데이터 보유) */
  preset: StylePreset;
}

/**
 * 선택한 요약 스타일이 Slack 에 전달될 때의 모습을 미니 메시지 카드로 미리 보여준다.
 * previewTitle 이 비어있으면 상위에서 호출 자체를 생략한다.
 */
export function PersonaPreview({ preset }: PersonaPreviewProps) {
  return (
    <div className="rounded-lg border bg-muted/30 p-3 space-y-2" aria-label="Slack 미리보기">
      <div className="flex items-center gap-2">
        <div
          className="w-7 h-7 rounded-full bg-[var(--status-neutral-text)] flex items-center justify-center text-white text-xs font-bold shrink-0"
          aria-hidden="true"
        >
          N
        </div>
        <div>
          <span className="text-xs font-medium">뉴스봇</span>
          <span className="text-xs text-muted-foreground ml-2">오전 9:00</span>
        </div>
      </div>
      <div className="ml-9 border-l-2 border-[var(--status-neutral-text)] pl-3 space-y-1">
        <div className="text-xs text-muted-foreground">{preset.previewSource}</div>
        <div className="text-xs font-medium">{preset.previewTitle}</div>
        <div className="text-xs text-muted-foreground whitespace-pre-line">{preset.previewBody}</div>
      </div>
    </div>
  );
}
