import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { RotateCcw, Home, Trophy, AlertTriangle, Clock } from 'lucide-react-native';

export default function ResultScreen() {
    const router = useRouter();
    const { winner, winReason, resetGame, startGame } = useGame();

    const handleRematch = () => {
        // Reset and start immediately with same settings (assumed handled by context or we just reset state and nav to game)
        // Actually startGame resets the state internally if we pass params, or we just call resetGame then navigate?
        // Let's modify startGame to handle restart or just call startGame again.
        // In context, startGame resets refs and state.
        startGame();
        router.replace('/game');
    };

    const handleHome = () => {
        resetGame();
        router.replace('/');
    };

    if (!winner) {
        // Fallback if accessed directly
        return (
            <SafeAreaView style={styles.container}>
                <Text style={styles.text}>No Game Results</Text>
                <TouchableOpacity style={styles.buttonSecondary} onPress={handleHome}>
                    <Text style={styles.buttonTextSecondary}>Go Home</Text>
                </TouchableOpacity>
            </SafeAreaView>
        );
    }

    const winnerColor = winner === 1 ? COLORS.player1 : COLORS.player2;
    const reasonText = winReason === 'time' ? 'TIME UP' : 'PENALTY';
    const reasonIcon = winReason === 'time' ? <Clock size={48} color={COLORS.textDim} /> : <AlertTriangle size={48} color={COLORS.danger} />;

    return (
        <SafeAreaView style={styles.container}>
            <View style={styles.content}>

                <View style={styles.iconContainer}>
                    <Trophy size={80} color={COLORS.accent} />
                    <View style={styles.reasonBadge}>
                        {reasonIcon}
                    </View>
                </View>

                <Text style={styles.winnerText}>WINNER</Text>
                <Text style={[styles.playerText, { color: winnerColor }]}>PLAYER {winner}</Text>

                <View style={styles.reasonContainer}>
                    <Text style={styles.reasonLabel}>VICTORY BY</Text>
                    <Text style={styles.reasonValue}>{reasonText}</Text>
                </View>

                <View style={styles.buttonGroup}>
                    <TouchableOpacity style={[styles.button, { backgroundColor: COLORS.success }]} onPress={handleRematch}>
                        <RotateCcw size={24} color={COLORS.background} />
                        <Text style={styles.buttonText}>再戦する</Text>
                    </TouchableOpacity>

                    <TouchableOpacity style={styles.buttonSecondary} onPress={handleHome}>
                        <Home size={24} color={COLORS.text} />
                        <Text style={styles.buttonTextSecondary}>設定に戻る</Text>
                    </TouchableOpacity>
                </View>

            </View>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: COLORS.background,
    },
    content: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
    },
    iconContainer: {
        alignItems: 'center',
        marginBottom: 32,
        position: 'relative',
    },
    reasonBadge: {
        marginTop: 16,
    },
    winnerText: {
        fontSize: 24,
        color: COLORS.textDim,
        letterSpacing: 4,
        fontWeight: 'bold',
    },
    playerText: {
        fontSize: 64,
        fontWeight: '900',
        marginBottom: 48,
        textShadowColor: 'rgba(0,0,0,0.5)',
        textShadowOffset: { width: 0, height: 4 },
        textShadowRadius: 12,
    },
    reasonContainer: {
        alignItems: 'center',
        marginBottom: 64,
    },
    reasonLabel: {
        fontSize: 14,
        color: COLORS.textDim,
        marginBottom: 4,
    },
    reasonValue: {
        fontSize: 32,
        fontWeight: 'bold',
        color: COLORS.text,
    },
    buttonGroup: {
        width: '100%',
        gap: 16,
    },
    button: {
        width: '100%',
        paddingVertical: 20,
        borderRadius: 16,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        elevation: 4,
    },
    buttonSecondary: {
        width: '100%',
        paddingVertical: 20,
        borderRadius: 16,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        backgroundColor: COLORS.surface,
        borderWidth: 1,
        borderColor: COLORS.surface,
    },
    buttonText: {
        fontSize: 20,
        fontWeight: 'bold',
        color: COLORS.background,
    },
    buttonTextSecondary: {
        fontSize: 20,
        fontWeight: 'bold',
        color: COLORS.text,
    },
    text: {
        color: COLORS.text,
    },
});
