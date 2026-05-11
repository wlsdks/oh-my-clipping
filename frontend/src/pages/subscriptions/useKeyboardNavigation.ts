import { useEffect } from "react";

/**
 * 구독 목록의 키보드 네비게이션을 처리하는 훅.
 *
 * - ArrowDown/Up: 포커스 인덱스 이동
 * - Enter: 현재 포커스된 항목 선택 (패널 열기)
 * - Escape: 열린 패널 닫기
 * - Space: 카테고리 탭에서 체크박스 토글
 *
 * @param options.isRequestFilter - 현재 요청 필터(pending/rejected/withdrawn)가 활성화되어 있는지
 * @param options.displayedItems - 현재 화면에 표시 중인 항목 배열
 * @param options.focusedIndex - 현재 키보드 포커스 인덱스
 * @param options.isPanelOpen - 사이드 패널이 열려 있는지
 * @param options.onFocusChange - 포커스 인덱스 변경 핸들러
 * @param options.onSelectItem - 항목 선택(패널 열기) 핸들러
 * @param options.onClosePanel - 패널 닫기 핸들러
 * @param options.onToggleSelect - 체크박스 토글 핸들러 (카테고리 탭에서만 사용)
 */
export function useKeyboardNavigation({
  isRequestFilter,
  displayedItems,
  focusedIndex,
  isPanelOpen,
  onFocusChange,
  onSelectItem,
  onClosePanel,
  onToggleSelect,
}: {
  isRequestFilter: boolean;
  displayedItems: { id: string }[];
  focusedIndex: number;
  isPanelOpen: boolean;
  onFocusChange: (updater: (prev: number) => number) => void;
  onSelectItem: (index: number) => void;
  onClosePanel: () => void;
  onToggleSelect?: (id: string) => void;
}) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // 입력 요소에 포커스되어 있으면 무시
      const target = e.target as HTMLElement;
      if (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT" ||
        target.isContentEditable
      ) {
        return;
      }

      const maxIndex = displayedItems.length - 1;

      switch (e.key) {
        case "ArrowDown": {
          e.preventDefault();
          onFocusChange((prev) => Math.min(prev + 1, maxIndex));
          break;
        }
        case "ArrowUp": {
          e.preventDefault();
          onFocusChange((prev) => Math.max(prev - 1, 0));
          break;
        }
        case "Enter": {
          if (focusedIndex >= 0 && focusedIndex <= maxIndex) {
            e.preventDefault();
            onSelectItem(focusedIndex);
          }
          break;
        }
        case "Escape": {
          if (isPanelOpen) {
            e.preventDefault();
            onClosePanel();
          }
          break;
        }
        case " ": {
          // Space로 체크박스 토글 — 카테고리 탭에서만 동작
          if (!isRequestFilter && focusedIndex >= 0 && focusedIndex <= maxIndex && onToggleSelect) {
            e.preventDefault();
            onToggleSelect(displayedItems[focusedIndex].id);
          }
          break;
        }
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isRequestFilter, displayedItems, focusedIndex, isPanelOpen, onFocusChange, onSelectItem, onClosePanel, onToggleSelect]);
}
