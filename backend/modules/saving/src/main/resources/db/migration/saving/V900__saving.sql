CREATE SCHEMA IF NOT EXISTS saving;

CREATE TABLE saving.goal (
    id                 UUID PRIMARY KEY,
    owner_id           UUID           NOT NULL,
    name               VARCHAR(60)    NOT NULL,
    target_amount      NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    deadline           DATE,
    icon               VARCHAR(40),
    saved_amount       NUMERIC(19, 4) NOT NULL,
    milestone          INTEGER        NOT NULL,
    auto_amount        NUMERIC(19, 4),
    auto_wallet_id     UUID,
    auto_frequency     VARCHAR(10),
    auto_start         DATE,
    auto_count         INTEGER        NOT NULL,
    round_up_wallet_id UUID,
    round_up_unit      NUMERIC(19, 4),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT         NOT NULL
);

CREATE INDEX idx_saving_goal_owner ON saving.goal (owner_id);
CREATE INDEX idx_saving_goal_round_up ON saving.goal (owner_id, round_up_wallet_id);

CREATE TABLE saving.movement (
    id             UUID PRIMARY KEY,
    goal_id        UUID           NOT NULL REFERENCES saving.goal (id) ON DELETE CASCADE,
    type           VARCHAR(10)    NOT NULL,
    source         VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    wallet_id      UUID           NOT NULL,
    movement_date  DATE           NOT NULL,
    status         VARCHAR(10)    NOT NULL,
    transaction_id UUID,
    failure_reason VARCHAR(300),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_saving_movement_goal ON saving.movement (goal_id);
