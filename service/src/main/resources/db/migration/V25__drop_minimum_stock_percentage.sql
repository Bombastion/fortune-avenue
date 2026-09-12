-- The real game (per FortuneStreetModding's own board editor's "Tools > Stock Prices" preview,
-- Editor/MainWindow.xaml.cs, and their district simulator at fortunestreetmodding.github.io/
-- simulator, src/pages/simulator.js) computes every district's base stock value with one fixed
-- 16.16 fixed-point multiplier (0x0B00 / 0x10000, roughly 4.3%) applied identically everywhere,
-- not a per-district author-configurable floor. GameDistrictInformationDao and
-- GameSimulationService now hardcode that constant instead, so the per-district and per-game
-- minimum_stock_percentage columns (V14/V15/V16) no longer have anything to hold.
ALTER TABLE districts
    DROP CONSTRAINT chk_districts_minimum_stock_percentage_range,
    DROP COLUMN minimum_stock_percentage;

ALTER TABLE game_district_information
    DROP CONSTRAINT chk_game_district_information_minimum_stock_percentage_range,
    DROP COLUMN minimum_stock_percentage;
