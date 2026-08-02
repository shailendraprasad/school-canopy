-- Demo seed data for local development (matches README demo accounts)
-- Password for all accounts: Admin@123456

DO $$
DECLARE
    v_school_id UUID := 'b0000000-0000-0000-0000-000000000001';
    v_year_id UUID := 'b0000000-0000-0000-0000-000000000010';
    v_class_id UUID := 'b0000000-0000-0000-0000-000000000020';
    v_section_id UUID := 'b0000000-0000-0000-0000-000000000021';
    v_admin_id UUID := 'b0000000-0000-0000-0000-000000000030';
    v_teacher_id UUID := 'b0000000-0000-0000-0000-000000000031';
    v_parent_id UUID := 'b0000000-0000-0000-0000-000000000032';
    v_student_id UUID := 'b0000000-0000-0000-0000-000000000040';
    v_hash TEXT := '$2a$12$APnn35Dg/Q0HZWknXKnMzOvVjD/izJQJAfGF8cL5E9nZ.IsGMQ24q';
BEGIN
    INSERT INTO school (id, name, prefix, contact_email, address, phone, status, brand_color, created_at)
    VALUES (v_school_id, 'Demo International School', 'DEMO', 'admin@demo.edu', '123 School Lane, Bangalore', '9876543210', 'ACTIVE', '#4a6b8a', NOW())
    ON CONFLICT (prefix) DO NOTHING;

    INSERT INTO academic_year (id, school_id, name, starts_on, ends_on, status, created_at)
    VALUES (v_year_id, v_school_id, '2025-26',
        make_date(2025, 4, 1), make_date(2026, 3, 31), 'ACTIVE', NOW())
    ON CONFLICT (school_id, name) DO NOTHING;

    INSERT INTO user_account (id, school_id, email, password_hash, name, role, status, failed_login_attempts, created_at)
    VALUES
        (v_admin_id, v_school_id, 'schooladmin@demo.edu', v_hash, 'School Admin', 'SCHOOL_ADMINISTRATOR', 'ACTIVE', 0, NOW()),
        (v_teacher_id, v_school_id, 'indumathi@demoui.edu', v_hash, 'Indumathi R', 'TEACHER', 'ACTIVE', 0, NOW()),
        (v_parent_id, v_school_id, 'shailendra@demo.edu', v_hash, 'Shailendra Kumar', 'PARENT', 'ACTIVE', 0, NOW())
    ON CONFLICT (email) DO UPDATE SET
        password_hash = EXCLUDED.password_hash,
        failed_login_attempts = 0,
        locked_until = NULL,
        status = 'ACTIVE';

    INSERT INTO "class" (id, school_id, name, grade_level, status, created_at)
    VALUES (v_class_id, v_school_id, 'Grade 5', '5', 'ACTIVE', NOW())
    ON CONFLICT DO NOTHING;

    INSERT INTO section (id, school_id, class_id, name, status, created_at)
    VALUES (v_section_id, v_school_id, v_class_id, 'A', 'ACTIVE', NOW())
    ON CONFLICT DO NOTHING;

    INSERT INTO student (id, school_id, student_id, name, first_name, last_name, parent_email, status, created_at)
    VALUES (v_student_id, v_school_id, 'DEMO-2026-0001', 'Aarav Kumar', 'Aarav', 'Kumar', 'shailendra@demo.edu', 'ACTIVE', NOW())
    ON CONFLICT DO NOTHING;

    INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, academic_year_id, status, enrolled_at)
    SELECT gen_random_uuid(), v_student_id, v_section_id, v_school_id, v_year_id, 'ACTIVE', NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM student_section_enrollment
        WHERE student_id = v_student_id AND academic_year_id = v_year_id AND status = 'ACTIVE'
    );

    INSERT INTO parent_student_link (id, parent_id, student_id, school_id, relationship, linked_at)
    SELECT gen_random_uuid(), v_parent_id, v_student_id, v_school_id, 'FATHER', NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM parent_student_link WHERE parent_id = v_parent_id AND student_id = v_student_id
    );

    INSERT INTO teacher_section_assignment (id, teacher_id, section_id, school_id, academic_year_id, status, assigned_at)
    SELECT gen_random_uuid(), v_teacher_id, v_section_id, v_school_id, v_year_id, 'ACTIVE', NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM teacher_section_assignment
        WHERE teacher_id = v_teacher_id AND section_id = v_section_id AND academic_year_id = v_year_id AND status = 'ACTIVE'
    );
END $$;
