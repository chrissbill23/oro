import { Pressable, View } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { Avatar, Badge } from './Basics';
import { Icon } from './Icon';
import { T } from './Text';

/** ConversationRow: the avatar, the name and its time, the preview and its count; muted: a bell, a grey count */
export function ConversationRow({
  id, name, group, online, time, preview, unread, muted, typing, onPress, onLongPress,
}: {
  id: string; name: string; group: boolean; online?: boolean; time: string; preview: string; unread: number;
  muted?: boolean; typing?: string; onPress: () => void; onLongPress?: () => void;
}) {
  const { colors } = useTheme();
  const bold = unread > 0;
  return (
    <Pressable accessibilityRole="button" onPress={onPress} onLongPress={onLongPress}
      accessibilityLabel={`${name}${unread ? `, ${unread} unread` : ''}, ${typing ?? preview}, ${time}`}
      style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10,
        paddingHorizontal: 16, backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
      <Avatar name={name} id={id} group={group} online={online} />
      <View style={{ flex: 1, gap: 2 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <T weight="bold" numberOfLines={1} style={{ flex: 1 }}>{name}</T>
          {muted ? <Icon name="bell-off" size={14} color="textTertiary" /> : null}
          <T variant="caption" weight="semibold" color={bold && !muted ? 'accent2' : 'textTertiary'}>{time}</T>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <T variant="bodySmall" numberOfLines={1} color={typing ? 'accent' : bold ? 'textPrimary' : 'textSecondary'}
            weight={bold && !typing ? 'semibold' : 'regular'} italic={!!typing} style={{ flex: 1 }}>
            {typing ?? preview}
          </T>
          {unread > 0 ? <Badge count={unread} muted={muted} /> : null}
        </View>
      </View>
    </Pressable>
  );
}

/** RequestRow: who asks (a message request) or invites (a group), the first message; opens to accept or decline */
export function RequestRow({ id, name, kind, preview, time, onPress }: {
  id: string; name: string; kind: string; preview: string; time: string; onPress: () => void;
}) {
  const { colors } = useTheme();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${kind}: ${name}, ${preview}`} onPress={onPress}
      style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10,
        paddingHorizontal: 16, backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
      <Avatar name={name} id={id} />
      <View style={{ flex: 1, gap: 2 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <T weight="bold" numberOfLines={1} style={{ flex: 1 }}>{name}</T>
          <T variant="caption" color="textTertiary">{time}</T>
        </View>
        <T variant="caption" weight="bold" color="accent">{kind}</T>
        <T variant="bodySmall" color="textSecondary" numberOfLines={1}>{preview}</T>
      </View>
      <Icon name="chevron-right" size={18} color="textTertiary" />
    </Pressable>
  );
}
