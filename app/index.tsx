import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Clock, Zap, Skull, Play, Users } from 'lucide-react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const TIME_OPTIONS = [
    { label: '10秒', value: 10 },
    { label: '15秒', value: 15 },
    { label: '30秒', value: 30 },
];

const PLAYER_OPTIONS = [2, 3, 4, 5, 6];

const SETTINGS_KEY = '@battle_timer_settings_v2';

export default function HomeScreen() {
    const router = useRouter();
    const { startGame } = useGame();

    const [selectedTime, setSelectedTime] = useState(15);
    const [playerCount, setPlayerCount] = useState(2);
    const [difficulty, setDifficulty] = useState<'normal' | 'hard' | 'fun'>('normal');

    useEffect(() => {
        loadSettings();
    }, []);

    const loadSettings = async () => {
        console.log('[Debug] Loading settings from AsyncStorage');
        try {
            const jsonValue = await AsyncStorage.getItem(SETTINGS_KEY);
            if (jsonValue != null) {
                const settings = JSON.parse(jsonValue);
                if (settings.time) setSelectedTime(settings.time);
                if (settings.playerCount) setPlayerCount(settings.playerCount);
            }
        } catch (e) {
            console.log('[Debug] Failed to load settings', e);
        }
    };

    const handleStart = async () => {
        console.log(`[Debug] handleStart: time=${selectedTime}, players=${playerCount}`);
        try {
            await AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify({ time: selectedTime, playerCount }));
        } catch (e) {
            console.log('[Debug] Failed to save settings', e);
        }
        startGame(playerCount, selectedTime, difficulty);
        router.replace('/game');
    };

    return (
        <SafeAreaView style={styles.container}>
            <ScrollView contentContainerStyle={styles.content}>
                <View style={styles.header}>
                    <Text style={styles.title}>BATTLE TIMER</Text>
                    <Text style={styles.subtitle}>複数人で、言葉の限界に挑め。</Text>
                </View>

                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>プレイヤー人数</Text>
                    <View style={styles.optionsGrid}>
                        {PLAYER_OPTIONS.map((num) => (
                            <TouchableOpacity
                                key={num}
                                style={[styles.optionButton, playerCount === num && styles.optionButtonActive]}
                                onPress={() => {
                                    console.log(`[Debug] Player count selected: ${num}`);
                                    setPlayerCount(num);
                                }}
                            >
                                <Users size={20} color={playerCount === num ? COLORS.background : COLORS.text} />
                                <Text style={[styles.optionText, playerCount === num && styles.optionTextActive]}>{num}人</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>

                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>1ターンあたりの時間</Text>
                    <View style={styles.optionsGrid}>
                        {TIME_OPTIONS.map((option) => (
                            <TouchableOpacity
                                key={option.value}
                                style={[styles.optionButton, selectedTime === option.value && styles.optionButtonActive]}
                                onPress={() => setSelectedTime(option.value)}
                            >
                                <Clock size={20} color={selectedTime === option.value ? COLORS.background : COLORS.text} />
                                <Text style={[styles.optionText, selectedTime === option.value && styles.optionTextActive]}>{option.label}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>

                <TouchableOpacity style={styles.startButton} onPress={handleStart}>
                    <Text style={styles.startButtonText}>BATTLE START</Text>
                    <Play size={24} color={COLORS.background} fill={COLORS.background} />
                </TouchableOpacity>
            </ScrollView>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: COLORS.background },
    content: { padding: 24, flexGrow: 1, justifyContent: 'center' },
    header: { marginBottom: 48, alignItems: 'center' },
    title: { fontSize: 40, fontWeight: '900', color: COLORS.accent, letterSpacing: 2 },
    subtitle: { fontSize: 14, color: COLORS.textDim, marginTop: 8 },
    section: { marginBottom: 32 },
    sectionTitle: { fontSize: 18, fontWeight: 'bold', color: COLORS.text, marginBottom: 16 },
    optionsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
    optionButton: {
        flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12,
        borderRadius: 12, backgroundColor: COLORS.surface, minWidth: '30%', justifyContent: 'center'
    },
    optionButtonActive: { backgroundColor: COLORS.accent },
    optionText: { fontSize: 16, fontWeight: '600', color: COLORS.text },
    optionTextActive: { color: COLORS.background },
    startButton: {
        backgroundColor: COLORS.success, padding: 20, borderRadius: 16,
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12
    },
    startButtonText: { fontSize: 24, fontWeight: 'bold', color: COLORS.background },
});