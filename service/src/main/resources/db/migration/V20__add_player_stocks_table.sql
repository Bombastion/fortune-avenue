-- How much of a district's stock each player owns, in a given game. One row per (player,
-- game_district_information) -- game_district_information is itself already scoped to a single
-- (game, district) pair (see V15), so referencing it directly here is enough to place this
-- holding in both a game and a district without also repeating game_id. Created the first time a
-- player buys into a district's stock; quantity moves up and down from there as they buy or sell
-- (buy/sell logic isn't implemented yet).
CREATE TABLE player_stocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    game_district_information_id UUID NOT NULL REFERENCES game_district_information(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_player_stocks_quantity_nonnegative CHECK (quantity >= 0),
    UNIQUE (player_id, game_district_information_id)
);
