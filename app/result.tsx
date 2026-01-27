import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { RotateCcw, Home, Trophy } from 'lucide-react-native';

export default function ResultScreen() {
    const router = useRouter();
    const { winner, winReason, resetGame, startGame, playerCount, initialTime } = useGame();

    const handleRematch = () => {
        console.log('[Debug] Rematch requested');
        startGame(playerCount, initialTime);
        router.replace('/game');
    };

    const handleHome = () => {
        console.log('[Debug] Returning to home');
        resetGame();
        router.replace('/');
    };

    if (!winner) return null;

    return (
        <SafeAreaView style={styles.container}>
            <View style={styles.content}>
                <Trophy size={100} color={COLORS.accent} />
                <Text style={styles.winnerText}>WINNER</Text>
                <Text style={styles.playerText}>PLAYER {winner}</Text>
                <Text style={styles.reasonText}>{winReason === 'time' ? 'TIME UP!' : 'PENALTY!'}</Text>

                <View style={styles.buttonGroup}>
                    <TouchableOpacity style={styles.primaryButton} onPress={handleRematch}>
                        <RotateCcw size={24} color={COLORS.background} />
                        <Text style={styles.buttonText}>同じ設定で再戦</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.secondaryButton} onPress={handleHome}>
                        <Home size={24} color={COLORS.text} />
                        <Text style={styles.buttonTextSecondary}>トップに戻る</Text>
                    </TouchableOpacity>
                </View>
            </View>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: COLORS.background },
    content: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
    winnerText: { fontSize: 24, color: COLORS.textDim, marginTop: 24 },
    playerText: { fontSize: 64, fontWeight: '900', color: COLORS.accent, marginVertical: 10 },
    reasonText: { fontSize: 20, color: COLORS.danger, fontWeight: 'bold', marginBottom: 40 },
    buttonGroup: { width: '100%', gap: 16 },
    primaryButton: {
        backgroundColor: COLORS.success, padding: 20, borderRadius: 16,
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12
    },
    secondaryButton: {
        backgroundColor: COLORS.surface, padding: 20, borderRadius: 16,
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12
    },
    buttonText: { fontSize: 20, fontWeight: 'bold', color: COLORS.background },
    buttonTextSecondary: { fontSize: 20, fontWeight: 'bold', color: COLORS.text },
});