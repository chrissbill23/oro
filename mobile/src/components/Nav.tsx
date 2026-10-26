import type { BottomTabBarProps } from 'expo-router/tabs';
import * as Haptics from 'expo-haptics';
import { router } from 'expo-router';
import { PropsWithChildren, ReactNode } from 'react';
import { Modal, Platform, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LiveStatus } from '../live/LiveProvider';
import { useTheme } from '../theme/ThemeProvider';
import { Avatar, Badge } from './Basics';
import { Glass } from './Glass';
import { Icon, IconName } from './Icon';
import { T } from './Text';

/** NavBar/Large: the title of a tab (30, extra bold), its actions on the right; a subtitle (the connection) under it */
export function LargeNav({ title, right, subtitle }: { title: string; right?: ReactNode; subtitle?: ReactNode }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={{ paddingTop: insets.top + 8, paddingHorizontal: 16, paddingBottom: 8 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 44 }}>
        <T variant="title" accessibilityRole="header">{title}</T>
        <View style={{ flexDirection: 'row', gap: 8 }}>{right}</View>
      </View>
      {subtitle}
    </View>
  );
}

/** NavBar/Inline: back, a centered title, an action; 52 high */
export function InlineNav({ title, right, onBack, backLabel }: {
  title: string; right?: ReactNode; onBack?: () => void; backLabel?: string;
}) {
  const insets = useSafeAreaInsets();
  return (
    <View style={{ paddingTop: insets.top }}>
      <View style={{ height: 52, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8 }}>
        <View style={{ flex: 1, alignItems: 'flex-start' }}>
          <BackButton onPress={onBack} label={backLabel} />
        </View>
        <T variant="headline" numberOfLines={1} accessibilityRole="header" style={{ flex: 2, textAlign: 'center' }}>{title}</T>
        <View style={{ flex: 1, alignItems: 'flex-end' }}>{right}</View>
      </View>
    </View>
  );
}

export function BackButton({ onPress, label, count }: { onPress?: () => void; label?: string; count?: number }) {
  const back = onPress ?? (() => (router.canGoBack() ? router.back() : router.replace('/')));
  return (
    <Pressable accessibilityRole="button" accessibilityLabel="Back" hitSlop={8} onPress={back}
      style={{ flexDirection: 'row', alignItems: 'center', gap: 2, height: 44, paddingRight: 8 }}>
      <Icon name={Platform.OS === 'android' ? 'arrow-left' : 'chevron-left'} size={26} color="accent" />
      {label ? <T color="accent" weight="medium">{label}</T> : null}
      {count ? <Badge count={count} /> : null}
    </Pressable>
  );
}

/** a line under the title when not live: offline, connecting, updating (design: J) */
export function ConnectionLine({ status }: { status: LiveStatus }) {
  if (status === 'live') {
    return null;
  }
  const text = status === 'offline' ? 'Waiting for network' : status === 'updating' ? 'Updating…' : 'Connecting…';
  return (
    <View accessibilityLiveRegion="polite" style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
      <Icon name={status === 'offline' ? 'wifi-off' : 'loader-circle'} size={14} color="textSecondary" />
      <T variant="caption" weight="semibold" color="textSecondary">{text}</T>
    </View>
  );
}

/** ConversationHeader: glass, 60 high: back (with the unread count elsewhere), the avatar, the name, online */
export function ConversationHeader({
  name, id, group, online, subtitle, unread, onInfo, right,
}: {
  name: string; id: string; group: boolean; online?: boolean; subtitle?: string; unread?: number; onInfo?: () => void;
  right?: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  return (
    <Glass edge={false} style={{ paddingTop: insets.top, borderBottomWidth: 1, borderBottomColor: colors.border }}>
      <View style={{ height: 60, flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 8 }}>
        <BackButton count={unread} />
        <Pressable accessibilityRole="button" accessibilityLabel={`${name}, info`} onPress={onInfo}
          style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Avatar name={name} id={id} group={group} online={online} />
          <View style={{ flex: 1 }}>
            <T variant="headline" numberOfLines={1}>{name}</T>
            {subtitle ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
                {online ? <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: colors.online }} /> : null}
                <T variant="caption" color={online ? 'success' : 'textSecondary'} numberOfLines={1}>{subtitle}</T>
              </View>
            ) : null}
          </View>
        </Pressable>
        {right}
      </View>
    </Glass>
  );
}

const tabIcons: Record<string, IconName> = { index: 'message-circle', requests: 'inbox', you: 'circle-user-round' };
const tabLabels: Record<string, string> = { index: 'Chats', requests: 'Requests', you: 'You' };

/** TabBar: a glass capsule (62 high, radius 31) floating over the list; the active tab on accent-soft; badges */
export function CapsuleTabBar({ state, emitter, navigateToTab, descriptors }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  return (
    <View pointerEvents="box-none" style={{ position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 16,
      paddingBottom: Math.max(insets.bottom, 12) }}>
      <Glass style={{ height: 62, borderRadius: 31, shadowColor: '#1E1A4D', shadowOpacity: 0.12, shadowRadius: 20,
        shadowOffset: { width: 0, height: 8 } }}>
        <View accessibilityRole="tablist" style={{ flex: 1, flexDirection: 'row', padding: 6, gap: 4 }}>
          {state.routes.map((route, i) => {
            const active = state.index === i;
            const badge = descriptors[route.key].options.tabBarBadge;
            return (
              <Pressable key={route.key} accessibilityRole="tab" accessibilityState={{ selected: active }}
                accessibilityLabel={`${tabLabels[route.name] ?? route.name}${badge ? `, ${badge} new` : ''}`}
                onPress={() => {
                  const event = emitter.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
                  if (!active && !event.defaultPrevented) {
                    void Haptics.selectionAsync().catch(() => undefined);
                    navigateToTab(route.key);
                  }
                }}
                style={{ flex: 1, borderRadius: 25, alignItems: 'center', justifyContent: 'center', gap: 2,
                  backgroundColor: active ? colors.accentSoft : 'transparent' }}>
                <View>
                  <Icon name={tabIcons[route.name] ?? 'circle-dashed'} size={22} color={active ? 'accent' : 'textSecondary'} />
                  {badge ? (
                    <View style={{ position: 'absolute', top: -6, right: -14 }}>
                      <Badge count={Number(badge)} />
                    </View>
                  ) : null}
                </View>
                <T size={10} weight="bold" color={active ? 'accent' : 'textSecondary'}>{tabLabels[route.name] ?? route.name}</T>
              </Pressable>
            );
          })}
        </View>
      </Glass>
    </View>
  );
}

export interface SheetAction {
  label: string;
  icon?: IconName;
  destructive?: boolean;
  onPress: () => void;
}

/** an action sheet from the bottom: a header (the reactions bar), the actions, Cancel */
export function ActionSheet({ visible, onClose, title, header, actions, note }: PropsWithChildren<{
  visible: boolean; onClose: () => void; title?: string; header?: ReactNode; actions: SheetAction[]; note?: string;
}>) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable accessibilityLabel="Close" onPress={onClose} style={{ flex: 1, backgroundColor: colors.scrim, justifyContent: 'flex-end' }}>
        <Pressable onPress={() => undefined} style={{ backgroundColor: colors.background, borderTopLeftRadius: 20,
          borderTopRightRadius: 20, paddingBottom: insets.bottom + 8, paddingTop: 8, maxHeight: '80%' }}>
          <View style={{ alignSelf: 'center', width: 36, height: 5, borderRadius: 3, backgroundColor: colors.border, marginBottom: 8 }} />
          {title ? <T variant="label" color="textSecondary" align="center" style={{ paddingBottom: 8 }}>{title}</T> : null}
          {header}
          <ScrollView>
            {actions.map((a) => (
              <Pressable key={a.label} accessibilityRole="button" onPress={() => {
                onClose();
                a.onPress();
              }} style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14,
                paddingHorizontal: 20, backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
                {a.icon ? <Icon name={a.icon} size={20} color={a.destructive ? 'danger' : 'textPrimary'} /> : null}
                <T weight="medium" color={a.destructive ? 'danger' : 'textPrimary'}>{a.label}</T>
              </Pressable>
            ))}
          </ScrollView>
          {note ? <T variant="caption" color="textSecondary" style={{ paddingHorizontal: 20, paddingTop: 4, lineHeight: 17 }}>{note}</T> : null}
          <Pressable accessibilityRole="button" onPress={onClose} style={{ marginHorizontal: 16, marginTop: 8, height: 52,
            borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surfaceRaised }}>
            <T weight="bold">Cancel</T>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** the bar of a sheet: Cancel, the title, its action */
export function ModalBar({ title, onCancel, cancel = 'Cancel', action, onAction, busy, disabled, top }: {
  title: string; onCancel: () => void; cancel?: string; action?: string; onAction?: () => void; busy?: boolean; disabled?: boolean; top: number;
}) {
  return (
    <View style={{ paddingTop: Math.max(top, 12), paddingHorizontal: 16, height: 52 + Math.max(top, 12), flexDirection: 'row', alignItems: 'center' }}>
      <Pressable accessibilityRole="button" onPress={onCancel} hitSlop={8} style={{ flex: 1 }}><T color="accent">{cancel}</T></Pressable>
      <T variant="headline" accessibilityRole="header">{title}</T>
      <View style={{ flex: 1, alignItems: 'flex-end' }}>
        {action ? (
          <Pressable accessibilityRole="button" disabled={disabled || busy} onPress={onAction} hitSlop={8}>
            <T weight="bold" color={disabled || busy ? 'textTertiary' : 'accent'}>{busy ? '…' : action}</T>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}
