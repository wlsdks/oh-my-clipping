import { useState, useEffect } from "react";
import { Input } from "@/components/ui/input";
import { Search, X } from "lucide-react";

interface MemberSearchBarProps {
  value: string;
  onChange: (value: string) => void;
}

export function MemberSearchBar({ value, onChange }: MemberSearchBarProps) {
  const [localValue, setLocalValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => onChange(localValue), 300);
    return () => clearTimeout(timer);
  }, [localValue, onChange]);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  return (
    <div className="relative w-72">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
      <Input value={localValue} onChange={(e) => setLocalValue(e.target.value)} placeholder="이름, 아이디, 부서 검색…" className="pl-9 pr-8" />
      {localValue && (
        <button type="button" onClick={() => setLocalValue("")} className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground" aria-label="검색어 초기화">
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
