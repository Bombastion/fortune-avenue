-- A district's minimum_stock_percentage is always a fraction of its shops' average value, so
-- like shop_information.base_price_percentage it must be strictly less than 1, not merely no
-- greater than 1 as V14/V15 originally allowed. Tightens both CHECK constraints accordingly, and
-- shrinks both columns back down to NUMERIC(4, 4) -- the same sizing as base_price_percentage --
-- now that neither ever needs to hold 1.0000.
ALTER TABLE districts
    DROP CONSTRAINT chk_districts_minimum_stock_percentage_range;
ALTER TABLE districts
    ALTER COLUMN minimum_stock_percentage TYPE NUMERIC(4, 4);
ALTER TABLE districts
    ADD CONSTRAINT chk_districts_minimum_stock_percentage_range
        CHECK (minimum_stock_percentage > 0 AND minimum_stock_percentage < 1);

ALTER TABLE game_district_information
    DROP CONSTRAINT chk_game_district_information_minimum_stock_percentage_range;
ALTER TABLE game_district_information
    ALTER COLUMN minimum_stock_percentage TYPE NUMERIC(4, 4);
ALTER TABLE game_district_information
    ADD CONSTRAINT chk_game_district_information_minimum_stock_percentage_range
        CHECK (minimum_stock_percentage > 0 AND minimum_stock_percentage < 1);
