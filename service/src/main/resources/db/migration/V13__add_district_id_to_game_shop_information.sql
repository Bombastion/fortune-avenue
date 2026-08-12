-- Denormalized from board_spaces.district_id (via shop_information.space_id), the same way
-- game_shop_information already carries board_id/space_id directly instead of requiring a join
-- every time. Lets a player's owned shops within a district be queried directly when a purchase
-- requires recalculating that district's values. Nullable since not every shop belongs to a
-- district.
ALTER TABLE game_shop_information
    ADD COLUMN district_id UUID REFERENCES districts(id) ON DELETE SET NULL;
