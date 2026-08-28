CREATE TABLE ai_trusted_tool
(
    id         UUID PRIMARY KEY,
    org_id     UUID,
    user_id    UUID         NOT NULL,
    tool_name  VARCHAR(100) NOT NULL,
    granted_at TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_ai_trusted_tool_user_tool
    ON ai_trusted_tool (user_id, tool_name);
