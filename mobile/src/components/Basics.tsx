import { PropsWithChildren, ReactNode } from 'react';
import { Pressable, Switch, View } from 'react-native';
import { initials } from '../lib/people';
import { useTheme } from '../theme/ThemeProvider';
import { avatarColors, ColorName } from '../theme/tokens';
import { Icon, IconName } from './Icon';
import { T } from './Text';

const avatarText: Record<number, number> = { 24: 10, 40: 15, 56: 20, 96: 34 };

function colorOf(key: string): ColorName {
  let h = 0;
  for (let i = 0; i < key.length; i++) {
    h = (h * 31 + key.charCodeAt(i)) | 0;
  }
  return avatarColors[Math.abs(h) % avatarColors.length];
}

/** Avatar/24, /40, /56, /96: initials on a color of the person (photos: needs backend); the online dot: contacts only */
export function Avatar({ name, id, size = 40, online, group }: {
  name: string; id: string; size?: 24 | 40 | 56 | 96; online?: boolean; group?: boolean;
}) {
  const { colors } = useTheme();
  const dot = size <= 24 ? 8 : size >= 96 ? 18 : 12;
  return (
    <View accessibilityLabel={online ? `${name}, online` : name}
      style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: colors[colorOf(id)],
        alignItems: 'center', justifyContent: 'center' }}>
      {group && size >= 40 ? (
        <Icon name="users-round" size={size * 0.45} color="textPrimary" />
      ) : (
        <T weight="bold" size={avatarText[size]}>{initials(name)}</T>
      )}
      {online ? (
        <View style={{ position: 'absolute', right: -1, bottom: -1, width: dot, height: dot, borderRadius: dot / 2,
          backgroundColor: colors.online, borderWidth: 2, borderColor: colors.background }} />
      ) : null}
    </View>
  );
}

/** Badge: a count on accent-2, 22 high; muted: grey */
export function Badge({ count, muted }: { count: number; muted?: boolean }) {
  const { colors } = useTheme();
  return (
    <View style={{ minWidth: 22, height: 22, paddingHorizontal: 7, borderRadius: 11, alignItems: 'center',
      justifyContent: 'center', backgroundColor: muted ? colors.surfaceRaised : colors.accent2 }}>
      <T variant="caption" weight="bold" color={muted ? 'textSecondary' : 'accent2Contrast'}>
        {count > 99 ? '99+' : String(count)}
      </T>
    </View>
  );
}

/** SectionHeader: a label above a group of rows */
export function SectionHeader({ label }: { label: string }) {
  return (
    <View style={{ paddingTop: 16, paddingHorizontal: 16, paddingBottom: 4 }}>
      <T variant="label" weight="bold" color="textSecondary" accessibilityRole="header">{label}</T>
    </View>
  );
}

/** ListRow: an icon tile, a title and its subtitle, a value, a switch or a chevron; destructive in danger */
export function ListRow({
  icon, title, subtitle, value, toggle, onToggle, onPress, chevron = !!onPress, destructive, last, trailing,
}: {
  icon?: IconName; title: string; subtitle?: string; value?: string; toggle?: boolean; onToggle?: (v: boolean) => void;
  onPress?: () => void; chevron?: boolean; destructive?: boolean; last?: boolean; trailing?: ReactNode;
}) {
  const { colors } = useTheme();
  return (
    <Pressable
      accessibilityRole={toggle !== undefined ? 'switch' : onPress ? 'button' : undefined}
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => ({
        paddingVertical: 12, paddingHorizontal: 16, gap: 12, flexDirection: 'row', alignItems: 'center',
        borderBottomWidth: last ? 0 : 1, borderBottomColor: colors.border,
        backgroundColor: pressed ? colors.surfaceRaised : 'transparent',
      })}
    >
      {icon ? (
        <View style={{ width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center',
          backgroundColor: destructive ? colors.dangerSoft : colors.accentSoft }}>
          <Icon name={icon} size={18} color={destructive ? 'danger' : 'accent'} />
        </View>
      ) : null}
      <View style={{ flex: 1, gap: 2 }}>
        <T weight="medium" color={destructive ? 'danger' : 'textPrimary'}>{title}</T>
        {subtitle ? <T variant="label" weight="regular" color="textSecondary" style={{ lineHeight: 18 }}>{subtitle}</T> : null}
      </View>
      {value ? <T size={15} color="textSecondary">{value}</T> : null}
      {trailing}
      {toggle !== undefined ? (
        <Switch value={toggle} onValueChange={onToggle} trackColor={{ true: colors.accent, false: colors.surfaceRaised }}
          thumbColor="#FFFFFF" />
      ) : null}
      {chevron && toggle === undefined ? <Icon name="chevron-right" size={18} color="textTertiary" /> : null}
    </Pressable>
  );
}

/** a group of rows on a glass card */
export function Card({ children }: PropsWithChildren) {
  const { colors } = useTheme();
  return (
    <View style={{ backgroundColor: colors.surface, borderRadius: 16, borderWidth: 1, borderColor: colors.border,
      overflow: 'hidden' }}>
      {children}
    </View>
  );
}

/** EmptyState: an icon, a sentence, an action (no illustration) */
export function EmptyState({ icon, title, body, action }: {
  icon: IconName; title: string; body: string; action?: ReactNode;
}) {
  const { colors } = useTheme();
  return (
    <View style={{ alignItems: 'center', gap: 12, paddingHorizontal: 32, paddingVertical: 48 }}>
      <View style={{ width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center',
        backgroundColor: colors.accentSoft }}>
        <Icon name={icon} size={28} color="accent" />
      </View>
      <T variant="headline" align="center">{title}</T>
      <T variant="bodySmall" color="textSecondary" align="center" style={{ lineHeight: 20 }}>{body}</T>
      {action}
    </View>
  );
}

/** Tag/NeedsBackend: what Oro's API does not do yet */
export function NeedsBackend({ what }: { what: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignSelf: 'flex-start', alignItems: 'center', gap: 6, paddingVertical: 5,
      paddingHorizontal: 10, borderRadius: 999, borderWidth: 1, borderColor: colors.warning,
      backgroundColor: colors.warningSoft }}>
      <Icon name="server-cog" size={14} color="warning" />
      <T size={11} weight="bold" color="warning">Needs backend: {what}</T>
    </View>
  );
}
