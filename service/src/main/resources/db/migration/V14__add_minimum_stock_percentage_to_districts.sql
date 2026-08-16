-- Every district now defines a minimum stock percentage: the floor, as a fraction of the
-- average value of the district's SHOP spaces, that its stock can trade at once a game starts.
-- Used to seed game_district_information.current_stock_value (see V15 and
-- GameDistrictInformationDao.seedForGame).
ALTER TABLE districts
    -- NUMERIC(5, 4), not (4, 4): unlike base_price_percentage (always < 1), this is allowed
    -- to equal 1 exactly, which needs one digit of room before the decimal point.
    ADD COLUMN minimum_stock_percentage NUMERIC(5, 4) NOT NULL
        CONSTRAINT chk_districts_minimum_stock_percentage_range
            CHECK (minimum_stock_percentage > 0 AND minimum_stock_percentage <= 1);
