-- How many times this player has already collected the BANK promotion this game (see
-- GameSimulationService) -- starts at 0, used as-is as the "level" in the promotion payout
-- formula (boards.base_salary + boards.promotion_bonus * promotion_count), then incremented
-- after each promotion.
ALTER TABLE player_states
    ADD COLUMN promotion_count INT NOT NULL DEFAULT 0;
