-- =============================================================================
-- V6: Add extended student fields and relationship type on parent link
-- =============================================================================

ALTER TABLE student ADD COLUMN first_name VARCHAR(50);
ALTER TABLE student ADD COLUMN last_name VARCHAR(50);
ALTER TABLE student ADD COLUMN address TEXT;
ALTER TABLE student ADD COLUMN parent_contact VARCHAR(20);
ALTER TABLE student ADD COLUMN parent_email VARCHAR(255);
ALTER TABLE student ADD COLUMN blood_group VARCHAR(5);

-- Add relationship type to parent_student_link (MOTHER, FATHER, GUARDIAN)
ALTER TABLE parent_student_link ADD COLUMN relationship VARCHAR(20) DEFAULT 'GUARDIAN';

-- Migrate existing "name" data into first_name (best-effort split)
UPDATE student SET first_name = split_part(name, ' ', 1),
                   last_name = CASE 
                     WHEN position(' ' in name) > 0 THEN substring(name from position(' ' in name) + 1)
                     ELSE ''
                   END
WHERE first_name IS NULL;
