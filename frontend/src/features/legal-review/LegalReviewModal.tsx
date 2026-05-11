import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  findLegalBasisOption,
  type LegalBasis,
} from "./model/legalBasisOptions";

export interface LegalReviewFormData {
  legalBasis: LegalBasis;
  summaryAllowed: boolean;
  fulltextAllowed: boolean;
  reviewNotes: string | null;
  responsibilityAcknowledged: boolean;
}

interface ApproveRequestProps {
  mode: "approve-request";
  open: boolean;
  onClose: () => void;
  request: {
    id: string;
    sourceName: string;
    sourceUrl: string;
    requesterEmail: string;
    createdAt: string;
  };
  onConfirm: (data: LegalReviewFormData) => void;
  isPending?: boolean;
}

interface CreateSourceProps {
  mode: "create-source";
  open: boolean;
  onClose: () => void;
  categoryId: string;
  onConfirm: (
    sourceData: { name: string; url: string },
    legalData: LegalReviewFormData,
  ) => void;
  isPending?: boolean;
}

type LegalReviewModalProps = ApproveRequestProps | CreateSourceProps;

const MAX_NOTES = 200;

/**
 * 법적 검토 모달.
 * approve-request 모드: 사용자 신청 승인
 * create-source 모드: 관리자 직접 추가
 */
export function LegalReviewModal(props: LegalReviewModalProps) {
  const legalBasis: LegalBasis = "QUOTATION_ONLY";
  const policy = findLegalBasisOption("QUOTATION_ONLY").defaultPolicy;
  const reviewNotes = "";
  const acknowledged = true;

  // create-source 모드용 필드
  const [sourceName, setSourceName] = useState("");
  const [sourceUrl, setSourceUrl] = useState("");

  const formData: LegalReviewFormData = {
    legalBasis,
    summaryAllowed: policy.summaryAllowed,
    fulltextAllowed: policy.fulltextAllowed,
    reviewNotes: reviewNotes.trim() || null,
    responsibilityAcknowledged: acknowledged,
  };

  const sourceFieldsValid =
    props.mode === "approve-request" ||
    (sourceName.trim().length > 0 && sourceUrl.trim().length > 0);

  const canConfirm =
    acknowledged && reviewNotes.length <= MAX_NOTES && sourceFieldsValid;

  function handleConfirm() {
    if (!canConfirm) return;
    if (props.mode === "approve-request") {
      props.onConfirm(formData);
    } else {
      props.onConfirm(
        { name: sourceName.trim(), url: sourceUrl.trim() },
        formData,
      );
    }
  }

  return (
    <Dialog open={props.open} onOpenChange={(open) => !open && props.onClose()}>
      <DialogContent className="max-w-[520px] max-h-[85vh] overflow-y-auto overflow-x-hidden">
        <DialogHeader>
          <DialogTitle>
            {props.mode === "approve-request"
              ? "구독 승인"
              : "RSS 소스 직접 추가"}
          </DialogTitle>
          <DialogDescription className="sr-only">법적 근거를 검토하고 승인 여부를 결정합니다</DialogDescription>
        </DialogHeader>

        {/* 소스 정보 (모드별 분기) */}
        {props.mode === "approve-request" ? (
          <div className="rounded-lg bg-muted/30 p-3 space-y-1 text-sm">
            <div className="font-semibold">📰 {props.request.sourceName}</div>
            <div className="text-xs text-muted-foreground break-all">
              🔗 {props.request.sourceUrl}
            </div>
            <div className="text-xs text-muted-foreground">
              👤 {props.request.requesterEmail}
            </div>
          </div>
        ) : (
          <div className="space-y-2">
            <input
              type="text"
              placeholder="소스 이름"
              value={sourceName}
              onChange={(e) => setSourceName(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg"
            />
            <input
              type="url"
              placeholder="https://example.com/rss"
              value={sourceUrl}
              onChange={(e) => setSourceUrl(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg"
            />
          </div>
        )}

        {/* 법적 근거: 인용만 자동 적용 */}
        <div className="rounded-lg bg-muted/30 p-3">
          <p className="text-xs text-muted-foreground">
            📋 법적 근거: <span className="font-medium text-foreground">인용만</span> — AI 요약만 생성하고 원문은 저장하지 않습니다
          </p>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={props.onClose}
            disabled={props.isPending}
          >
            취소
          </Button>
          <Button onClick={handleConfirm} disabled={!canConfirm || props.isPending}>
            {props.mode === "approve-request" ? "승인" : "등록"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
