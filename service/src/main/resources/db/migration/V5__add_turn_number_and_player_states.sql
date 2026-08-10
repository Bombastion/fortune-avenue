ALTER TABLE games
    ADD COLUMN turn_number INT NOT NULL DEFAULT 0;

-- Separate from players (identity/roster: which game, which user) so that
-- per-player game state -- position now, money/properties/etc. later -- has
-- its own home instead of players accumulating unrelated columns over time.
-- One-to-one with players via the UNIQUE constraint.
CREATE TABLE player_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL UNIQUE REFERENCES players(id) ON DELETE CASCADE,
    current_space_id UUID REFERENCES board_spaces(id)
);
