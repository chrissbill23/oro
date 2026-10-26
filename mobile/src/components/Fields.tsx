import { ComponentProps, useState } from 'react';
import { Pressable, TextInput, TextInputProps, View } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { font, fontSize, radius } from '../theme/tokens';
import { Icon } from './Icon';
import { T } from './Text';

/** TextField: a label, an input 52 high on surface, a helper or an error under it; a password shows and hides */
export function TextField({ label, helper, error, secure, style, ref, ...input }: TextInputProps & {
  label: string; helper?: string; error?: string; secure?: boolean; ref?: ComponentProps<typeof TextInput>['ref'];
}) {
  const { colors } = useTheme();
  const [hidden, setHidden] = useState(true);
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ gap: 6 }}>
      <T variant="label" color="textSecondary">{label}</T>
      <View
        style={{
          height: 52, paddingHorizontal: 16, gap: 8, flexDirection: 'row', alignItems: 'center',
          backgroundColor: colors.surface, borderRadius: radius.md, borderWidth: 1,
          borderColor: error ? colors.danger : focused ? colors.accent : colors.border,
        }}
      >
        <TextInput
          ref={ref}
          accessibilityLabel={label}
          placeholderTextColor={colors.textTertiary}
          secureTextEntry={secure && hidden}
          autoCapitalize={secure ? 'none' : input.autoCapitalize}
          onFocus={(e) => {
            setFocused(true);
            input.onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            input.onBlur?.(e);
          }}
          style={[{ flex: 1, fontFamily: font.regular, fontSize: fontSize.body, color: colors.textPrimary }, style]}
          {...input}
        />
        {secure ? (
          <Pressable accessibilityRole="button" accessibilityLabel={hidden ? 'Show password' : 'Hide password'} hitSlop={12}
            onPress={() => setHidden((h) => !h)}>
            <Icon name={hidden ? 'eye' : 'eye-off'} size={20} color="textSecondary" />
          </Pressable>
        ) : null}
      </View>
      {error || helper ? (
        <View style={{ flexDirection: 'row', gap: 6, alignItems: 'center' }}>
          {error ? <Icon name="circle-alert" size={14} color="danger" /> : null}
          <T variant="caption" color={error ? 'danger' : 'textSecondary'} style={{ flex: 1 }}>{error ?? helper}</T>
        </View>
      ) : null}
    </View>
  );
}

/** SearchField: 40 high on surface-raised, radius 12 */
export function SearchField({ value, onChangeText, placeholder = 'Search messages', autoFocus, onSubmit }: {
  value: string; onChangeText: (v: string) => void; placeholder?: string; autoFocus?: boolean; onSubmit?: () => void;
}) {
  const { colors } = useTheme();
  return (
    <View style={{ height: 40, paddingHorizontal: 12, gap: 8, flexDirection: 'row', alignItems: 'center',
      backgroundColor: colors.surfaceRaised, borderRadius: 12 }}>
      <Icon name="search" size={18} color="textSecondary" />
      <TextInput
        accessibilityLabel={placeholder}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textSecondary}
        autoFocus={autoFocus}
        returnKeyType="search"
        onSubmitEditing={onSubmit}
        style={{ flex: 1, fontFamily: font.regular, fontSize: fontSize.body, color: colors.textPrimary }}
      />
      {value ? (
        <Pressable accessibilityRole="button" accessibilityLabel="Clear" hitSlop={12} onPress={() => onChangeText('')}>
          <Icon name="circle-x" size={18} color="textTertiary" />
        </Pressable>
      ) : null}
    </View>
  );
}
