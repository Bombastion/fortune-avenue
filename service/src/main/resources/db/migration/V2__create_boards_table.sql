CREATE TABLE boards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    start_space_id UUID
);

CREATE TABLE board_spaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    space_type VARCHAR(50) NOT NULL
);

CREATE TABLE board_paths (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    from_space_id UUID NOT NULL REFERENCES board_spaces(id) ON DELETE CASCADE,
    to_space_id UUID NOT NULL REFERENCES board_spaces(id) ON DELETE CASCADE,
    branch_order INT NOT NULL DEFAULT 0,
    UNIQUE (from_space_id, branch_order)
);

ALTER TABLE boards
    ADD CONSTRAINT fk_boards_start_space FOREIGN KEY (start_space_id) REFERENCES board_spaces(id);
