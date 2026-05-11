-- V126__create_category_organizations.sql
-- Phase 3 PR2: Category ↔ Organization many-to-many.
CREATE TABLE category_organizations (
    category_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id, organization_id),
    CONSTRAINT fk_cat_orgs_category FOREIGN KEY (category_id) REFERENCES batch_categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_orgs_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT uq_cat_orgs_tenant UNIQUE (tenant_id, category_id, organization_id)
);

CREATE INDEX idx_cat_orgs_organization ON category_organizations(organization_id);
CREATE INDEX idx_cat_orgs_tenant ON category_organizations(tenant_id);
