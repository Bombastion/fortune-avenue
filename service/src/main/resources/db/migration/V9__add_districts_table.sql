-- Districts group related spaces together (e.g. a set of same-colored
-- spaces a player can build up, Monopoly-style). A space's district is
-- optional since not every space belongs to one.
CREATE TABLE districts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    color_hex VARCHAR(6) NOT NULL,
    CONSTRAINT chk_districts_color_hex_format CHECK (color_hex ~ '^[0-9A-Fa-f]{6}$')
);

ALTER TABLE board_spaces
    ADD COLUMN district_id UUID REFERENCES districts(id) ON DELETE SET NULL;
