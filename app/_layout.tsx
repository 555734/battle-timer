import { Stack } from 'expo-router';
import { GameProvider } from '../context/GameContext';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

export default function Layout() {
  return (
    <SafeAreaProvider>
      <GameProvider>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="index" />
          <Stack.Screen name="game" />
          <Stack.Screen name="result" />
        </Stack>
        <StatusBar style="light" />
      </GameProvider>
    </SafeAreaProvider>
  );
}
