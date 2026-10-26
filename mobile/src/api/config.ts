/**
 * Oro's address: EXPO_PUBLIC_ORO_URL (e.g. https://umoja.example.org/oro); in development, the Oro of the dev stack.
 * A phone (Expo Go) reaches the computer by its address on the network, not localhost.
 */
export const ORO_URL = (process.env.EXPO_PUBLIC_ORO_URL ?? 'http://localhost:8082/oro').replace(/\/$/, '');
