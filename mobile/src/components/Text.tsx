import { Text as RNText, TextProps, TextStyle } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { ColorName, font, fontSize, FontWeight } from '../theme/tokens';

export type Variant = keyof typeof fontSize;

const defaults: Record<Variant, FontWeight> = {
  title: 'extrabold', headline: 'bold', body: 'regular', bodySmall: 'regular', caption: 'regular', label: 'semibold',
};

export interface TProps extends TextProps {
  variant?: Variant;
  weight?: FontWeight;
  color?: ColorName;
  size?: number;
  italic?: boolean;
  align?: TextStyle['textAlign'];
}

/** text in Plus Jakarta Sans, sized by the design's scale; follows the user's text size (Dynamic Type, font scale) */
export function T({ variant = 'body', weight, color = 'textPrimary', size, italic, align, style, ...rest }: TProps) {
  const { colors } = useTheme();
  const w = weight ?? defaults[variant];
  const family = italic && w === 'regular' ? font.regularItalic : font[w];
  return (
    <RNText
      {...rest}
      style={[
        {
          fontFamily: family,
          fontSize: size ?? fontSize[variant],
          color: colors[color],
          textAlign: align,
          letterSpacing: variant === 'title' ? -0.6 : undefined,
        },
        style,
      ]}
    />
  );
}
