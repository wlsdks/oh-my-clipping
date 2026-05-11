export interface Persona {
  id: string;
  name: string;
  description?: string | null;
  systemPrompt: string;
  summaryStyle?: string | null;
  targetAudience?: string | null;
  maxItems: number;
  language: string;
  isActive: boolean;
  isPreset?: boolean;
  currentVersion: number;
  previewTitle: string | null;
  previewSource: string | null;
  previewBody: string | null;
  tone: string | null;
  lengthPref: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PersonaVersionSummary {
  version: number;
  changeSummary: string | null;
  createdAt: string;
}

export interface PersonaVersionDetail {
  version: number;
  name: string;
  description: string | null;
  systemPrompt: string;
  summaryStyle: string | null;
  targetAudience: string | null;
  maxItems: number;
  language: string;
  previewTitle: string | null;
  previewSource: string | null;
  previewBody: string | null;
  changeSummary: string | null;
  createdAt: string;
}
