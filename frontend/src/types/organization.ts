/**
 * Phase 3 PR2: 외부 조직 엔티티 (경쟁사/고객사/파트너 등).
 * 백엔드 `organizations.type` CHECK 제약과 일치.
 */
export type OrganizationType = "COMPETITOR" | "CUSTOMER" | "PARTNER" | "OTHER";

/** 사용자에게 노출할 type 레이블 — 드롭다운/뱃지에서 사용. */
export const ORGANIZATION_TYPE_LABELS: Record<OrganizationType, string> = {
  COMPETITOR: "경쟁사",
  CUSTOMER: "고객사",
  PARTNER: "파트너",
  OTHER: "기타",
};

/**
 * 조직 생성 경로. 백엔드 `organizations.origin` DB 값과 일치.
 * V134 신규.
 */
export type OrgOrigin =
  | "user_wizard"
  | "admin_created"
  | "competitor_mirror"
  | "backfill"
  | "legacy";

export interface Organization {
  id: string;
  tenantId: string;
  name: string;
  type: OrganizationType;
  domain: string | null;
  description: string | null;
  /** 한국 주식 종목 코드 (V134 신규). */
  stockCode: string | null;
  /** 조직 별칭 목록 (V134 신규). */
  aliases: string[];
  /** 생성 경로 (V134 신규). */
  origin: OrgOrigin | null;
  /** 연결된 카테고리 수. 목록 API 에서만 집계된다. 단건 조회 시 0. */
  usageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrganizationListResponse {
  content: Organization[];
  totalCount: number;
}

export interface CreateOrganizationRequest {
  name: string;
  type: OrganizationType;
  domain?: string | null;
  description?: string | null;
}

export interface UpdateOrganizationRequest {
  name?: string;
  type?: OrganizationType;
  domain?: string | null;
  description?: string | null;
  aliases?: string[] | null;
}

export interface SetCategoryOrganizationsRequest {
  organizationIds: string[];
}
