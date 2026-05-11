import type { RecentCustomPersona } from "@/types/personaAnalytics";
import { formatKoreanDateTime } from "@/utils/date";

interface Props {
  personas: RecentCustomPersona[];
}

/**
 * 최근 생성된 커스텀 페르소나 테이블.
 * 기존 PersonasPage > StyleStatsTab 의 같은 블록을 Analytics 페이지로 이관한 컴포넌트.
 */
export function RecentCustomPersonasTable({ personas }: Props) {
  return (
    <div className="rounded-2xl border bg-card overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th scope="col" className="text-left px-4 py-2.5 font-medium">유저</th>
            <th scope="col" className="text-left px-4 py-2.5 font-medium">스타일 이름</th>
            <th scope="col" className="text-left px-4 py-2.5 font-medium">프롬프트 미리보기</th>
            <th scope="col" className="text-left px-4 py-2.5 font-medium">생성일</th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {personas.map((p) => (
            <tr key={p.id}>
              <td className="px-4 py-2.5 text-muted-foreground">{p.userName}</td>
              <td className="px-4 py-2.5">{p.personaName}</td>
              <td className="px-4 py-2.5 max-w-xs truncate text-muted-foreground">
                {p.systemPromptPreview}
              </td>
              <td className="px-4 py-2.5 text-muted-foreground whitespace-nowrap">
                {formatKoreanDateTime(p.createdAt)}
              </td>
            </tr>
          ))}
          {personas.length === 0 && (
            <tr>
              <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground text-xs">
                커스텀 스타일이 없어요
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
