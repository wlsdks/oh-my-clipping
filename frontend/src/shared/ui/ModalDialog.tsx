import { isValidElement, type PropsWithChildren, type ReactNode } from "react";
import * as Dialog from "@radix-ui/react-dialog";

interface ModalDialogProps extends PropsWithChildren {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title?: ReactNode;
  ariaLabel?: string;
  className?: string;
  overlayClassName?: string;
  disableClose?: boolean;
  showCloseButton?: boolean;
  closeLabel?: string;
  closeText?: ReactNode;
}

/**
 * 공통 모달 프레임입니다.
 *
 * Radix Dialog의 접근성/포커스 관리를 사용하면서
 * 기존 전역 CSS 클래스(`modal-backdrop`, `modal-card`)를 그대로 재사용합니다.
 */
export function ModalDialog({
  open,
  onOpenChange,
  title,
  ariaLabel,
  className = "modal-card",
  overlayClassName = "modal-backdrop",
  disableClose = false,
  showCloseButton = false,
  closeLabel = "닫기",
  closeText = "×",
  children
}: ModalDialogProps) {
  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen && disableClose) return;
    onOpenChange(nextOpen);
  }

  function renderTitle() {
    if (!title) return null;
    if (isValidElement(title)) {
      return <Dialog.Title asChild>{title}</Dialog.Title>;
    }
    return (
      <Dialog.Title asChild>
        <h3>{title}</h3>
      </Dialog.Title>
    );
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay asChild>
          <div className={overlayClassName}>
            <Dialog.Content
              className={className}
              aria-label={ariaLabel}
              onEscapeKeyDown={(event) => {
                if (disableClose) event.preventDefault();
              }}
              onPointerDownOutside={(event) => {
                if (disableClose) event.preventDefault();
              }}
              onInteractOutside={(event) => {
                if (disableClose) event.preventDefault();
              }}
            >
              {title || showCloseButton ? (
                <div className={`modal-title-row${showCloseButton ? " modal-title-row-with-close" : ""}`}>
                  <div>{renderTitle()}</div>
                  {showCloseButton ? (
                    <Dialog.Close asChild>
                      <button
                        className="btn btn-ghost modal-close-button"
                        type="button"
                        disabled={disableClose}
                        aria-label={closeLabel}
                      >
                        {closeText}
                      </button>
                    </Dialog.Close>
                  ) : null}
                </div>
              ) : null}
              {children}
            </Dialog.Content>
          </div>
        </Dialog.Overlay>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
