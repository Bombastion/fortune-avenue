ALTER TABLE player_states
    ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'WAITING';

-- turn_order is null until every player is READY; from then on it's a fixed,
-- randomly-decided array of player ids to cycle through. No FK possible on
-- array elements in Postgres, so that each id is actually a player in this
-- game is enforced in the service layer, not the schema.
ALTER TABLE games
    ADD COLUMN turn_order UUID[],
    ADD COLUMN max_turns INT NOT NULL DEFAULT 10;
