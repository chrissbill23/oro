import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const KEY = 'oro.refreshToken';

/**
 * The refresh token of the session: the Keychain (iOS) or the Keystore (Android), never plain storage. In a browser
 * (the web preview only) the local storage stands in.
 */
export const tokenStore = {
  async get(): Promise<string | null> {
    if (Platform.OS === 'web') {
      return globalThis.localStorage?.getItem(KEY) ?? null;
    }
    return SecureStore.getItemAsync(KEY);
  },
  async set(token: string): Promise<void> {
    if (Platform.OS === 'web') {
      globalThis.localStorage?.setItem(KEY, token);
      return;
    }
    await SecureStore.setItemAsync(KEY, token, { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK });
  },
  async clear(): Promise<void> {
    if (Platform.OS === 'web') {
      globalThis.localStorage?.removeItem(KEY);
      return;
    }
    await SecureStore.deleteItemAsync(KEY);
  },
};
