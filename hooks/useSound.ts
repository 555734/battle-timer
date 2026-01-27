import { useEffect, useState, useCallback } from 'react';
import { Audio } from 'expo-av';

type SoundType = 'tap' | 'switch' | 'win' | 'penalty';

// Placeholder sound map. In a real app, you would import assets here.
// const SOUND_MAP = {
//   tap: require('../assets/sounds/tap.mp3'),
//   ...
// };

export function useSound() {
    const [sound, setSound] = useState<Audio.Sound>();

    async function playSound(type: SoundType) {
        if (__DEV__) {
            console.log(`[Sound] Playing simple sound for: ${type}`);
        }

        // Logic to load and play sound:
        // try {
        //   const { sound } = await Audio.Sound.createAsync(SOUND_MAP[type]);
        //   setSound(sound);
        //   await sound.playAsync();
        // } catch (e) {
        //   console.log('Error playing sound', e);
        // }
    }

    useEffect(() => {
        return sound
            ? () => {
                sound.unloadAsync();
            }
            : undefined;
    }, [sound]);

    return { playSound };
}
