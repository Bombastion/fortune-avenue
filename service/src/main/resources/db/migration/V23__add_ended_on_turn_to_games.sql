-- Records the turn number a game actually ended on -- set once, whether turn_number naturally
-- reached max_turns or (see GameSimulationService.endGameIfNetWorthReached) a player's net worth
-- reached target_net_worth first. Null while the game is still in progress. Deciding whether/when
-- a game has ended is the service layer's job, not a DAO's -- see GameDao.setEndedOnTurn.
ALTER TABLE games ADD COLUMN ended_on_turn INT NULL;
