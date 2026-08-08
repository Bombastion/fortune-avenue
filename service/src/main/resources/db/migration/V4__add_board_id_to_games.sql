-- Fresh table, so this is safe to add straight as NOT NULL: there's no
-- existing row that would need a default value backfilled.
ALTER TABLE games
    ADD COLUMN board_id UUID NOT NULL REFERENCES boards(id);
