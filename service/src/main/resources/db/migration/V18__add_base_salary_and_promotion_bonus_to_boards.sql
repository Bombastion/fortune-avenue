-- Boards define a base salary and a per-level promotion bonus for the BANK promotion payout (see
-- GameSimulationService): a player crossing/landing on a BANK space while holding all 4 suits (see
-- SpaceType) earns base_salary + (promotion_bonus * their promotion count so far) + the value of
-- every shop they own, then their promotion count (see V19) goes up by one for next time.
ALTER TABLE boards
    ADD COLUMN base_salary INT NOT NULL
        CONSTRAINT chk_boards_base_salary_positive CHECK (base_salary > 0),
    ADD COLUMN promotion_bonus INT NOT NULL
        CONSTRAINT chk_boards_promotion_bonus_non_negative CHECK (promotion_bonus >= 0);
