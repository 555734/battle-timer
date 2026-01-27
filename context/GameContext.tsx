import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import { CONSTRAINTS, Constraint } from '../constants/Constraints';
import { DEFAULT_SETTINGS } from '../constants/Settings';

export type PlayerId = number;
export type GameStatus = 'idle' | 'playing' | 'paused' | 'ended';

interface GameContextType {
    initialTime: number;
    playerCount: number;
    timers: number[];
    activePlayer: PlayerId | null; // 1-based index
    status: GameStatus;
    currentConstraint: Constraint | null;
    winner: PlayerId | null;
    winReason: 'time' | 'penalty' | null;

    startGame: (playerCount: number, timeLimit?: number, difficulty?: 'normal' | 'hard' | 'fun' | 'all') => void;
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
    const [initialTime, setInitialTime] = useState(DEFAULT_SETTINGS.initialTime);
    const [playerCount, setPlayerCount] = useState(2);
    const [timers, setTimers] = useState<number[]>([]);
    const [activePlayer, setActivePlayer] = useState<PlayerId | null>(null);
    const [status, setStatus] = useState<GameStatus>('idle');
    const [currentConstraint, setCurrentConstraint] = useState<Constraint | null>(null);
    const [winner, setWinner] = useState<PlayerId | null>(null);
    const [winReason, setWinReason] = useState<'time' | 'penalty' | null>(null);

    const lastTickRef = useRef<number | null>(null);
    const timersRef = useRef<number[]>([]);
    const frameIdRef = useRef<number | null>(null);

    const startGame = useCallback((count: number, customTime?: number, difficulty?: 'normal' | 'hard' | 'fun' | 'all') => {
        console.log(`[Debug] startGame called: playerCount=${count}, time=${customTime}, difficulty=${difficulty}`);
        const time = customTime || initialTime;
        setInitialTime(time);
        setPlayerCount(count);

        const newTimers = Array(count).fill(time);
        timersRef.current = [...newTimers];
        setTimers(newTimers);

        setActivePlayer(1);

        let availableConstraints = CONSTRAINTS;
        if (difficulty && difficulty !== 'all') {
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

    const endGame = useCallback((winningPlayer: PlayerId, reason: 'time' | 'penalty') => {
        console.log(`[Debug] endGame: winner=Player${winningPlayer}, reason=${reason}`);
        setStatus('ended');
        setWinner(winningPlayer);
        setWinReason(reason);
        lastTickRef.current = null;
    }, []);

    const pauseGame = useCallback(() => {
        console.log('[Debug] pauseGame called');
        if (status === 'playing') {
            setStatus('paused');
            lastTickRef.current = null;
        }
    }, [status]);

    const resumeGame = useCallback(() => {
        console.log('[Debug] resumeGame called');
        if (status === 'paused') {
            setStatus('playing');
            lastTickRef.current = Date.now();
        }
    }, [status]);

    const switchTurn = useCallback(() => {
        console.log(`[Debug] switchTurn: current activePlayer=${activePlayer}`);
        if (status !== 'playing' || !activePlayer) return;

        const nextPlayer = (activePlayer % playerCount) + 1;

        // ターンを終えたプレイヤーのタイマーをリセット（元の仕様を継承）
        const updatedTimers = [...timersRef.current];
        updatedTimers[activePlayer - 1] = initialTime;
        timersRef.current = updatedTimers;
        setTimers(updatedTimers);

        setActivePlayer(nextPlayer);
        lastTickRef.current = Date.now();
    }, [status, activePlayer, playerCount, initialTime]);

    const triggerPenalty = useCallback((loser: PlayerId) => {
        console.log(`[Debug] triggerPenalty: loser=Player${loser}`);
        // 複数人の場合、指摘された人以外が勝ち残るか、即終了か。
        // ここではシンプルに、指摘した側の直前のプレイヤーを暫定勝者とするなどのロジックが必要ですが、
        // 2人対戦の仕様を引き継ぎ、指摘された人以外の「誰か」を勝者に設定します。
        const provisionalWinner = loser === 1 ? 2 : 1;
        endGame(provisionalWinner, 'penalty');
    }, [endGame]);

    const resetGame = useCallback(() => {
        console.log('[Debug] resetGame called');
        setStatus('idle');
        setTimers([]);
        timersRef.current = [];
        setActivePlayer(null);
        setWinner(null);
        setWinReason(null);
        setCurrentConstraint(null);
    }, []);

    useEffect(() => {
        if (status !== 'playing' || activePlayer === null) {
            if (frameIdRef.current) {
                cancelAnimationFrame(frameIdRef.current);
                frameIdRef.current = null;
            }
            return;
        }

        const loop = () => {
            const now = Date.now();
            if (lastTickRef.current) {
                const delta = (now - lastTickRef.current) / 1000;
                const currentIndex = activePlayer - 1;

                timersRef.current[currentIndex] = Math.max(0, timersRef.current[currentIndex] - delta);

                // UI更新頻度を抑えるための工夫も可能ですが、一旦シンプルに更新
                setTimers([...timersRef.current]);

                if (timersRef.current[currentIndex] <= 0) {
                    // 時間切れの場合、他の誰かが勝者（ここでは次のプレイヤーを勝者と仮定）
                    const winnerId = (activePlayer % playerCount) + 1;
                    endGame(winnerId, 'time');
                    return;
                }
                lastTickRef.current = now;
            }
            frameIdRef.current = requestAnimationFrame(loop);
        };

        frameIdRef.current = requestAnimationFrame(loop);
        return () => {
            if (frameIdRef.current) cancelAnimationFrame(frameIdRef.current);
        };
    }, [status, activePlayer, playerCount, endGame]);

    const value = {
        initialTime,
        playerCount,
        timers,
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