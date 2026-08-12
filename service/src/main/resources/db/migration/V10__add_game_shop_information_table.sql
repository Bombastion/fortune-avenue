-- Per-game copy of shop_information. Board templates are reusable (multiple
-- games can point at the same board_id), so the mutable, in-game state of a
-- shop -- current value, who owns it, how much is invested -- can't live on
-- shop_information itself. One row per (game, shop) instead, seeded from
-- shop_information when a game starts.
CREATE TABLE game_shop_information (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    shop_information_id UUID NOT NULL REFERENCES shop_information(id) ON DELETE CASCADE,
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    space_id UUID NOT NULL REFERENCES board_spaces(id) ON DELETE CASCADE,
    base_value INT NOT NULL,
    base_price_percentage NUMERIC(4, 4) NOT NULL,
    -- Null until a player buys it
    owner_id UUID REFERENCES players(id) ON DELETE SET NULL,
    current_value INT NOT NULL,
    current_investment INT NOT NULL,
    max_cap INT NOT NULL,
    CONSTRAINT chk_game_shop_information_base_value_positive CHECK (base_value > 0),
    CONSTRAINT chk_game_shop_information_base_price_percentage_range
        CHECK (base_price_percentage > 0 AND base_price_percentage < 1),
    UNIQUE (game_id, shop_information_id)
);
