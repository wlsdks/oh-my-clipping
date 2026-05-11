import type { Persona } from "@/types/persona";
import { cn } from "@/utils/cn";
import { Badge } from "@/components/ui/badge";
import { FileText } from "lucide-react";
import { EmojiWithText } from "@/components/shared/EmojiWithText";
import { MetaDot } from "@/components/shared/MetaDot";

interface PresetCardProps {
  persona: Persona;
  subscriptionCount: number;
  onClick: () => void;
}

function extractEmoji(text: string | null): string | null {
  if (!text) return null;
  const emojiMatch = text.match(/\p{Emoji_Presentation}|\p{Extended_Pictographic}/u);
  return emojiMatch ? emojiMatch[0] : null;
}

export function PresetCard({ persona, subscriptionCount, onClick }: PresetCardProps) {
  const emoji = extractEmoji(persona.previewTitle);

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "group bg-card rounded-2xl p-5 border border-border shadow-sm cursor-pointer text-left",
        "transition-colors duration-150 hover:bg-accent/50",
        !persona.isActive && "opacity-55"
      )}
    >
      {/* 상단: 이모지 아이콘 + 이름 + 상태/버전 뱃지 */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <EmojiWithText
          emoji={emoji}
          text={persona.name}
          size="md"
          fallbackIcon={<FileText className="h-4 w-4 text-muted-foreground" />}
          className="min-w-0 flex-1 gap-2.5"
          textClassName="font-semibold text-sm"
        />

        <div className="flex items-center gap-1 shrink-0">
          <Badge
            variant={persona.isActive ? "secondary" : "outline"}
            className={cn(
              "text-[11px] px-1.5 py-0 h-5",
              persona.isActive
                ? "text-foreground/70"
                : "text-muted-foreground"
            )}
          >
            {persona.isActive ? "활성" : "비활성"}
          </Badge>
          <Badge variant="outline" className="text-[11px] px-1.5 py-0 h-5 text-muted-foreground">
            v{persona.currentVersion}
          </Badge>
        </div>
      </div>

      {/* 설명 */}
      {persona.description && (
        <p className="text-xs text-muted-foreground line-clamp-1 mb-2.5">
          {persona.description}
        </p>
      )}

      {/* 구분선 */}
      <div className="border-t border-border/50 mt-3 pt-3">
        {/* 하단 메타: 대상 독자 · 구독 건수 */}
        <MetaDot
          className="text-xs text-muted-foreground"
          items={[persona.targetAudience, `구독 ${subscriptionCount}건`]}
        />
      </div>
    </button>
  );
}
