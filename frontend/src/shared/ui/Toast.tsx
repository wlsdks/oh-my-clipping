import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Check } from "lucide-react";

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface ToastProps {
  text: string;
  icon?: ReactNode;
  duration?: number;
  action?: ToastAction;
  onDismiss: () => void;
}

const DEFAULT_ICON = <Check className="h-3.5 w-3.5" />;

export function Toast({ text, icon = DEFAULT_ICON, duration = 2500, action, onDismiss }: ToastProps) {
  const [leaving, setLeaving] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function dismiss() {
    setLeaving(true);
    setTimeout(onDismiss, 250);
  }

  useEffect(() => {
    timerRef.current = setTimeout(dismiss, duration);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [text, duration]); // dismiss captures stable state setters — safe to omit

  return (
    <div className={`toast${leaving ? " toast-leaving" : ""}`} role="status" aria-live="polite">
      <span className="toast-icon">{icon}</span>
      <span className="toast-text">{text}</span>
      {action && (
        <button
          className="toast-action"
          type="button"
          onClick={() => {
            action.onClick();
            dismiss();
          }}
        >
          {action.label}
        </button>
      )}
      <button className="toast-close" type="button" onClick={dismiss} aria-label="닫기">
        ×
      </button>
    </div>
  );
}
