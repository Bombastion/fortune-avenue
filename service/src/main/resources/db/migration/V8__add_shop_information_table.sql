-- Extra information for SHOP spaces. One row per SHOP board_space; the
-- board_id is duplicated from board_spaces so it can be queried directly,
-- the same way board_spaces and board_paths both carry board_id.
CREATE TABLE shop_information (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    space_id UUID NOT NULL UNIQUE REFERENCES board_spaces(id) ON DELETE CASCADE,
    base_value INT NOT NULL,
    base_price_percentage NUMERIC(4, 4) NOT NULL,
    CONSTRAINT chk_shop_information_base_value_positive CHECK (base_value > 0),
    CONSTRAINT chk_shop_information_base_price_percentage_range
        CHECK (base_price_percentage > 0 AND base_price_percentage < 1)
);
