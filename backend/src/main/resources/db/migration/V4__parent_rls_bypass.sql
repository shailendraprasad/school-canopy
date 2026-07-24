-- V4: Allow PARENT role to query across schools (visibility controlled by parent_student_link joins)
-- Parents don't have a school_id, so RLS on school-scoped tables blocks them.
-- The ParentPortalResource uses explicit joins on parent_student_link to restrict visibility.

DROP POLICY IF EXISTS user_account_tenant_isolation ON user_account;
CREATE POLICY user_account_tenant_isolation ON user_account
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PLATFORM_TEAM_MEMBER'
        OR current_setting('app.current_role', true) = 'PARENT'
        OR school_id IS NULL
    );

DROP POLICY IF EXISTS student_tenant_isolation ON student;
CREATE POLICY student_tenant_isolation ON student
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS announcement_tenant_isolation ON announcement;
CREATE POLICY announcement_tenant_isolation ON announcement
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS calendar_event_tenant_isolation ON calendar_event;
CREATE POLICY calendar_event_tenant_isolation ON calendar_event
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS message_thread_tenant_isolation ON message_thread;
CREATE POLICY message_thread_tenant_isolation ON message_thread
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS message_tenant_isolation ON message;
CREATE POLICY message_tenant_isolation ON message
    USING (
        thread_id IN (
            SELECT id FROM message_thread
            WHERE school_id = current_setting('app.current_school_id', true)::uuid
        )
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS enrollment_tenant_isolation ON student_section_enrollment;
CREATE POLICY enrollment_tenant_isolation ON student_section_enrollment
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS section_tenant_isolation ON section;
CREATE POLICY section_tenant_isolation ON section
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );

DROP POLICY IF EXISTS class_tenant_isolation ON class;
CREATE POLICY class_tenant_isolation ON class
    USING (
        school_id = current_setting('app.current_school_id', true)::uuid
        OR current_setting('app.current_role', true) = 'SUPER_ADMIN'
        OR current_setting('app.current_role', true) = 'PARENT'
    );
