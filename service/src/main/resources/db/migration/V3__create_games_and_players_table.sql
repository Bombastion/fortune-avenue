CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

-- user_id is nullable because a player isn't always tied to a person --
-- computer opponents (not implemented yet) will be players with no user.
-- The UNIQUE constraint still does useful work with that nullability: Postgres
-- treats each NULL as distinct, so it allows any number of computer players
-- per game while still stopping the same real user from joining a game twice.
CREATE TABLE players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE (game_id, user_id)
);
