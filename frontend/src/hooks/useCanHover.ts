import { useMediaQuery } from "./useMediaQuery";

/**
 * 포인터가 hover 가능한 디바이스인지 감지한다.
 * 터치/모바일처럼 hover 개념이 없는 환경에서는 false 를 반환해,
 * hover 전용 UI(툴팁 등) 활성 여부를 판단하는 데 쓴다.
 */
export function useCanHover(): boolean {
  return useMediaQuery("(hover: hover)");
}
