import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { userService } from "@/services/userService";
import { userFriendlyMessage } from "@/shared/lib/httpError";

interface DataExportDialogProps {
  open: boolean;
  onClose: () => void;
}

type ExportFormat = "json" | "csv";

/**
 * 사용자 본인 개인정보 export 다이얼로그.
 * 개인정보보호법 제35조(개인정보의 열람)에 따라 본인이 직접 JSON/CSV 로 내려받을 수 있다.
 * 일일 3회 한도가 있으며, 초과 시 서버가 429 를 반환한다.
 */
export function DataExportDialog({ open, onClose }: DataExportDialogProps) {
  const [format, setFormat] = useState<ExportFormat>("json");

  const { mutate: download, isPending } = useMutation({
    mutationFn: async (selectedFormat: ExportFormat) => {
      const { blob, filename } = await userService.downloadPersonalData(selectedFormat);
      triggerBrowserDownload(blob, filename);
    },
    onSuccess: () => {
      toast.success("개인정보 파일을 다운로드했어요");
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "다운로드에 실패했어요. 잠시 후 다시 시도해 주세요"));
    }
  });

  function handleOpenChange(isOpen: boolean) {
    if (!isOpen && !isPending) onClose();
  }

  function handleDownload() {
    download(format);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>개인정보 내려받기</DialogTitle>
          <DialogDescription className="sr-only">
            본인 개인정보를 파일로 다운로드합니다
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground leading-relaxed">
            개인정보보호법 제35조(개인정보의 열람)에 따라 본인의 계정, 구독, 북마크, 최근 행동 기록 등을 파일로
            받아볼 수 있어요.
          </p>

          <div className="rounded-xl border border-border bg-muted/40 p-3 text-xs text-muted-foreground leading-relaxed">
            <strong className="text-foreground">개인 보안을 위해 안내드려요</strong>
            <ul className="mt-1.5 list-disc space-y-1 pl-4">
              <li>비밀번호, 토큰, 인증 비밀 정보는 포함되지 않아요.</li>
              <li>하루 최대 3회까지 받을 수 있어요.</li>
              <li>다운로드 기록은 감사 로그에 남아요.</li>
            </ul>
          </div>

          <div className="space-y-2">
            <Label>파일 형식</Label>
            <div className="grid grid-cols-2 gap-2">
              <FormatOption
                active={format === "json"}
                label="JSON"
                description="개발 도구와 호환"
                onClick={() => setFormat("json")}
                disabled={isPending}
              />
              <FormatOption
                active={format === "csv"}
                label="CSV"
                description="엑셀로 열어보기"
                onClick={() => setFormat("csv")}
                disabled={isPending}
              />
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            취소
          </Button>
          <Button onClick={handleDownload} disabled={isPending}>
            {isPending ? "준비 중..." : "다운로드"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface FormatOptionProps {
  active: boolean;
  label: string;
  description: string;
  onClick: () => void;
  disabled: boolean;
}

function FormatOption({ active, label, description, onClick, disabled }: FormatOptionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={[
        "flex flex-col items-start gap-0.5 rounded-xl border px-3 py-2.5 text-left transition",
        "disabled:opacity-60 disabled:cursor-not-allowed",
        active
          ? "border-primary bg-primary/10 text-foreground"
          : "border-border bg-background hover:border-primary/60"
      ].join(" ")}
    >
      <span className="text-sm font-medium">{label}</span>
      <span className="text-xs text-muted-foreground">{description}</span>
    </button>
  );
}

/**
 * Blob 을 받아 브라우저가 파일 다운로드 UI 를 띄우도록 트리거한다.
 * `URL.createObjectURL` 로 임시 URL 을 만든 뒤, 숨겨진 anchor 에 click 을 전달한다.
 */
function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  // createObjectURL 로 만든 URL 은 수동으로 해제해 누수를 방지한다.
  URL.revokeObjectURL(url);
}
