-- =============================================================================
-- V2: Row-Level Security Policies
-- =============================================================================

-- Enable RLS on all tenant-scoped tables
ALTER TABLE user_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE class ENABLE ROW LEVEL SECURITY;
ALTER TABLE section ENABLE ROW LEVEL SECURITY;
ALTER TABLE student ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_section_enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE teacher_section_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE parent_student_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcement ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE message_thread ENABLE ROW LEVEL SECURITY;
ALTER TABLE message ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- RLS Policy: user_account
CREATE POLICY user_account_tenant_isolation ON user_account
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PLATFORM_TEAM_MEMBER'
        OR school_id IS NULL
    );

-- RLS Policy: class
CREATE POLICY class_tenant_isolation ON class
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: section
CREATE POLICY section_tenant_isolation ON section
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: student
CREATE POLICY student_tenant_isolation ON student
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: student_section_enrollment
CREATE POLICY enrollment_tenant_isolation ON student_section_enrollment
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: teacher_section_assignment
CREATE POLICY teacher_assignment_tenant_isolation ON teacher_section_assignment
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: parent_student_link
CREATE POLICY parent_link_tenant_isolation ON parent_student_link
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: announcement
CREATE POLICY announcement_tenant_isolation ON announcement
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: calendar_event
CREATE POLICY calendar_event_tenant_isolation ON calendar_event
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: message_thread
CREATE POLICY message_thread_tenant_isolation ON message_thread
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: message (via thread's school_id)
CREATE POLICY message_tenant_isolation ON message
    USING (
        thread_id IN (
            SELECT id FROM message_thread
            WHERE school_id = current_setting('app.current_school_id', true)::uuid
        )
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
    );

-- RLS Policy: audit_log
CREATE POLICY audit_log_tenant_isolation ON audit_log
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR school_id IS NULL
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PLATFORM_TEAM_MEMBER'
    );

-- Note: The application user needs BYPASSRLS or the backend connects as a role
-- that has appropriate policies. For this design, the backend sets session variables
-- before each request, and the app DB user does NOT bypass RLS.
