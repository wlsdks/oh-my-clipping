import type { Source } from "@/types/source";
import { LEGAL_BASIS_OPTIONS } from "./constants";

export interface SourceDraft {
  name: string;
  url: string;
  sourceRegion: "GLOBAL" | "DOMESTIC" | "UNKNOWN" | "";
  categoryId: string;
  legalBasis: string;
  summaryAllowed: boolean;
  reviewNotes: string;
  isActive: boolean;
  updatedAt: string;
}

export function createEmptySourceDraft() {
  return {
    name: "",
    url: "",
    sourceRegion: "",
    categoryId: "",
    legalBasis: LEGAL_BASIS_OPTIONS[0].value,
    summaryAllowed: true,
    reviewNotes: "",
    isActive: true,
    updatedAt: ""
  } satisfies SourceDraft;
}

export function toSourceDraft(source: Source): SourceDraft {
  return {
    name: source.name,
    url: source.url,
    sourceRegion: source.sourceRegion,
    categoryId: source.categoryId,
    legalBasis: source.legalBasis,
    summaryAllowed: source.summaryAllowed,
    reviewNotes: source.reviewNotes || "",
    isActive: source.isActive,
    updatedAt: source.updatedAt
  };
}
