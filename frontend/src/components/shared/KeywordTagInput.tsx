import { useState, type KeyboardEvent } from "react";

type TagColor = "include" | "exclude";

const TAG_STYLES: Record<TagColor, string> = {
  include: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border border-[var(--status-neutral-bg)]",
  exclude: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)] border border-[var(--status-danger-bg)]"
};

interface KeywordTagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  color?: TagColor;
  disabled?: boolean;
}

export function KeywordTagInput({
  tags,
  onChange,
  placeholder = "입력 후 Enter 또는 +",
  color = "include",
  disabled
}: KeywordTagInputProps) {
  const [inputValue, setInputValue] = useState("");

  function addTag() {
    const val = inputValue.trim();
    if (!val || tags.includes(val)) {
      setInputValue("");
      return;
    }
    onChange([...tags, val]);
    setInputValue("");
  }

  function removeTag(idx: number) {
    onChange(tags.filter((_, i) => i !== idx));
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      addTag();
    } else if (e.key === "Backspace" && inputValue === "" && tags.length > 0) {
      removeTag(tags.length - 1);
    }
  }

  const tagStyle = TAG_STYLES[color];

  return (
    <div
      className={`min-h-10 flex flex-wrap gap-1.5 items-center rounded-md border bg-background px-3 py-2 text-sm focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
    >
      {tags.map((tag, i) => (
        <span
          key={i}
          className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-medium ${tagStyle}`}
        >
          {tag}
          {!disabled && (
            <button
              type="button"
              className="ml-0.5 hover:opacity-70 leading-none"
              onClick={() => removeTag(i)}
              aria-label={`${tag} 삭제`}
            >
              ×
            </button>
          )}
        </span>
      ))}
      <div className="flex items-center gap-1 flex-1 min-w-20">
        <input
          className="flex-1 min-w-0 bg-transparent outline-none placeholder:text-muted-foreground text-sm disabled:cursor-not-allowed"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={tags.length === 0 ? placeholder : ""}
          disabled={disabled}
        />
        <button
          type="button"
          className="shrink-0 text-muted-foreground hover:text-foreground disabled:opacity-50"
          onClick={addTag}
          disabled={disabled || !inputValue.trim()}
          aria-label="키워드 추가"
        >
          +
        </button>
      </div>
      {tags.length > 0 && <span className="text-xs text-muted-foreground shrink-0">{tags.length}개</span>}
    </div>
  );
}
