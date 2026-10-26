import { ActivityIndicator, Pressable, StyleProp, View, ViewStyle } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { ColorName, radius } from '../theme/tokens';
import { Icon, IconName } from './Icon';
import { T } from './Text';

export type ButtonKind = 'primary' | 'secondary' | 'text' | 'destructive';

const looks: Record<ButtonKind, { fill?: ColorName; pressed?: ColorName; text: ColorName }> = {
  primary: { fill: 'accent', pressed: 'accentPressed', text: 'accentContrast' },
  secondary: { fill: 'surfaceRaised', pressed: 'border', text: 'textPrimary' },
  text: { text: 'accent' },
  destructive: { fill: 'dangerSoft', pressed: 'border', text: 'danger' },
};

/** Button/Primary, /Secondary, /Text, /Destructive: 52 high, radius-md, label 16 bold; loading shows a spinner */
export function Button({
  label, onPress, kind = 'primary', icon, loading, disabled, style, accessibilityHint,
}: {
  label: string; onPress?: () => void; kind?: ButtonKind; icon?: IconName; loading?: boolean; disabled?: boolean;
  style?: StyleProp<ViewStyle>; accessibilityHint?: string;
}) {
  const { colors } = useTheme();
  const look = looks[kind];
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      accessibilityHint={accessibilityHint}
      onPress={inactive ? undefined : onPress}
      style={({ pressed }) => [
        {
          height: kind === 'text' ? 44 : 52,
          borderRadius: radius.md,
          backgroundColor: look.fill ? colors[pressed && look.pressed ? look.pressed : look.fill] : 'transparent',
          opacity: disabled ? 0.45 : 1,
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'row',
          gap: 8,
          paddingHorizontal: 16,
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={colors[look.text]} />
      ) : (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          {icon ? <Icon name={icon} size={20} color={look.text} /> : null}
          <T weight="bold" color={look.text}>{label}</T>
        </View>
      )}
    </Pressable>
  );
}

/** IconButton (plain, 22 accent) and IconButton/Filled (surface-raised circle, 20): 44 × 44 */
export function IconButton({
  name, onPress, filled, color, label, size,
}: { name: IconName; onPress?: () => void; filled?: boolean; color?: ColorName; label: string; size?: number }) {
  const { colors } = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={4}
      onPress={onPress}
      style={({ pressed }) => ({
        width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center',
        backgroundColor: filled ? colors.surfaceRaised : pressed ? colors.surfaceRaised : 'transparent',
        opacity: pressed && filled ? 0.7 : 1,
      })}
    >
      <Icon name={name} size={size ?? (filled ? 20 : 22)} color={color ?? (filled ? 'textPrimary' : 'accent')} />
    </Pressable>
  );
}
