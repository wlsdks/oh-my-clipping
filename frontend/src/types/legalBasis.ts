export type LegalBasis = "QUOTATION_ONLY" | "OPEN_LICENSE" | "LICENSED" | "PROHIBITED";

export const LEGAL_BASIS_BADGE: Record<LegalBasis, { label: string; className: string }> = {
  QUOTATION_ONLY: { label: "인용만", className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]" },
  OPEN_LICENSE: { label: "오픈", className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]" },
  LICENSED: { label: "계약", className: "bg-primary/10 text-primary" },
  PROHIBITED: { label: "금지", className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]" },
};

export function getLegalBasisBadge(basis: string) {
  return LEGAL_BASIS_BADGE[basis as LegalBasis] ?? { label: basis, className: "bg-muted text-muted-foreground" };
}
