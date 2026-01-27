import React, { useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Dimensions } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame, PlayerId } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Pause, RotateCcw, AlertTriangle } from 'lucide-react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withTiming,
    withSequence,
    interpolateColor
} from 'react-native-reanimated';

const { width } = Dimensions.get('window');

const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    const ms = Math.floor((seconds % 1) * 10); // Show tenths of a second if needed, mostly for low time
    return `${m}:${s.toString().padStart(2, '0')}`;
};

const PlayerSection = ({
    player,
    time,
    initialTime,
    isActive,
    rotated = false,
    onPress,
    onPenalty
}: {
    player: PlayerId;
    time: number;
    initialTime: number;
    isActive: boolean;
    rotated?: boolean;
    onPress: () => void;
    onPenalty: () => void;
}) => {
    const baseColor = player === 1 ? COLORS.player1 : COLORS.player2;
    const activeColor = player === 1 ? COLORS.player1Light : COLORS.player2Light;

    // Calculate progress percentage
    const progress = Math.max(0, Math.min(100, (time / initialTime) * 100));

    return (
        <View style={[
            styles.playerSection,
            rotated && styles.rotatedSection,
            { backgroundColor: isActive ? activeColor : COLORS.surface }
        ]}>
            {/* Progress Bar Background */}
            <View style={[styles.progressBarContainer, rotated && styles.rotatedProgressBar]}>
                <View
                    style={[
                        styles.progressBarFill,
                        {
                            width: `${progress}%`,
                            backgroundColor: time < 5 ? COLORS.danger : COLORS.accent
                        }
                    ]}
                />
            </View>

            {/* Container for content that rotates */}
            <View style={[styles.playerContent, rotated && { transform: [{ rotate: '180deg' }] }]}>

                {/* Timer Display */}
                <View style={styles.timerContainer}>
                    <Text style={[
                        styles.timerText,
                        { color: isActive ? COLORS.background : COLORS.text },
                        time < 10 && styles.timerTextUrgent // Visual urgency
                    ]}>
                        {formatTime(time)}
                    </Text>
                </View>

                {/* Action Area */}
                <TouchableOpacity
                    style={styles.actionArea}
                    activeOpacity={0.8}
                    onPress={onPress}
                    disabled={!isActive} // Only receiving player (who is active) can end their turn
                >
                    <Text style={[
                        styles.actionText,
                        { color: isActive ? COLORS.background : COLORS.textDim }
                    ]}>
                        {isActive ? "TAP TO END TURN" : "WAITING..."}
                    </Text>
                </TouchableOpacity>

                {/* Penalty Button (Small) */}
                {!isActive && (
                    <TouchableOpacity style={styles.penaltyButton} onPress={onPenalty}>
                        <AlertTriangle size={20} color={COLORS.danger} />
                        <Text style={styles.penaltyText}>指摘！</Text>
                    </TouchableOpacity>
                )}
            </View>
        </View>
    );
};

export default function GameScreen() {
    const router = useRouter();
    const {
        timer1,
        timer2,
        activePlayer,
        status,
        currentConstraint,
        switchTurn,
        pauseGame,
        triggerPenalty,
        winner,
        initialTime
    } = useGame();

    // Navigate to result if game ended
    useEffect(() => {
        if (status === 'ended') {
            router.replace('/result');
        }
    }, [status]);

    if (status === 'ended') return null;

    return (
        <View style={styles.container}>
            {/* Player 2 (Top, Rotated) */}
            <PlayerSection
                player={2}
                time={timer2}
                initialTime={initialTime}
                isActive={activePlayer === 2}
                rotated={true}
                onPress={switchTurn}
                onPenalty={() => triggerPenalty(1)} // Player 2 calls penalty on Player 1
            />

            {/* Center Control Bar */}
            <View style={styles.centerBar}>
                <View style={styles.constraintContainer}>
                    <Text style={styles.constraintLabel}>CONDITION</Text>
                    <Text style={styles.constraintText} numberOfLines={2} adjustsFontSizeToFit>
                        {currentConstraint?.text || '...'}
                    </Text>
                </View>

                {/* Pause/Menu Button */}
                {/* <TouchableOpacity style={styles.menuButton} onPress={pauseGame}>
          <Pause size={24} color={COLORS.text} />
        </TouchableOpacity> */}
            </View>

            {/* Player 1 (Bottom) */}
            <PlayerSection
                player={1}
                time={timer1}
                initialTime={initialTime}
                isActive={activePlayer === 1}
                rotated={false}
                onPress={switchTurn}
                onPenalty={() => triggerPenalty(2)} // Player 1 calls penalty on Player 2
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: COLORS.background,
    },
    playerSection: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        width: '100%',
    },
    rotatedSection: {
        // backgroundColor handled in component
        borderBottomWidth: 4,
        borderBottomColor: COLORS.background,
    },
    playerContent: {
        width: '100%',
        height: '100%',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 24,
    },
    timerContainer: {
        marginTop: 40,
    },
    timerText: {
        fontSize: 80,
        fontWeight: '900',
        fontVariant: ['tabular-nums'],
    },
    timerTextUrgent: {
        color: COLORS.danger,
    },
    actionArea: {
        flex: 1,
        width: '100%',
        alignItems: 'center',
        justifyContent: 'center',
        maxHeight: 200,
    },
    actionText: {
        fontSize: 24,
        fontWeight: 'bold',
        opacity: 0.8,
    },
    penaltyButton: {
        position: 'absolute',
        top: 20,
        right: 20,
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: 'rgba(213, 0, 0, 0.2)',
        padding: 12,
        borderRadius: 8,
        borderWidth: 1,
        borderColor: COLORS.danger,
    },
    penaltyText: {
        color: COLORS.danger,
        fontWeight: 'bold',
        marginLeft: 4,
    },
    centerBar: {
        height: 80,
        backgroundColor: COLORS.background,
        borderTopWidth: 2,
        borderBottomWidth: 2,
        borderColor: COLORS.surface,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10,
    },
    constraintContainer: {
        flex: 1,
        alignItems: 'center',
        paddingHorizontal: 16,
    },
    constraintLabel: {
        fontSize: 10,
        color: COLORS.textDim,
        letterSpacing: 2,
        marginBottom: 2,
    },
    constraintText: {
        fontSize: 18,
        fontWeight: 'bold',
        color: COLORS.accent,
        textAlign: 'center',
    },
    menuButton: {
        padding: 16,
    },
    progressBarContainer: {
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: 12,
        backgroundColor: 'rgba(0,0,0,0.1)',
        zIndex: 5,
    },
    rotatedProgressBar: {
        bottom: 0,
        top: undefined,
    },
    progressBarFill: {
        height: '100%',
        backgroundColor: COLORS.accent,
    },
});
