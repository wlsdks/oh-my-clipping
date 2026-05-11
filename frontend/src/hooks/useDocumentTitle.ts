import { useEffect } from "react";
import { useMatches } from "react-router-dom";

interface RouteHandle {
  title?: string;
}

export function useDocumentTitle() {
  const matches = useMatches();
  useEffect(() => {
    const match = [...matches].reverse().find((m) => (m.handle as RouteHandle)?.title);
    if (match) {
      document.title = `${(match.handle as RouteHandle).title} | Clipping`;
    }
  }, [matches]);
}
