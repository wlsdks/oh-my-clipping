import { useRef, useState } from "react";
import { ModalDialog } from "./ModalDialog";
import { Banner } from "./Banner";

interface ReviewDialogProps {
  open: boolean;
  /** 승인/반려 모드 */
  mode: "approve" | "reject";
  /** 모달 제목 (예: "회원가입 승인", "요청 반려") */
  title: string;
  /** 제목 아래 안내 문구 */
  description?: string;
  /** 승인/반려 확정 시 호출 -- note 값을 전달한다 */
  onConfirm: (note: string) => void;
  /** 취소/닫기 시 호출 */
  onCancel: () => void;
  /** true이면 반려 사유 입력을 필수로 강제한다 (기본: reject 모드일 때 true) */
  requireNote?: boolean;
  /** 네트워크 요청 중 버튼 비활성화 */
  loading?: boolean;
  /** 텍스트 영역 label (기본: 모드에 따라 자동 생성) */
  noteLabel?: string;
  /** 텍스트 영역 placeholder */
  notePlaceholder?: string;
  /** 확정 버튼 텍스트 (기본: "승인 확정" / "반려 확정") */
  confirmLabel?: string;
  /** 초기 note 값 */
  defaultNote?: string;
}

/**
 * ReviewDialog 내부 폼.
 *
 * ModalDialog가 open일 때만 마운트되므로,
 * 닫힘/열림 시 자연스럽게 상태가 초기화된다.
 */
function ReviewDialogBody({
  mode,
  description,
  onConfirm,
  onCancel,
  requireNote,
  loading,
  noteLabel,
  notePlaceholder,
  confirmLabel,
  defaultNote
}: Omit<ReviewDialogProps, "open" | "title">) {
  const [note, setNote] = useState(defaultNote ?? "");
  const [error, setError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 반려 모드에서는 기본적으로 note 필수
  const isNoteRequired = requireNote ?? mode === "reject";

  function handleConfirm() {
    const trimmed = note.trim();
    if (isNoteRequired && trimmed.length === 0) {
      setError("반려 사유를 입력해주세요.");
      return;
    }
    onConfirm(trimmed);
  }

  // 자동 label/placeholder/confirmLabel 생성
  const resolvedNoteLabel = noteLabel ?? (mode === "approve" ? "메모 (선택)" : "반려 사유 (필수)");
  const resolvedPlaceholder = notePlaceholder ?? (mode === "approve" ? "예: 확인 완료" : "예: 사유를 입력해주세요.");
  const resolvedConfirmLabel = confirmLabel ?? (mode === "approve" ? "승인 확정" : "반려 확정");

  return (
    <>
      {description ? <p className="modal-caption">{description}</p> : null}

      <label className="field">
        <span>{resolvedNoteLabel}</span>
        <textarea
          ref={textareaRef}
          rows={4}
          value={note}
          onChange={(e) => {
            setNote(e.target.value);
            setError(null);
          }}
          placeholder={resolvedPlaceholder}
        />
      </label>

      {error ? <Banner type="error">{error}</Banner> : null}

      <div className="inline-row modal-actions">
        <button className="btn btn-ghost" type="button" onClick={onCancel} disabled={loading}>
          취소
        </button>
        <button
          className={mode === "approve" ? "btn btn-primary" : "btn btn-danger"}
          type="button"
          onClick={handleConfirm}
          disabled={loading}
        >
          {resolvedConfirmLabel}
        </button>
      </div>
    </>
  );
}

/**
 * 승인/반려 공통 다이얼로그.
 *
 * ModalDialog(Radix Dialog)를 내부적으로 조합하며,
 * textarea + 확인/취소 버튼을 포함한다.
 * 열릴 때마다 내부 폼(ReviewDialogBody)이 새로 마운트되어 상태가 자동 초기화된다.
 */
export function ReviewDialog({ open, title, onCancel, ...bodyProps }: ReviewDialogProps) {
  return (
    <ModalDialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onCancel();
      }}
      title={title}
      ariaLabel={title}
    >
      {open ? <ReviewDialogBody onCancel={onCancel} {...bodyProps} /> : null}
    </ModalDialog>
  );
}
