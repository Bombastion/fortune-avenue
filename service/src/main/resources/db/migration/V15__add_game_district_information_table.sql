-- Per-game copy of a district's stock information. Board templates are reusable (multiple games
-- can point at the same board_id), so -- the same reasoning as game_shop_information copying
-- shop_information -- a district's in-game stock value can't live on districts itself. One row
-- per (game, district), seeded when a game starts (see GameDistrictInformationDao.seedForGame,
-- called right after GameShopInformationDao.seedForGame, which it depends on): current_stock_value
-- is the average current_value of that district's just-seeded SHOP rows, multiplied by the
-- district's minimum_stock_percentage (copied here for the same reason base_price_percentage is
-- copied onto game_shop_information). Only districts that actually contain at least one SHOP
-- space get a row -- there's nothing to average for a district with none.
CREATE TABLE game_district_information (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    district_id UUID NOT NULL REFERENCES districts(id) ON DELETE CASCADE,
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    -- NUMERIC(5, 4) for the same reason as districts.minimum_stock_percentage (V14): the
    -- value is allowed to equal 1 exactly.
    minimum_stock_percentage NUMERIC(5, 4) NOT NULL,
    current_stock_value INT NOT NULL,
    CONSTRAINT chk_game_district_information_minimum_stock_percentage_range
        CHECK (minimum_stock_percentage > 0 AND minimum_stock_percentage <= 1),
    CONSTRAINT chk_game_district_information_current_stock_value_nonnegative
        CHECK (current_stock_value >= 0),
    UNIQUE (game_id, district_id)
);
