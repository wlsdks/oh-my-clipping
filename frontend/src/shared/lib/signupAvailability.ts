import { useEffect, useState } from "react";
import { requestJson } from "../api/httpClient";
import type { SignupAvailabilityResponse } from "../types/auth";

interface SignupAvailabilityState {
  loading: boolean;
  data: SignupAvailabilityResponse | null;
  error: string | null;
}

export function useSignupAvailability(scope: "admin" | "user" = "admin"): SignupAvailabilityState {
  const [state, setState] = useState<SignupAvailabilityState>({
    loading: true,
    data: null,
    error: null
  });

  useEffect(() => {
    let mounted = true;

    const endpoint =
      scope === "admin" ? "/api/public/admin/auth/signup-availability" : "/api/public/user/auth/signup-availability";

    requestJson<SignupAvailabilityResponse>(endpoint)
      .then((data) => {
        if (!mounted) return;
        setState({ loading: false, data, error: null });
      })
      .catch(() => {
        if (!mounted) return;
        setState({ loading: false, data: null, error: "가입 가능 여부를 확인하지 못했어요" });
      });

    return () => {
      mounted = false;
    };
  }, [scope]);

  return state;
}
