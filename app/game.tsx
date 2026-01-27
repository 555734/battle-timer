import React, { useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AlertTriangle } from 'lucide-react-native';

const formatTime = (seconds: number) => {
    const s = Math.floor(seconds);
    const ms = Math.floor((seconds % 1) * 10);
    return `${s}.${ms}`;
};

export default function GameScreen() {
    const router = useRouter();
    const {
        timers,
        activePlayer,
        status,
        currentConstraint,
        switchTurn,
        triggerPenalty,
        initialTime
    } = useGame();

    useEffect(() => {
        if (status === 'ended') {
            console.log('[Debug] Game ended, navigating to result');
            router.replace('/result');
        }
    }, [status]);

    if (status === 'ended') return null;

    return (
        <SafeAreaView style={styles.container}>
            {/* 縛り条件表示 */}
            <View style={styles.constraintHeader}>
                <Text style={styles.constraintLabel}>CONDITION</Text>
                <Text style={styles.constraintText}>{currentConstraint?.text || '...'}</Text>
            </View>

            <ScrollView contentContainerStyle={styles.playerList}>
                {timers.map((time, index) => {
                    const pId = index + 1;
                    const isActive = activePlayer === pId;
                    const progress = (time / initialTime) * 100;

                    return (
                        <TouchableOpacity
                            key={pId}
                            style={[styles.playerCard, isActive && styles.activeCard]}
                            onPress={() => {
                                if (isActive) {
                                    console.log(`[Debug] Player ${pId} tapped to end turn`);
                                    switchTurn();
                                }
                            }}
                            activeOpacity={0.7}
                        >
                            <View style={[styles.progressBar, { width: `${progress}%`, backgroundColor: isActive ? COLORS.accent : COLORS.surface }]} />

                            <View style={styles.cardContent}>
                                <Text style={[styles.playerLabel, isActive && styles.activeText]}>PLAYER {pId}</Text>
                                <Text style={[styles.timerText, isActive && styles.activeTimerText]}>{formatTime(time)}</Text>

                                {!isActive && (
                                    <TouchableOpacity
                                        style={styles.penaltyButton}
                                        onPress={() => {
                                            console.log(`[Debug] Penalty triggered for Player ${pId}`);
                                            triggerPenalty(pId);
                                        }}
                                    >
                                        <AlertTriangle size={18} color={COLORS.danger} />
                                        <Text style={styles.penaltyText}>指摘</Text>
                                    </TouchableOpacity>
                                )}
                            </View>
                        </TouchableOpacity>
                    );
                })}
            </ScrollView>

            {activePlayer && (
                <View style={styles.footer}>
                    <Text style={styles.footerText}>PLAYER {activePlayer} のターン</Text>
                </View>
            )}
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: COLORS.background },
    constraintHeader: { padding: 20, alignItems: 'center', backgroundColor: COLORS.surface },
    constraintLabel: { fontSize: 12, color: COLORS.textDim, letterSpacing: 2 },
    constraintText: { fontSize: 22, fontWeight: 'bold', color: COLORS.accent, textAlign: 'center', marginTop: 4 },
    playerList: { padding: 16, gap: 12 },
    playerCard: {
        height: 100, backgroundColor: COLORS.surface, borderRadius: 16,
        overflow: 'hidden', justifyContent: 'center', position: 'relative'
    },
    activeCard: { height: 160, borderColor: COLORS.accent, borderWidth: 2 },
    progressBar: { position: 'absolute', left: 0, top: 0, bottom: 0, opacity: 0.2 },
    cardContent: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20 },
    playerLabel: { fontSize: 18, fontWeight: 'bold', color: COLORS.textDim },
    activeText: { color: COLORS.background, fontSize: 24 },
    timerText: { fontSize: 32, fontWeight: '900', color: COLORS.text, fontVariant: ['tabular-nums'] },
    activeTimerText: { fontSize: 54, color: COLORS.background },
    penaltyButton: { flexDirection: 'row', alignItems: 'center', padding: 8, backgroundColor: 'rgba(213,0,0,0.1)', borderRadius: 8 },
    penaltyText: { color: COLORS.danger, fontWeight: 'bold', marginLeft: 4 },
    footer: { padding: 20, alignItems: 'center', borderTopWidth: 1, borderColor: COLORS.surface },
    footerText: { color: COLORS.accent, fontWeight: 'bold', fontSize: 18 }
});