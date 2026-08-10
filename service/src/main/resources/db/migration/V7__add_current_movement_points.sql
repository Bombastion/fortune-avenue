-- Null whenever nobody's mid-turn (waiting on a roll, or the game hasn't
-- started/is over); set to the remaining movement from a die roll once the
-- current player rolls, decremented as they move, and cleared back to null
-- the moment their turn ends. Only ever meaningful for the player at
-- games.turn_number % array_length(turn_order, 1) in turn order.
ALTER TABLE games
    ADD COLUMN current_movement_points INT;
