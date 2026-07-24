-- V5: Attendance tracking
CREATE TABLE attendance (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id),
    section_id  UUID NOT NULL REFERENCES section(id),
    date        DATE NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'PRESENT',  -- PRESENT, ABSENT, LATE
    marked_by   UUID NOT NULL REFERENCES user_account(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, date)
);

CREATE INDEX idx_attendance_section_date ON attendance(section_id, date);
CREATE INDEX idx_attendance_student ON attendance(student_id);
CREATE INDEX idx_attendance_school ON attendance(school_id);

-- RLS policy
ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;
CREATE POLICY attendance_tenant_isolation ON attendance
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );
