import { BlurView } from 'expo-blur';
import { ReactNode, useState } from 'react';
import { Modal, Platform, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, { FadeIn, ZoomIn } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme/ThemeProvider';
import { Glass } from './Glass';
import { Icon, IconName } from './Icon';
import { T } from './Text';

/** The six of the ReactionBar, in its order */
export const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏'];

/** Behind the ＋: more emoji, in a few rows */
const MORE_REACTIONS = [
  '😀', '😁', '😅', '🤣', '😊', '😇', '🙂', '😉', '😍', '🥰', '😘', '😋', '😜', '🤪', '🤗', '🤔',
  '🤨', '😐', '😏', '🙄', '😬', '😴', '😎', '🥳', '😕', '😟', '😭', '😤', '😡', '🤯', '😱', '🥺',
  '👏', '🙌', '👌', '✌️', '🤞', '💪', '👀', '🔥', '✨', '🎉', '💯', '✅', '❌', '⚽', '☕', '🍕',
  '💔', '💙', '💚', '💜', '🧡', '💛', '🤍', '⭐',
];

export type MenuAction = { label: string; icon: IconName; destructive?: boolean; onPress: () => void };

/**
 * D2 · Long press: the message lifted over a blurred scrim, the ReactionBar above it (six, then ＋ for more) and the
 * MessageActions below it, on the side of the message. A tap outside closes it.
 */
export function MessageMenu({
  visible, mine, preview, reacted, onReact, actions, onClose,
}: {
  visible: boolean; mine: boolean; preview: ReactNode; reacted?: string[]; onReact?: (emoji: string) => void;
  actions: MenuAction[]; onClose: () => void;
}) {
  const { colors, scheme } = useTheme();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [more, setMore] = useState(false);
  const close = () => {
    setMore(false);
    onClose();
  };

  const side = mine ? 'flex-end' : 'flex-start';
  const pick = (emoji: string) => {
    close();
    onReact?.(emoji);
  };
  const emoji = (e: string, size: number) => {
    const on = reacted?.includes(e);
    return (
      <Pressable key={e} accessibilityRole="button" accessibilityLabel={`React ${e}`} accessibilityState={{ selected: on }}
        onPress={() => pick(e)}
        style={({ pressed }) => ({ width: size, height: size, borderRadius: size / 2, alignItems: 'center', justifyContent: 'center',
          backgroundColor: on ? colors.accentSoft : pressed ? colors.surfaceRaised : 'transparent' })}>
        <T size={Math.round(size * 0.62)} style={{ lineHeight: Math.round(size * 0.8) }}>{e}</T>
      </Pressable>
    );
  };

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent onRequestClose={close}>
      <View style={StyleSheet.absoluteFill}>
        {Platform.OS === 'android' ? null : (
          <BlurView intensity={30} tint={scheme === 'dark' ? 'dark' : 'light'} style={StyleSheet.absoluteFill} />
        )}
        <Pressable accessibilityLabel="Close" onPress={close} style={[StyleSheet.absoluteFill, { backgroundColor: colors.scrim }]} />
        <View pointerEvents="box-none" style={{ flex: 1, justifyContent: 'center', gap: 10,
          paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }}>
          {onReact ? (
            <Animated.View entering={ZoomIn.duration(180)} style={[styles.shadow, { alignSelf: side, marginHorizontal: 12, maxWidth: width - 24, borderRadius: more ? 20 : 999 }]}>
              {more ? (
                <Glass tint="surface" style={[styles.panel, { width: Math.min(width - 24, 360) }]}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingTop: 10 }}>
                    <T variant="caption" weight="bold" color="textSecondary" style={{ flex: 1 }}>Reactions</T>
                    <Pressable accessibilityRole="button" accessibilityLabel="Fewer reactions" hitSlop={10} onPress={() => setMore(false)}>
                      <Icon name="x" size={18} color="textSecondary" />
                    </Pressable>
                  </View>
                  <ScrollView style={{ maxHeight: Math.min(260, height * 0.35) }} contentContainerStyle={styles.grid}>
                    {[...QUICK_REACTIONS, ...MORE_REACTIONS].map((e) => emoji(e, 40))}
                  </ScrollView>
                </Glass>
              ) : (
                <Glass tint="surface" style={styles.bar}>
                  {QUICK_REACTIONS.map((e) => emoji(e, 44))}
                  <Pressable accessibilityRole="button" accessibilityLabel="More reactions" onPress={() => setMore(true)}
                    style={({ pressed }) => [styles.plus, { backgroundColor: pressed ? colors.accentSoft : colors.surfaceRaised }]}>
                    <Icon name="plus" size={20} color="textSecondary" />
                  </Pressable>
                </Glass>
              )}
            </Animated.View>
          ) : null}
          <Animated.View entering={FadeIn.duration(150)} pointerEvents="none" style={{ maxHeight: height * 0.4, overflow: 'hidden' }}>
            {preview}
          </Animated.View>
          {actions.length ? (
            <Animated.View entering={ZoomIn.duration(180)} style={[styles.shadow, { alignSelf: side, marginHorizontal: 16, borderRadius: 16 }]}>
              <Glass tint="surface" style={styles.actions}>
                {actions.map((a, i) => (
                  <Pressable key={a.label} accessibilityRole="menuitem" onPress={() => {
                    close();
                    a.onPress();
                  }} style={({ pressed }) => [styles.action, {
                    borderBottomWidth: i < actions.length - 1 ? StyleSheet.hairlineWidth : 0, borderBottomColor: colors.border,
                    backgroundColor: pressed ? colors.surfaceRaised : 'transparent',
                  }]}>
                    <T color={a.destructive ? 'danger' : 'textPrimary'} style={{ flex: 1 }}>{a.label}</T>
                    <Icon name={a.icon} size={19} color={a.destructive ? 'danger' : 'textPrimary'} />
                  </Pressable>
                ))}
              </Glass>
            </Animated.View>
          ) : null}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  shadow: { shadowColor: '#0B0828', shadowOpacity: 0.18, shadowRadius: 24, shadowOffset: { width: 0, height: 8 } },
  bar: { flexDirection: 'row', alignItems: 'center', gap: 2, padding: 6, borderRadius: 999 },
  plus: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  panel: { borderRadius: 20, paddingBottom: 6 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', paddingHorizontal: 8, paddingTop: 6 },
  actions: { width: 250, borderRadius: 16 },
  action: { height: 46, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 12 },
});
