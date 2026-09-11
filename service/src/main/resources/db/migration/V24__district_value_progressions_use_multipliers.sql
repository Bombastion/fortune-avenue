-- Replaces the district_value_progressions "boost percentage" columns (permanently applied to a
-- shop's stored value the moment a purchase reached that owned_shop_count) with plain
-- multipliers, looked up fresh every time instead: price_multiplier scales a shop's
-- dynamically-computed toll price, and max_capital_multiplier scales its baseValue to produce
-- the ceiling on how much capital it can hold. Neither is ever baked into current_value anymore
-- -- see GameSimulationService.
--
-- existing_shop_boost_percentage/new_shop_boost_percentage stored additive boosts (e.g. 0.1000 =
-- +10%, constrained > 0); the new columns store the multiplier itself (e.g. 1.1000 = 1.1x,
-- constrained > 1), so existing data is shifted up by 1 before the columns are renamed/repurposed
-- -- new_shop_boost_percentage's values become max_capital_multiplier's starting point since
-- there's no better data to seed it from, not because the two concepts are otherwise related.
UPDATE district_value_progressions
SET existing_shop_boost_percentage = existing_shop_boost_percentage + 1,
    new_shop_boost_percentage = new_shop_boost_percentage + 1;

ALTER TABLE district_value_progressions
    DROP CONSTRAINT chk_district_value_progressions_existing_shop_boost_percentage_positive;
ALTER TABLE district_value_progressions
    DROP CONSTRAINT chk_district_value_progressions_new_shop_boost_percentage_positive;

ALTER TABLE district_value_progressions
    RENAME COLUMN existing_shop_boost_percentage TO price_multiplier;
ALTER TABLE district_value_progressions
    RENAME COLUMN new_shop_boost_percentage TO max_capital_multiplier;

ALTER TABLE district_value_progressions
    ADD CONSTRAINT chk_district_value_progressions_price_multiplier_above_one
        CHECK (price_multiplier > 1);
ALTER TABLE district_value_progressions
    ADD CONSTRAINT chk_district_value_progressions_max_capital_multiplier_above_one
        CHECK (max_capital_multiplier > 1);
