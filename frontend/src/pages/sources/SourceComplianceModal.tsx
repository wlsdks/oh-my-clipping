import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { AlertTriangle } from "lucide-react";
import { sourceKeys } from "@/queries/sourceKeys";
import { sourceService } from "@/services/sourceService";
import type { Source } from "@/types/source";
import { formatKoreanDateTime } from "@/utils/date";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";

const LEGAL_BASIS_OPTIONS = [
  { value: "QUOTATION_ONLY", label: "인용만", desc: "뉴스 헤드라인·요약 수준의 인용 (최소 허용 범위)" },
  { value: "OPEN_LICENSE", label: "오픈 라이선스", desc: "CC, Public Domain 등 자유 사용 가능" },
  { value: "LICENSED", label: "라이선스 계약", desc: "콘텐츠 이용 계약 체결됨" },
  { value: "PROHIBITED", label: "사용 금지", desc: "저작권 사유로 수집·요약이 금지된 소스" }
];

interface Props {
  open: boolean;
  source: Source | null;
  onClose: () => void;
}

export function SourceComplianceModal({ open, source, onClose }: Props) {
  const qc = useQueryClient();
  const [legalBasis, setLegalBasis] = useState("QUOTATION_ONLY");
  const [summaryAllowed, setSummaryAllowed] = useState(true);
  const [reviewNotes, setReviewNotes] = useState("");

  const { data: compliance, isLoading } = useQuery({
    queryKey: sourceKeys.compliance(source?.id ?? ""),
    queryFn: () => sourceService.getCompliance(source!.id),
    enabled: open && source !== null
  });

  useEffect(() => {
    if (compliance) {
      setLegalBasis(compliance.legalBasis ?? "QUOTATION_ONLY");
      setSummaryAllowed(compliance.summaryAllowed);
      setReviewNotes(compliance.reviewNotes ?? "");
    }
  }, [compliance]);

  const { mutate: save, isPending } = useMutation({
    mutationFn: () =>
      sourceService.updateCompliance(source!.id, {
        legalBasis,
        summaryAllowed,
        fulltextAllowed: false,
        reviewNotes: reviewNotes || null
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: sourceKeys.compliance(source!.id) });
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success("저작권 정보를 저장했어요");
      onClose();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "저장하지 못했어요"))
  });

  if (!source) return null;

  const selectedBasisDesc = LEGAL_BASIS_OPTIONS.find((o) => o.value === legalBasis)?.desc;

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o && !isPending) onClose();
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>저작권 관리</DialogTitle>
          <DialogDescription className="sr-only">저작권 정보를 확인하고 수정합니다</DialogDescription>
        </DialogHeader>

        <p className="text-sm text-muted-foreground -mt-2">{source.name}</p>

        {isLoading ? (
          <div className="py-8 text-center text-sm text-muted-foreground">불러오는 중...</div>
        ) : (
          <div className="space-y-5 py-2">
            {/* 요약 허용 — 가장 중요한 제어 */}
            <div className="flex items-center justify-between rounded-2xl border border-border p-3">
              <div>
                <Label>요약 허용</Label>
                <p className="text-xs text-muted-foreground mt-0.5">
                  끄면 이 채널의 뉴스 수집·요약이 <strong>즉시 중단</strong>돼요
                </p>
              </div>
              <Switch checked={summaryAllowed} onCheckedChange={setSummaryAllowed} disabled={isPending} />
            </div>

            {!summaryAllowed && (
              <div className="flex items-start gap-1.5 px-2.5 py-1.5 rounded-lg bg-[var(--status-warning-bg)] text-xs text-[var(--status-warning-text)]">
                <AlertTriangle size={12} className="mt-0.5 shrink-0" />
                <span>요약이 비허용 상태이에요 이 채널의 뉴스는 수집되지 않아요.</span>
              </div>
            )}

            {/* 법적 근거 */}
            <div className="space-y-2">
              <Label>법적 근거</Label>
              <Select value={legalBasis} onValueChange={setLegalBasis} disabled={isPending}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {LEGAL_BASIS_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {selectedBasisDesc && <p className="text-xs text-muted-foreground">{selectedBasisDesc}</p>}
            </div>

            {/* 검토 메모 */}
            <div className="space-y-2">
              <Label htmlFor="review-notes">검토 메모</Label>
              <Textarea
                id="review-notes"
                rows={3}
                value={reviewNotes}
                onChange={(e) => setReviewNotes(e.target.value)}
                placeholder="예: robots.txt 확인 완료, 2026-03-15 이용약관 검토"
                disabled={isPending}
              />
            </div>

            {compliance?.termsReviewedAt && (
              <p className="text-xs text-muted-foreground">
                마지막 검토: {formatKoreanDateTime(compliance.termsReviewedAt)}
              </p>
            )}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            취소
          </Button>
          <Button onClick={() => save()} disabled={isPending || isLoading}>
            {isPending ? "저장 중..." : "저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
