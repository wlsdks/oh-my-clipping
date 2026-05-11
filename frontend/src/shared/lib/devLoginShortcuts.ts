import { useEffect, useState } from "react";
import { ApiError, requestJson } from "../api/httpClient";
import type { DevLoginShortcutsEnvelope } from "../types/auth";

interface DevLoginShortcutState {
  loading: boolean;
  data: DevLoginShortcutsEnvelope | null;
}

export function useDevLoginShortcuts(): DevLoginShortcutState {
  const [state, setState] = useState<DevLoginShortcutState>({
    loading: true,
    data: null
  });

  useEffect(() => {
    let mounted = true;

    requestJson<DevLoginShortcutsEnvelope>("/api/public/dev/login-shortcuts")
      .then((data) => {
        if (!mounted) return;
        setState({ loading: false, data });
      })
      .catch((error: Error) => {
        if (!mounted) return;
        if (error instanceof ApiError && error.status === 404) {
          setState({ loading: false, data: null });
          return;
        }
        setState({ loading: false, data: null });
      });

    return () => {
      mounted = false;
    };
  }, []);

  return state;
}
