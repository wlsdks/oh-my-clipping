import { useEffect, useRef } from "react";
import { ModalDialog } from "./ModalDialog";

interface ConfirmModalProps {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "danger" | "default";
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmModal({
  open,
  title,
  description,
  confirmLabel = "확인",
  cancelLabel = "취소",
  variant = "default",
  onConfirm,
  onCancel
}: ConfirmModalProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) confirmRef.current?.focus();
  }, [open]);

  if (!open) return null;

  return (
    <ModalDialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onCancel();
      }}
      title={title}
      ariaLabel={typeof title === "string" ? title : "확인 모달"}
      className="confirm-dialog"
      overlayClassName="confirm-overlay"
      showCloseButton
    >
      {description ? <p className="confirm-desc">{description}</p> : null}
      <div className="confirm-actions">
        <button className="btn confirm-cancel-btn" type="button" onClick={onCancel}>
          {cancelLabel}
        </button>
        <button
          ref={confirmRef}
          className={`btn confirm-ok-btn ${variant === "danger" ? "confirm-ok-danger" : ""}`}
          type="button"
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </ModalDialog>
  );
}
