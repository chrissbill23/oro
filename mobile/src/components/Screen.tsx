import { PropsWithChildren, ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, View, ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme/ThemeProvider';
import { ColorName } from '../theme/tokens';
import { Backdrop } from './Backdrop';
import { Icon } from './Icon';
import { T } from './Text';

/** a screen on the backdrop: its bar, its content scrolling above the keyboard */
export function Screen({ children, nav, scroll = true, footer, contentStyle }: PropsWithChildren<{
  nav?: ReactNode; scroll?: boolean; footer?: ReactNode; contentStyle?: ViewStyle;
}>) {
  const insets = useSafeAreaInsets();
  return (
    <Backdrop>
      {nav}
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {scroll ? (
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={[{ padding: 16, paddingBottom: insets.bottom + 24, gap: 16 }, contentStyle]}>
            {children}
          </ScrollView>
        ) : (
          <View style={[{ flex: 1 }, contentStyle]}>{children}</View>
        )}
        {footer ? <View style={{ paddingHorizontal: 16, paddingBottom: insets.bottom + 12, gap: 8 }}>{footer}</View> : null}
      </KeyboardAvoidingView>
    </Backdrop>
  );
}

/** an alert in a form: danger (an error), warning, success */
export function Banner({ kind = 'danger', text, children }: PropsWithChildren<{ kind?: 'danger' | 'warning' | 'success'; text: string }>) {
  const soft = kind === 'danger' ? 'dangerSoft' : kind === 'warning' ? 'warningSoft' : 'successSoft';
  return (
    <Backdropless color={soft}>
      <View style={{ flexDirection: 'row', gap: 10, alignItems: 'flex-start' }}>
        <Icon name={kind === 'success' ? 'circle-check' : 'circle-alert'} size={18} color={kind} />
        <View style={{ flex: 1, gap: 8 }}>
          <T variant="bodySmall" weight="medium" color={kind} accessibilityLiveRegion="assertive" style={{ lineHeight: 20 }}>{text}</T>
          {children}
        </View>
      </View>
    </Backdropless>
  );
}

function Backdropless({ color, children }: PropsWithChildren<{ color: ColorName }>) {
  const { colors } = useTheme();
  return <View style={{ padding: 12, borderRadius: 12, backgroundColor: colors[color] }}>{children}</View>;
}

/** the title block of a form screen (A2, A5, A6) */
export function Heading({ title, body }: { title: string; body?: string }) {
  return (
    <View style={{ gap: 6, paddingTop: 8 }}>
      <T variant="title" accessibilityRole="header">{title}</T>
      {body ? <T color="textSecondary" style={{ lineHeight: 22 }}>{body}</T> : null}
    </View>
  );
}
