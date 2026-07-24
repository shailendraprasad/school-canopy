-- =============================================================================
-- V8: School Branding (custom color + logo)
-- =============================================================================

ALTER TABLE school ADD COLUMN brand_color VARCHAR(7) DEFAULT '#4a6b8a';
ALTER TABLE school ADD COLUMN logo_url VARCHAR(500);
