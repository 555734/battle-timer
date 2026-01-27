import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import { CONSTRAINTS, Constraint } from '../constants/Constraints';
import { DEFAULT_SETTINGS } from '../constants/Settings';

export type PlayerId = 1 | 2;
export type GameStatus = 'idle' | 'playing' | 'paused' | 'ended';

interface GameContextType {
    // State
    initialTime: number;
    timer1: number;
    timer2: number;
    activePlayer: PlayerId | null;
    status: GameStatus;
    currentConstraint: Constraint | null;
    winner: PlayerId | null;
    winReason: 'time' | 'penalty' | null;

    // Actions
    startGame: (timeLimit?: number, difficulty?: 'normal' | 'hard' | 'fun' | 'all') => void;
    pauseGame: () => void;
    resumeGame: () => void;
    switchTurn: () => void;
    resetGame: () => void;
    triggerPenalty: (loser: PlayerId) => void;
}

const GameContext = createContext<GameContextType | undefined>(undefined);

export const useGame = () => {
    const context = useContext(GameContext);
    if (!context) {
        throw new Error('useGame must be used within a GameProvider');
    }
    return context;
};

export const GameProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    // Settings
    const [initialTime, setInitialTime] = useState(DEFAULT_SETTINGS.initialTime);

    // Game State
    const [timer1, setTimer1] = useState(initialTime);
    const [timer2, setTimer2] = useState(initialTime);
    const [activePlayer, setActivePlayer] = useState<PlayerId | null>(null);
    const [status, setStatus] = useState<GameStatus>('idle');
    const [currentConstraint, setCurrentConstraint] = useState<Constraint | null>(null);

    // End Game State
    const [winner, setWinner] = useState<PlayerId | null>(null);
    const [winReason, setWinReason] = useState<'time' | 'penalty' | null>(null);

    // Refs for precise timing
    const lastTickRef = useRef<number | null>(null);
    const timer1Ref = useRef(initialTime);
    const timer2Ref = useRef(initialTime);
    const frameIdRef = useRef<number | null>(null);

    // --- Logic ---

    const getRandomConstraint = () => {
        const randomIndex = Math.floor(Math.random() * CONSTRAINTS.length);
        return CONSTRAINTS[randomIndex];
    };

    const startGame = useCallback((customTime?: number, difficulty?: 'normal' | 'hard' | 'fun' | 'all') => {
        const time = customTime || initialTime;
        setInitialTime(time);

        // Reset refs
        timer1Ref.current = time;
        timer2Ref.current = time;
        setTimer1(time);
        setTimer2(time);

        setActivePlayer(1);

        // Filter constraints
        let availableConstraints = CONSTRAINTS;
        if (difficulty && difficulty !== 'all') {
            availableConstraints = CONSTRAINTS.filter(c => c.type === difficulty || c.type === 'normal'); // Always include normal? Or strict?
            // Requirement: Easy (Word count), Hard (Forbidden), etc.
            // Let's implement strict filtering but fallback to all if empty (safety)
            const strictFiltered = CONSTRAINTS.filter(c => c.type === difficulty);
            if (strictFiltered.length > 0) {
                availableConstraints = strictFiltered;
            }
        }

        const randomIndex = Math.floor(Math.random() * availableConstraints.length);
        setCurrentConstraint(availableConstraints[randomIndex]);

        setStatus('playing');
        setWinner(null);
        setWinReason(null);

        lastTickRef.current = Date.now();
    }, [initialTime]);

    const pauseGame = useCallback(() => {
        if (status === 'playing') {
            setStatus('paused');
            lastTickRef.current = null;
        }
    }, [status]);

    const resumeGame = useCallback(() => {
        if (status === 'paused') {
            setStatus('playing');
            lastTickRef.current = Date.now();
        }
    }, [status]);

    const switchTurn = useCallback(() => {
        if (status !== 'playing' || !activePlayer) return;

        // Switch player
        const nextPlayer = activePlayer === 1 ? 2 : 1;
        setActivePlayer(nextPlayer);

        // Reset timer for the next player (Per-turn logic)
        if (nextPlayer === 1) {
            setTimer1(initialTime);
            timer1Ref.current = initialTime;
        } else {
            setTimer2(initialTime);
            timer2Ref.current = initialTime;
        }

        // Reset tick ref to avoid "jump" if there was a slight delay
        lastTickRef.current = Date.now();

        // OPTIONAL: New constraint every turn?
        // User requirement: "毎ターン変えるか、ゲームごとに変えるかを選択可能にします（デフォルトはゲームごとに固定推奨）"
        // So default is fixed per game. We can add a setting later.
    }, [status, activePlayer, initialTime]);

    const endGame = useCallback((winningPlayer: PlayerId, reason: 'time' | 'penalty') => {
        setStatus('ended');
        setWinner(winningPlayer);
        setWinReason(reason);
        lastTickRef.current = null;
    }, []);

    const triggerPenalty = useCallback((loser: PlayerId) => {
        const winner = loser === 1 ? 2 : 1;
        endGame(winner, 'penalty');
    }, [endGame]);

    const resetGame = useCallback(() => {
        setStatus('idle');
        setTimer1(initialTime);
        setTimer2(initialTime);
        timer1Ref.current = initialTime;
        timer2Ref.current = initialTime;
        setActivePlayer(null);
        setWinner(null);
        setWinReason(null);
        setCurrentConstraint(null);
    }, [initialTime]);

    // --- Timer Loop ---
    useEffect(() => {
        if (status !== 'playing') {
            if (frameIdRef.current) {
                cancelAnimationFrame(frameIdRef.current);
                frameIdRef.current = null;
            }
            return;
        }

        const loop = () => {
            const now = Date.now();
            if (lastTickRef.current) {
                const delta = (now - lastTickRef.current) / 1000; // seconds

                if (activePlayer === 1) {
                    timer1Ref.current = Math.max(0, timer1Ref.current - delta);
                    setTimer1(timer1Ref.current); // Use state for UI
                    if (timer1Ref.current <= 0) {
                        endGame(2, 'time');
                        return; // Stop loop
                    }
                } else if (activePlayer === 2) {
                    timer2Ref.current = Math.max(0, timer2Ref.current - delta);
                    setTimer2(timer2Ref.current); // Use state for UI
                    if (timer2Ref.current <= 0) {
                        endGame(1, 'time');
                        return; // Stop loop
                    }
                }

                lastTickRef.current = now;
            }

            frameIdRef.current = requestAnimationFrame(loop);
        };

        frameIdRef.current = requestAnimationFrame(loop);

        return () => {
            if (frameIdRef.current) {
                cancelAnimationFrame(frameIdRef.current);
            }
        };
    }, [status, activePlayer, endGame]);

    const value = {
        initialTime,
        timer1,
        timer2,
        activePlayer,
        status,
        currentConstraint,
        winner,
        winReason,
        startGame,
        pauseGame,
        resumeGame,
        switchTurn,
        resetGame,
        triggerPenalty,
    };

    return <GameContext.Provider value={value}>{children}</GameContext.Provider>;
};
