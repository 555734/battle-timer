import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput } from 'react-native';
import { useRouter } from 'expo-router';
import { useGame, PlayerId } from '../context/GameContext';
import { COLORS } from '../constants/Settings';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Clock, Zap, Skull, Play } from 'lucide-react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const TIME_OPTIONS = [
    { label: '10秒', value: 10 },
    { label: '15秒', value: 15 },
    { label: '30秒', value: 30 },
    { label: '60秒', value: 60 },
];

const DIFFICULTY_OPTIONS = [
    { id: 'normal', label: 'ノーマル', icon: Zap },
    { id: 'hard', label: 'ハード', icon: Skull },
    { id: 'fun', label: 'パーティ', icon: Zap }, // Mapping types to UI labels
];

const SETTINGS_KEY = '@battle_timer_settings_v1';

export default function HomeScreen() {
    const router = useRouter();
    const { startGame } = useGame();

    const [selectedTime, setSelectedTime] = useState(180);
    const [difficulty, setDifficulty] = useState<'normal' | 'hard' | 'fun'>('normal');

    useEffect(() => {
        loadSettings();
    }, []);

    const loadSettings = async () => {
        try {
            const jsonValue = await AsyncStorage.getItem(SETTINGS_KEY);
            if (jsonValue != null) {
                const settings = JSON.parse(jsonValue);
                if (settings.time) setSelectedTime(settings.time);
                if (settings.difficulty) setDifficulty(settings.difficulty);
            }
        } catch (e) {
            // error reading value
            console.log('Failed to load settings', e);
        }
    };

    const saveSettings = async (time: number, diff: string) => {
        try {
            const jsonValue = JSON.stringify({ time, difficulty: diff });
            await AsyncStorage.setItem(SETTINGS_KEY, jsonValue);
        } catch (e) {
            // saving error
            console.log('Failed to save settings', e);
        }
    };

    const handleStart = () => {
        saveSettings(selectedTime, difficulty);
        startGame(selectedTime, difficulty);
        router.replace('/game');
    };

    return (
        <SafeAreaView style={styles.container}>
            <ScrollView contentContainerStyle={styles.content}>
                <View style={styles.header}>
                    <Text style={styles.title}>BATTLE TIMER</Text>
                    <Text style={styles.subtitle}>言葉の縛りで、白熱の対戦を。</Text>
                </View>

                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>1ターンあたりの時間</Text>
                    <View style={styles.optionsGrid}>
                        {TIME_OPTIONS.map((option) => (
                            <TouchableOpacity
                                key={option.value}
                                style={[
                                    styles.optionButton,
                                    selectedTime === option.value && styles.optionButtonActive,
                                ]}
                                onPress={() => setSelectedTime(option.value)}
                            >
                                <Clock size={20} color={selectedTime === option.value ? COLORS.background : COLORS.text} />
                                <Text
                                    style={[
                                        styles.optionText,
                                        selectedTime === option.value && styles.optionTextActive,
                                    ]}
                                >
                                    {option.label}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>
                <View style={styles.customInputContainer}>
                    <Text style={styles.customInputLabel}>または任意の秒数を入力</Text>
                    <TextInput
                        style={styles.customInput}
                        value={selectedTime.toString()}
                        onChangeText={(text) => {
                            const num = parseInt(text, 10);
                            if (!isNaN(num)) {
                                setSelectedTime(num);
                            } else if (text === '') {
                                setSelectedTime(0);
                            }
                        }}
                        keyboardType="number-pad"
                        maxLength={3}
                        placeholder="秒"
                        placeholderTextColor={COLORS.textDim}
                    />
                    <Text style={styles.customInputUnit}>秒</Text>
                </View>


                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>縛りレベル (Coming Soon)</Text>
                    <View style={styles.optionsGrid}>
                        {DIFFICULTY_OPTIONS.map((option) => (
                            <TouchableOpacity
                                key={option.id}
                                style={[
                                    styles.optionButton,
                                    difficulty === option.id && styles.optionButtonActive,
                                ]}
                                onPress={() => setDifficulty(option.id as 'normal' | 'hard' | 'fun')}
                            >
                                <option.icon size={20} color={difficulty === option.id ? COLORS.background : COLORS.text} />
                                <Text
                                    style={[
                                        styles.optionText,
                                        difficulty === option.id && styles.optionTextActive,
                                    ]}
                                >
                                    {option.label}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                    <Text style={styles.note}>※ 現在は全種類の縛りからランダムに出題されます</Text>
                </View>

                <TouchableOpacity style={styles.startButton} onPress={handleStart}>
                    <Text style={styles.startButtonText}>BATTLE START</Text>
                    <Play size={24} color={COLORS.background} fill={COLORS.background} />
                </TouchableOpacity>
            </ScrollView>
        </SafeAreaView >
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: COLORS.background,
    },
    content: {
        padding: 24,
        flexGrow: 1,
        justifyContent: 'center',
    },
    header: {
        marginBottom: 48,
        alignItems: 'center',
    },
    title: {
        fontSize: 42,
        fontWeight: '900',
        color: COLORS.accent,
        letterSpacing: 2,
        marginBottom: 8,
        textShadowColor: 'rgba(255, 215, 64, 0.3)',
        textShadowOffset: { width: 0, height: 2 },
        textShadowRadius: 10,
    },
    subtitle: {
        fontSize: 16,
        color: COLORS.textDim,
    },
    section: {
        marginBottom: 32,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: 'bold',
        color: COLORS.text,
        marginBottom: 16,
        marginLeft: 4,
    },
    optionsGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 12,
    },
    optionButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        paddingVertical: 12,
        paddingHorizontal: 20,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: COLORS.surface,
        backgroundColor: COLORS.surface,
        minWidth: '45%',
        justifyContent: 'center',
    },
    optionButtonActive: {
        backgroundColor: COLORS.accent,
        borderColor: COLORS.accent,
    },
    optionText: {
        fontSize: 16,
        fontWeight: '600',
        color: COLORS.text,
    },
    optionTextActive: {
        color: COLORS.background,
    },
    note: {
        marginTop: 12,
        fontSize: 12,
        color: COLORS.textDim,
        textAlign: 'center',
    },
    startButton: {
        marginTop: 24,
        backgroundColor: COLORS.success,
        paddingVertical: 20,
        borderRadius: 16,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        shadowColor: COLORS.success,
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 12,
        elevation: 8,
    },
    startButtonText: {
        fontSize: 24,
        fontWeight: 'bold',
        color: COLORS.background, // Dark text on bright button
        letterSpacing: 1,
    },
    customInputContainer: {
        marginTop: 20,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        backgroundColor: COLORS.surface,
        padding: 12,
        borderRadius: 12,
    },
    customInputLabel: {
        color: COLORS.textDim,
        fontSize: 14,
    },
    customInput: {
        backgroundColor: COLORS.background,
        color: COLORS.accent,
        fontSize: 24,
        fontWeight: 'bold',
        paddingVertical: 8,
        paddingHorizontal: 16,
        borderRadius: 8,
        minWidth: 80,
        textAlign: 'center',
    },
    customInputUnit: {
        color: COLORS.text,
        fontWeight: 'bold',
        fontSize: 16,
    },
});
