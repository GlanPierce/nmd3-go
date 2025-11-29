export const PLAYER_1 = 'p1';
export const PLAYER_2 = 'p2';

export const GamePhase = {
    PRE_GAME: 'PRE_GAME',
    AMBUSH: 'AMBUSH',
    PLACEMENT: 'PLACEMENT',
    EXTRA_ROUNDS: 'EXTRA_ROUNDS',
    GAME_OVER: 'GAME_OVER',
    MATCH_CANCELLED: 'MATCH_CANCELLED'
};

export const API_ENDPOINTS = {
    READY: (gameId) => `/app/game/${gameId}/ready`,
    AMBUSH: (gameId) => `/app/game/${gameId}/ambush`,
    PLACE: (gameId) => `/app/game/${gameId}/place`
};
