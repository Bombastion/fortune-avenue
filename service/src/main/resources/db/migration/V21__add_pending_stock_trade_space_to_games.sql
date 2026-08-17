-- Set the moment movement passes or lands on a BANK space (mid-move or as the final stop),
-- alongside that same space's promotion check (see GameSimulationService.checkStockTrade) --
-- null the rest of the time. Kept separate from current_movement_points (which already carries
-- two other pause meanings: a pending branch choice, and a pending shop purchase) because a
-- stock trade decision can pause with movement still left, unlike a shop purchase, which only
-- ever pauses once movement has fully run out -- so current_movement_points alone can't double
-- as this pause's signal the way it does for the other two.
ALTER TABLE games
    ADD COLUMN pending_stock_trade_space_id UUID REFERENCES board_spaces(id) ON DELETE SET NULL;
