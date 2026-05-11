import { Moon, Sun } from "lucide-react";
import { cn } from "@/utils/cn";
import { useUiStore } from "@/store/uiStore";

export function ThemeToggle() {
  const { theme, setTheme } = useUiStore();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isDark}
      aria-label={isDark ? "라이트 모드로 전환" : "다크 모드로 전환"}
      onClick={() => setTheme(isDark ? "light" : "dark")}
      className={cn(
        "relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors duration-200",
        isDark ? "bg-white/15" : "bg-black/10"
      )}
    >
      <span
        className={cn(
          "pointer-events-none flex h-5 w-5 items-center justify-center rounded-full bg-white shadow-sm transition-transform duration-200",
          isDark ? "translate-x-5" : "translate-x-0"
        )}
      >
        {isDark ? <Moon size={11} className="text-muted-foreground" /> : <Sun size={11} className="text-amber-500" />}
      </span>
    </button>
  );
}
