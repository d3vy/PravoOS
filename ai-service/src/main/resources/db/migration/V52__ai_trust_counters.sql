CREATE TABLE ai_trust_counters (
    stat_date   DATE        NOT NULL,
    metric      VARCHAR(40) NOT NULL,
    value       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, metric)
);
