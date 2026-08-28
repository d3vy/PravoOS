CREATE TABLE ai_action_proposal
(
    id              UUID PRIMARY KEY,
    org_id          UUID,
    user_id         UUID         NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    tool_name       VARCHAR(100) NOT NULL,
    arguments       JSONB        NOT NULL,
    title           VARCHAR(500) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    result          TEXT,
    failure_reason  VARCHAR(1000),
    created_at      TIMESTAMP    NOT NULL,
    decided_at      TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_ai_action_proposal_pending
    ON ai_action_proposal (user_id, conversation_id, status);

CREATE INDEX idx_ai_action_proposal_expiry
    ON ai_action_proposal (expires_at) WHERE status = 'PENDING';

CREATE INDEX idx_ai_action_proposal_rate_limit
    ON ai_action_proposal (user_id, created_at);
