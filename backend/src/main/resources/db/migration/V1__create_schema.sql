-- =============================================================================
-- V1: School Canopy — Core Schema
-- =============================================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- SCHOOL
-- =============================================================================
CREATE TABLE school (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    prefix      VARCHAR(5) NOT NULL UNIQUE,
    contact_email VARCHAR(255) NOT NULL,
    address     VARCHAR(200),
    phone       VARCHAR(20),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_school_status ON school(status);
CREATE INDEX idx_school_prefix ON school(prefix);

-- =============================================================================
-- USER_ACCOUNT
-- =============================================================================
CREATE TABLE user_account (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID REFERENCES school(id),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(255),
    name                    VARCHAR(100) NOT NULL,
    role                    VARCHAR(30) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts   INT NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP,
    last_login_at           TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

CREATE INDEX idx_user_account_email ON user_account(email);
CREATE INDEX idx_user_account_school_id ON user_account(school_id);
CREATE INDEX idx_user_account_role ON user_account(role);
CREATE INDEX idx_user_account_status ON user_account(status);

-- =============================================================================
-- PLATFORM_TEAM_SCHOOL_ASSIGNMENT
-- =============================================================================
CREATE TABLE platform_team_school_assignment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id),
    school_id   UUID NOT NULL REFERENCES school(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, school_id)
);

-- =============================================================================
-- SESSION
-- =============================================================================
CREATE TABLE session (
    token           VARCHAR(64) PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES user_account(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_session_user_id ON session(user_id);
CREATE INDEX idx_session_expires_at ON session(expires_at);

-- =============================================================================
-- INVITATION
-- =============================================================================
CREATE TABLE invitation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token           VARCHAR(64) NOT NULL UNIQUE,
    target_user_id  UUID REFERENCES user_account(id),
    email           VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitation_token ON invitation(token);
CREATE INDEX idx_invitation_email ON invitation(email);

-- =============================================================================
-- CLASS
-- =============================================================================
CREATE TABLE class (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    name        VARCHAR(50) NOT NULL,
    grade_level VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_class_school_id ON class(school_id);

-- =============================================================================
-- SECTION
-- =============================================================================
CREATE TABLE section (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id    UUID NOT NULL REFERENCES class(id),
    school_id   UUID NOT NULL REFERENCES school(id),
    name        VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_section_class_id ON section(class_id);
CREATE INDEX idx_section_school_id ON section(school_id);

-- =============================================================================
-- STUDENT
-- =============================================================================
CREATE TABLE student (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  VARCHAR(20) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_student_school_id ON student(school_id);
CREATE INDEX idx_student_student_id ON student(student_id);

-- =============================================================================
-- STUDENT_SECTION_ENROLLMENT
-- =============================================================================
CREATE TABLE student_section_enrollment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID NOT NULL REFERENCES student(id),
    section_id  UUID NOT NULL REFERENCES section(id),
    school_id   UUID NOT NULL REFERENCES school(id),
    enrolled_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, section_id)
);

CREATE INDEX idx_enrollment_student ON student_section_enrollment(student_id);
CREATE INDEX idx_enrollment_section ON student_section_enrollment(section_id);

-- =============================================================================
-- TEACHER_SECTION_ASSIGNMENT
-- =============================================================================
CREATE TABLE teacher_section_assignment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id  UUID NOT NULL REFERENCES user_account(id),
    section_id  UUID NOT NULL REFERENCES section(id),
    school_id   UUID NOT NULL REFERENCES school(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(teacher_id, section_id)
);

CREATE INDEX idx_teacher_assignment_teacher ON teacher_section_assignment(teacher_id);
CREATE INDEX idx_teacher_assignment_section ON teacher_section_assignment(section_id);

-- =============================================================================
-- PARENT_STUDENT_LINK
-- =============================================================================
CREATE TABLE parent_student_link (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID NOT NULL REFERENCES user_account(id),
    student_id  UUID NOT NULL REFERENCES student(id),
    school_id   UUID NOT NULL REFERENCES school(id),
    linked_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(parent_id, student_id)
);

CREATE INDEX idx_parent_link_parent ON parent_student_link(parent_id);
CREATE INDEX idx_parent_link_student ON parent_student_link(student_id);

-- =============================================================================
-- ANNOUNCEMENT
-- =============================================================================
CREATE TABLE announcement (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    author_id   UUID NOT NULL REFERENCES user_account(id),
    title       VARCHAR(150) NOT NULL,
    body        TEXT NOT NULL,
    category    VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    scope_type  VARCHAR(20) NOT NULL,
    scope_id    UUID,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    publish_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_announcement_school ON announcement(school_id);
CREATE INDEX idx_announcement_status ON announcement(status);
CREATE INDEX idx_announcement_publish ON announcement(publish_at);

-- =============================================================================
-- CALENDAR_EVENT
-- =============================================================================
CREATE TABLE calendar_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    author_id   UUID NOT NULL REFERENCES user_account(id),
    title       VARCHAR(150) NOT NULL,
    event_date  DATE NOT NULL,
    start_time  TIME,
    end_time    TIME,
    location    VARCHAR(200),
    scope_type  VARCHAR(20) NOT NULL,
    scope_id    UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calendar_event_school ON calendar_event(school_id);
CREATE INDEX idx_calendar_event_date ON calendar_event(event_date);

-- =============================================================================
-- MESSAGE_THREAD
-- =============================================================================
CREATE TABLE message_thread (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    staff_id        UUID NOT NULL REFERENCES user_account(id),
    parent_id       UUID NOT NULL REFERENCES user_account(id),
    student_id      UUID NOT NULL REFERENCES student(id),
    subject         VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMP
);

CREATE INDEX idx_thread_school ON message_thread(school_id);
CREATE INDEX idx_thread_staff ON message_thread(staff_id);
CREATE INDEX idx_thread_parent ON message_thread(parent_id);

-- =============================================================================
-- MESSAGE
-- =============================================================================
CREATE TABLE message (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id           UUID NOT NULL REFERENCES message_thread(id),
    sender_id           UUID NOT NULL REFERENCES user_account(id),
    body                TEXT NOT NULL,
    read_by_recipient   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_message_thread ON message(thread_id);

-- =============================================================================
-- MESSAGE_ATTACHMENT
-- =============================================================================
CREATE TABLE message_attachment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL REFERENCES message(id),
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT NOT NULL,
    storage_path    VARCHAR(500) NOT NULL
);

CREATE INDEX idx_attachment_message ON message_attachment(message_id);

-- =============================================================================
-- PLATFORM_CONFIG
-- =============================================================================
CREATE TABLE platform_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  UUID REFERENCES user_account(id)
);

-- =============================================================================
-- AUDIT_LOG
-- =============================================================================
CREATE TABLE audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID REFERENCES school(id),
    action_type         VARCHAR(50) NOT NULL,
    target_entity_id    VARCHAR(100),
    performed_by        UUID NOT NULL REFERENCES user_account(id),
    details             JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_school ON audit_log(school_id);
CREATE INDEX idx_audit_log_action ON audit_log(action_type);
CREATE INDEX idx_audit_log_performed_by ON audit_log(performed_by);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);

-- =============================================================================
-- STUDENT_ID_SEQUENCE
-- =============================================================================
CREATE TABLE student_id_sequence (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    year        INT NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    UNIQUE(school_id, year)
);
