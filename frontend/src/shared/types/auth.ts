export interface SignupAvailabilityResponse {
  allowed: boolean;
  reason: string;
  message: string;
}

export interface DevLoginShortcutResponse {
  key: string;
  label: string;
  scope: "admin" | "user" | "new-user";
  username: string;
  password: string;
  note: string;
}

export interface DevLoginShortcutsEnvelope {
  enabled: boolean;
  shortcuts: DevLoginShortcutResponse[];
}
