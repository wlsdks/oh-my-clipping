import { useState, useEffect, useRef } from "react";

interface UseReviewSelectionProps {
  itemIds: string[];
  onAction: (summaryId: string, action: "approve" | "exclude") => void;
  autoSelect?: boolean;
  getSourceLink?: (id: string) => string | undefined;
  onSpaceToggle?: (id: string) => void;
}

interface UseReviewSelectionReturn {
  selectedId: string | null;
  setSelectedId: (id: string | null) => void;
  selectNext: () => void;
}

export function useReviewSelection({
  itemIds,
  onAction,
  autoSelect = true,
  getSourceLink,
  onSpaceToggle,
}: UseReviewSelectionProps): UseReviewSelectionReturn {
  const [selectedId, setSelectedId] = useState<string | null>(
    autoSelect && itemIds.length > 0 ? itemIds[0] : null
  );
  const itemIdsRef = useRef(itemIds);
  itemIdsRef.current = itemIds;

  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  const getSourceLinkRef = useRef(getSourceLink);
  getSourceLinkRef.current = getSourceLink;

  const onSpaceToggleRef = useRef(onSpaceToggle);
  onSpaceToggleRef.current = onSpaceToggle;

  // 선택된 항목이 목록에서 사라지면 선택 해제
  useEffect(() => {
    if (itemIds.length === 0) {
      setSelectedId(null);
    } else if (selectedId && !itemIds.includes(selectedId)) {
      setSelectedId(autoSelect ? itemIds[0] : null);
    }
  }, [itemIds, autoSelect, selectedId]);

  const selectNext = () => {
    const ids = itemIdsRef.current;
    const current = selectedIdRef.current;
    if (!current || ids.length === 0) return;
    const idx = ids.indexOf(current);
    if (idx < ids.length - 1) {
      setSelectedId(ids[idx + 1]);
    }
  };

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // 입력 필드 포커스 시 단축키 비활성화
      const tag = (document.activeElement?.tagName ?? "").toLowerCase();
      if (tag === "input" || tag === "textarea" || tag === "select") return;

      const ids = itemIdsRef.current;
      const current = selectedIdRef.current;

      switch (e.key) {
        case "ArrowDown":
        case "j": {
          e.preventDefault();
          if (ids.length === 0) return;
          // 함수형 업데이트로 최신 상태 기반 탐색 보장
          setSelectedId((prev) => {
            const idx = prev ? ids.indexOf(prev) : -1;
            if (idx < ids.length - 1) return ids[idx + 1];
            return prev;
          });
          break;
        }
        case "ArrowUp":
        case "k": {
          e.preventDefault();
          // 함수형 업데이트로 최신 상태 기반 탐색 보장
          setSelectedId((prev) => {
            const idx = prev ? ids.indexOf(prev) : -1;
            if (idx > 0) return ids[idx - 1];
            return prev;
          });
          break;
        }
        case "s": {
          if (current) onAction(current, "approve");
          break;
        }
        case "x": {
          if (current) onAction(current, "exclude");
          break;
        }
        case "o": {
          if (current && getSourceLinkRef.current) {
            const link = getSourceLinkRef.current(current);
            if (link) window.open(link, "_blank", "noopener,noreferrer");
          }
          break;
        }
        case " ": { // Space
          const active = document.activeElement;
          const isInteractive = active instanceof HTMLInputElement
            || active instanceof HTMLButtonElement
            || active instanceof HTMLSelectElement
            || active instanceof HTMLTextAreaElement;
          if (!isInteractive && current) {
            e.preventDefault();
            onSpaceToggleRef.current?.(current);
          }
          break;
        }
        case "Escape": {
          setSelectedId(null);
          break;
        }
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onAction]);

  return { selectedId, setSelectedId, selectNext };
}
