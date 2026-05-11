-- V125__create_organizations.sql
-- Phase 3 PR2: 경쟁사/고객사/파트너 등 외부 조직 엔티티.
-- tenant_id: 향후 다중 테넌트 migration 대비 기본값 'default'.
CREATE TABLE organizations (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'default',
    name VARCHAR(200) NOT NULL,
    type VARCHAR(32) NOT NULL,
    domain VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_organizations_type CHECK (type IN ('COMPETITOR', 'CUSTOMER', 'PARTNER', 'OTHER')),
    CONSTRAINT uq_organizations_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_organizations_tenant_type ON organizations(tenant_id, type);
CREATE INDEX idx_organizations_domain ON organizations(domain);
