-- V10: Academic year support for enrollments, attendance, and teacher assignments

CREATE TABLE academic_year (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    name        VARCHAR(20) NOT NULL,
    starts_on   DATE NOT NULL,
    ends_on     DATE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (school_id, name)
);

CREATE UNIQUE INDEX idx_one_active_year_per_school ON academic_year(school_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_academic_year_school ON academic_year(school_id);

-- Enrollments: year + lifecycle
ALTER TABLE student_section_enrollment
    ADD COLUMN academic_year_id UUID REFERENCES academic_year(id),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN ended_at TIMESTAMP;

-- Attendance: year tag
ALTER TABLE attendance
    ADD COLUMN academic_year_id UUID REFERENCES academic_year(id);

-- Teacher assignments: year-scoped
ALTER TABLE teacher_section_assignment
    ADD COLUMN academic_year_id UUID REFERENCES academic_year(id),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN ended_at TIMESTAMP;

-- Backfill one ACTIVE academic year per school (Apr–Mar naming)
INSERT INTO academic_year (id, school_id, name, starts_on, ends_on, status, created_at)
SELECT
    gen_random_uuid(),
    s.id,
    CASE
        WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4 THEN
            EXTRACT(YEAR FROM CURRENT_DATE)::text || '-' || RIGHT((EXTRACT(YEAR FROM CURRENT_DATE) + 1)::text, 2)
        ELSE
            (EXTRACT(YEAR FROM CURRENT_DATE) - 1)::text || '-' || RIGHT(EXTRACT(YEAR FROM CURRENT_DATE)::text, 2)
    END,
    CASE
        WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4 THEN
            make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 4, 1)
        ELSE
            make_date((EXTRACT(YEAR FROM CURRENT_DATE) - 1)::int, 4, 1)
    END,
    CASE
        WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4 THEN
            make_date((EXTRACT(YEAR FROM CURRENT_DATE) + 1)::int, 3, 31)
        ELSE
            make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 3, 31)
    END,
    'ACTIVE',
    NOW()
FROM school s;

UPDATE student_section_enrollment sse
SET academic_year_id = ay.id
FROM academic_year ay
WHERE ay.school_id = sse.school_id AND ay.status = 'ACTIVE';

UPDATE attendance a
SET academic_year_id = ay.id
FROM academic_year ay
WHERE ay.school_id = a.school_id AND ay.status = 'ACTIVE';

UPDATE teacher_section_assignment tsa
SET academic_year_id = ay.id
FROM academic_year ay
WHERE ay.school_id = tsa.school_id AND ay.status = 'ACTIVE';

ALTER TABLE student_section_enrollment
    ALTER COLUMN academic_year_id SET NOT NULL;

ALTER TABLE attendance
    ALTER COLUMN academic_year_id SET NOT NULL;

ALTER TABLE teacher_section_assignment
    ALTER COLUMN academic_year_id SET NOT NULL;

-- Replace uniqueness: one ACTIVE enrollment per student per year
ALTER TABLE student_section_enrollment
    DROP CONSTRAINT IF EXISTS student_section_enrollment_student_id_section_id_key;

CREATE UNIQUE INDEX idx_active_enrollment_per_student_year
    ON student_section_enrollment(student_id, academic_year_id)
    WHERE status = 'ACTIVE';

-- One ACTIVE teacher assignment per teacher+section+year
ALTER TABLE teacher_section_assignment
    DROP CONSTRAINT IF EXISTS teacher_section_assignment_teacher_id_section_id_key;

CREATE UNIQUE INDEX idx_active_teacher_assignment_per_year
    ON teacher_section_assignment(teacher_id, section_id, academic_year_id)
    WHERE status = 'ACTIVE';

-- RLS
ALTER TABLE academic_year ENABLE ROW LEVEL SECURITY;

CREATE POLICY academic_year_tenant_isolation ON academic_year
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );
