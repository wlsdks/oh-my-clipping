ALTER TABLE batch_categories ADD COLUMN max_items INT NOT NULL DEFAULT 5;
ALTER TABLE batch_categories ADD COLUMN persona_id VARCHAR(36);
