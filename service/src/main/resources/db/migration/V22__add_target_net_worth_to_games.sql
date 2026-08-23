-- A player's net worth -- every shop (property) they own plus the current value of every
-- district's stock they hold, gold on hand excluded -- is checked the moment it could have moved
-- (see GameSimulationService.endGameIfNetWorthReached): once any player's net worth reaches or
-- exceeds a game's target_net_worth, the game ends immediately, exactly as it already does once
-- turn_number reaches max_turns. Configurable per game at creation time (see CreateGameRequest),
-- defaulting to 6000.
ALTER TABLE games
    ADD COLUMN target_net_worth INT NOT NULL DEFAULT 6000
        CONSTRAINT chk_games_target_net_worth_positive CHECK (target_net_worth > 0);
