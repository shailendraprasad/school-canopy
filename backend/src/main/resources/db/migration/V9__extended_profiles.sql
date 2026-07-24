-- =============================================================================
-- V9: Extended profiles for School, Teacher, and Office Staff
-- =============================================================================

-- School extended fields
ALTER TABLE school ADD COLUMN board_affiliation VARCHAR(30);
ALTER TABLE school ADD COLUMN udise_code VARCHAR(15);
ALTER TABLE school ADD COLUMN school_type VARCHAR(20);
ALTER TABLE school ADD COLUMN medium_of_instruction VARCHAR(30);
ALTER TABLE school ADD COLUMN founded_year INTEGER;
ALTER TABLE school ADD COLUMN city VARCHAR(100);
ALTER TABLE school ADD COLUMN state VARCHAR(50);
ALTER TABLE school ADD COLUMN pin_code VARCHAR(6);
ALTER TABLE school ADD COLUMN principal_name VARCHAR(100);
ALTER TABLE school ADD COLUMN principal_phone VARCHAR(20);
ALTER TABLE school ADD COLUMN website VARCHAR(200);

-- Teacher/Staff extended fields on user_account
ALTER TABLE user_account ADD COLUMN phone VARCHAR(20);
ALTER TABLE user_account ADD COLUMN gender VARCHAR(10);
ALTER TABLE user_account ADD COLUMN date_of_birth DATE;
ALTER TABLE user_account ADD COLUMN qualification VARCHAR(100);
ALTER TABLE user_account ADD COLUMN specialization VARCHAR(100);
ALTER TABLE user_account ADD COLUMN employee_id VARCHAR(30);
ALTER TABLE user_account ADD COLUMN date_of_joining DATE;
ALTER TABLE user_account ADD COLUMN experience_years INTEGER;
ALTER TABLE user_account ADD COLUMN aadhaar_last4 VARCHAR(4);
ALTER TABLE user_account ADD COLUMN emergency_contact VARCHAR(20);
ALTER TABLE user_account ADD COLUMN address TEXT;
ALTER TABLE user_account ADD COLUMN designation VARCHAR(50);
