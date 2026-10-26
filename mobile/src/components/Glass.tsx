import { BlurView } from 'expo-blur';
import { PropsWithChildren } from 'react';
import { Platform, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { ColorName } from '../theme/tokens';

/**
 * A glass surface: a blur of what is behind (expo-blur, intensity about 24) under a tint, with a 1 pt edge. Android
 * has no reliable blur: the tint over the background, solid (the design's fallback).
 */
export function Glass({
  children, style, tint = 'glass', edge = true,
}: PropsWithChildren<{ style?: StyleProp<ViewStyle>; tint?: ColorName; edge?: boolean }>) {
  const { colors, scheme } = useTheme();
  const solid = Platform.OS === 'android';
  const radius = (StyleSheet.flatten(style)?.borderRadius as number | undefined) ?? 0;
  return (
    <View style={[{ overflow: 'hidden' }, style]}>
      {solid ? (
        <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.background }]} />
      ) : (
        <BlurView intensity={24} tint={scheme === 'dark' ? 'dark' : 'light'} style={StyleSheet.absoluteFill} />
      )}
      <View style={[StyleSheet.absoluteFill, { backgroundColor: colors[tint] }]} />
      {edge ? (
        <View
          pointerEvents="none"
          style={[StyleSheet.absoluteFill, { borderWidth: 1, borderColor: colors.glassStroke, borderRadius: radius }]}
        />
      ) : null}
      {children}
    </View>
  );
}
