-- Defines how shop values in a district scale as a single player accumulates
-- more of them there. One row per step: owned_shop_count is the count the
-- player has just reached (2, 3, 4, ... up to the district's size -- owning
-- just 1 shop has no boost yet, so 1 is never a valid step). On reaching that
-- count, every shop the player already owns in the district increases by
-- existing_shop_boost_percentage, and the shop they just bought increases by
-- new_shop_boost_percentage instead (larger, since it missed out on the
-- boosts from earlier purchases).
--
-- Deliberately per-district rather than a shared board-level curve: districts
-- of the same size can still scale differently from each other, and this
-- keeps every district's curve independently tunable.
CREATE TABLE district_value_progressions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    district_id UUID NOT NULL REFERENCES districts(id) ON DELETE CASCADE,
    owned_shop_count INT NOT NULL,
    -- Boosts are positive multipliers with no fixed upper bound (e.g. 1.5000 is a valid, if
    -- generous, 150% boost) -- unlike base_price_percentage, these aren't a fraction of a value.
    existing_shop_boost_percentage NUMERIC(7, 4) NOT NULL,
    new_shop_boost_percentage NUMERIC(7, 4) NOT NULL,
    CONSTRAINT chk_district_value_progressions_owned_shop_count_min CHECK (owned_shop_count >= 2),
    CONSTRAINT chk_district_value_progressions_existing_shop_boost_percentage_positive
        CHECK (existing_shop_boost_percentage > 0),
    CONSTRAINT chk_district_value_progressions_new_shop_boost_percentage_positive
        CHECK (new_shop_boost_percentage > 0),
    UNIQUE (district_id, owned_shop_count)
);
