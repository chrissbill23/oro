import { BlurView } from 'expo-blur';
import { ReactNode, useState } from 'react';
import { Modal, Platform, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme/ThemeProvider';
import { Anchor } from './Chat';
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
 * D2 · Long press: the message lifted in place over a blurred scrim, the ReactionBar just above it (six, then ＋ for
 * more) and the MessageActions just below it, on the side of the message. It moves only when the screen has no room
 * above or below. A tap outside closes it.
 */
export function MessageMenu({
  visible, mine, anchor, preview, reacted, onReact, actions, onClose,
}: {
  visible: boolean; mine: boolean; anchor: Anchor | null; preview: ReactNode; reacted?: string[];
  onReact?: (emoji: string) => void; actions: MenuAction[]; onClose: () => void;
}) {
  const { colors, scheme } = useTheme();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [more, setMore] = useState(false);
  const close = () => {
    setMore(false);
    onClose();
  };

  // the place of each part: the bubble where it was, unless the bar or the actions would leave the screen
  const gap = 8;
  const edge = 12;
  const gridH = Math.min(260, height * 0.35);
  const barH = !onReact ? 0 : more ? 36 + gridH + 6 : 56;
  const actionsH = actions.length * 46;
  const top0 = insets.top + edge + (barH ? barH + gap : 0);
  const bottom0 = height - insets.bottom - edge - (actionsH ? actionsH + gap : 0);
  const at = anchor ?? { x: edge, y: height / 2, width: width - 2 * edge, height: 0 };
  const bubbleH = Math.max(0, Math.min(at.height, bottom0 - top0));
  const bubbleTop = Math.max(top0, Math.min(at.y, bottom0 - bubbleH));
  const side = mine ? { right: Math.max(edge, width - at.x - at.width) } : { left: Math.max(edge, at.x) };
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
        {onReact ? (
          <Animated.View entering={ZoomIn.duration(180)} style={[styles.shadow, styles.abs, mine ? { right: edge } : { left: edge }, { top: bubbleTop - gap - barH, maxWidth: width - 2 * edge, borderRadius: more ? 20 : 999 }]}>
            {more ? (
              <Glass tint="surface" style={[styles.panel, { width: Math.min(width - 2 * edge, 360) }]}>
                <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingTop: 10 }}>
                  <T variant="caption" weight="bold" color="textSecondary" style={{ flex: 1 }}>Reactions</T>
                  <Pressable accessibilityRole="button" accessibilityLabel="Fewer reactions" hitSlop={10} onPress={() => setMore(false)}>
                    <Icon name="x" size={18} color="textSecondary" />
                  </Pressable>
                </View>
                <ScrollView style={{ height: gridH }} contentContainerStyle={styles.grid}>
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
        <View pointerEvents="none" style={[styles.abs, { top: bubbleTop, left: at.x, width: at.width, height: bubbleH, overflow: 'hidden' }]}>
          {preview}
        </View>
        {actions.length ? (
          <Animated.View entering={ZoomIn.duration(180)} style={[styles.shadow, styles.abs, side, { top: bubbleTop + bubbleH + gap, borderRadius: 16 }]}>
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
    </Modal>
  );
}

const styles = StyleSheet.create({
  abs: { position: 'absolute' },
  shadow: { shadowColor: '#0B0828', shadowOpacity: 0.18, shadowRadius: 24, shadowOffset: { width: 0, height: 8 } },
  bar: { flexDirection: 'row', alignItems: 'center', gap: 2, padding: 6, borderRadius: 999 },
  plus: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  panel: { borderRadius: 20, paddingBottom: 6 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', paddingHorizontal: 8, paddingTop: 6 },
  actions: { width: 250, borderRadius: 16 },
  action: { height: 46, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 12 },
});
