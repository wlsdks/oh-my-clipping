import { useState, useEffect } from "react";
import { Search, X } from "lucide-react";
import { Input } from "@/components/ui/input";

interface ApprovalSearchBarProps {
  value: string;
  onChange: (value: string) => void;
}

export function ApprovalSearchBar({ value, onChange }: ApprovalSearchBarProps) {
  const [local, setLocal] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => onChange(local), 300);
    return () => clearTimeout(timer);
  }, [local, onChange]);

  useEffect(() => { setLocal(value); }, [value]);

  return (
    <div className="relative w-64">
      <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        placeholder="이름, 부서로 검색"
        className="pl-9 pr-8"
      />
      {local && (
        <button
          type="button"
          onClick={() => { setLocal(""); onChange(""); }}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          aria-label="검색어 초기화"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}
