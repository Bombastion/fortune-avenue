-- Boards define how much gold every player starts a game with. player_states
-- tracks each player's current gold, seeded from their game's board.starting_gold
-- when the player is created (see PlayerDao) and free to change over the
-- course of play from there.
ALTER TABLE boards
    ADD COLUMN starting_gold INT NOT NULL
        CONSTRAINT chk_boards_starting_gold_positive CHECK (starting_gold > 0);

-- No non-negative check: current_gold can go below zero if a player spends more than they have
-- on hand, which triggers a state where they have to auction off properties to get back in the
-- positive (auction mechanics aren't implemented yet, but the schema has to allow the state).
ALTER TABLE player_states
    ADD COLUMN current_gold INT NOT NULL;
