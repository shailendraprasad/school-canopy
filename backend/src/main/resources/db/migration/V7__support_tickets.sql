-- =============================================================================
-- V7: Support Tickets
-- =============================================================================

CREATE TABLE support_ticket (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    created_by  UUID NOT NULL REFERENCES user_account(id),
    subject     VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to UUID REFERENCES user_account(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX idx_ticket_school ON support_ticket(school_id);
CREATE INDEX idx_ticket_status ON support_ticket(status);
CREATE INDEX idx_ticket_created_by ON support_ticket(created_by);

CREATE TABLE ticket_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES support_ticket(id),
    author_id   UUID NOT NULL REFERENCES user_account(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_comment_ticket ON ticket_comment(ticket_id);
