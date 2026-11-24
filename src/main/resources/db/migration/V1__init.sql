CREATE TABLE rockets (
    channel                      UUID PRIMARY KEY,
    status                       TEXT   NOT NULL,
    type                         TEXT,
    mission                      TEXT,
    speed                        BIGINT,
    explosion_reason             TEXT,
    launched_at                  TIMESTAMPTZ,
    last_applied_message_number  BIGINT NOT NULL DEFAULT 0,
    updated_at                   TIMESTAMPTZ
);

CREATE TABLE rocket_events (
    channel         UUID        NOT NULL REFERENCES rockets (channel),
    message_number  BIGINT      NOT NULL,
    message_type    TEXT        NOT NULL,
    message_time    TIMESTAMPTZ NOT NULL,
    payload         JSONB       NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (channel, message_number)
);
