-- V11: Phase 3 — year-scoped calendar, subjects, subject-teacher mapping

-- Calendar events: tie to academic year
ALTER TABLE calendar_event
    ADD COLUMN academic_year_id UUID REFERENCES academic_year(id);

UPDATE calendar_event ce
SET academic_year_id = ay.id
FROM academic_year ay
WHERE ay.school_id = ce.school_id AND ay.status = 'ACTIVE';

ALTER TABLE calendar_event
    ALTER COLUMN academic_year_id SET NOT NULL;

CREATE INDEX idx_calendar_event_year ON calendar_event(academic_year_id);

-- School subjects (catalog per school)
CREATE TABLE subject (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (school_id, name)
);

CREATE INDEX idx_subject_school ON subject(school_id);

-- Subject-teacher mapping per section per academic year
CREATE TABLE subject_teacher_assignment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES school(id),
    subject_id       UUID NOT NULL REFERENCES subject(id),
    teacher_id       UUID NOT NULL REFERENCES user_account(id),
    section_id       UUID NOT NULL REFERENCES section(id),
    academic_year_id UUID NOT NULL REFERENCES academic_year(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at         TIMESTAMP
);

CREATE UNIQUE INDEX idx_active_subject_teacher_per_year
    ON subject_teacher_assignment(subject_id, section_id, academic_year_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_subject_teacher_section ON subject_teacher_assignment(section_id, academic_year_id);

-- RLS
ALTER TABLE subject ENABLE ROW LEVEL SECURITY;
ALTER TABLE subject_teacher_assignment ENABLE ROW LEVEL SECURITY;

CREATE POLICY subject_tenant_isolation ON subject
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

CREATE POLICY subject_teacher_tenant_isolation ON subject_teacher_assignment
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );
