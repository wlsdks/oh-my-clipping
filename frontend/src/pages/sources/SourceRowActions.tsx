import { MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import type { Source } from "@/types/source";

type Mode = "active" | "archived";

interface SourceRowActionsProps {
  mode: Mode;
  source: Source;
  onEdit: (source: Source) => void;
  onVerify: (id: string) => void;
  onCompliance: (source: Source) => void;
  onArchive: (id: string) => void;
  onRestore: (id: string) => void;
  onDelete: (id: string) => void;
}

/**
 * 행 오른쪽의 "더보기" 드롭다운 메뉴.
 * active/archived 모드에 따라 보관/복구 메뉴가 교체된다.
 *
 * a11y: 트리거 버튼에 소스명을 포함한 aria-label 을 달아서 같은 페이지에
 * 여러 드롭다운이 렌더될 때 스크린리더가 구분 가능하도록 한다.
 */
export function SourceRowActions({
  mode,
  source,
  onEdit,
  onVerify,
  onCompliance,
  onArchive,
  onRestore,
  onDelete
}: SourceRowActionsProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="sm" variant="ghost" className="h-7 w-7 p-0" aria-label={`${source.name} 작업 메뉴`}>
          <MoreHorizontal size={14} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => onEdit(source)}>편집</DropdownMenuItem>
        <DropdownMenuItem onClick={() => onCompliance(source)}>저작권</DropdownMenuItem>
        <DropdownMenuItem onClick={() => onVerify(source.id)}>연결 확인</DropdownMenuItem>
        <DropdownMenuSeparator />
        {mode === "active" ? (
          <DropdownMenuItem onClick={() => onArchive(source.id)}>보관</DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => onRestore(source.id)}>복구</DropdownMenuItem>
        )}
        <DropdownMenuItem className="text-destructive" onClick={() => onDelete(source.id)}>
          삭제
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
