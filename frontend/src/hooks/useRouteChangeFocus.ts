import { useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";

export function useRouteChangeFocus(mainRef: React.RefObject<HTMLElement | null>) {
  const location = useLocation();
  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    mainRef.current?.focus();
  }, [location.pathname, mainRef]);
}
