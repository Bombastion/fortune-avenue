-- Card suits (see SpaceType: HEART, DIAMOND, SPADE, CLUB) a player has picked up so far this
-- game by passing or landing on that type of space (see GameSimulationService)
ALTER TABLE player_states
    ADD COLUMN held_suits VARCHAR(50)[] NOT NULL DEFAULT '{}';
