import { LinearGradient } from 'expo-linear-gradient';
import { PropsWithChildren, useState } from 'react';
import { StyleSheet, View, ViewStyle } from 'react-native';
import Svg, { Defs, Ellipse, RadialGradient, Stop } from 'react-native-svg';
import { useTheme } from '../theme/ThemeProvider';

/** the screens' background: bg-top to bg-bottom, a purple glow top right and a green one bottom left (design: notes) */
export function Backdrop({ children, style }: PropsWithChildren<{ style?: ViewStyle }>) {
  const { colors } = useTheme();
  // sized in points from the layout: a viewBox in percent keeps a square on the web
  const [size, setSize] = useState({ w: 0, h: 0 });
  const { w, h } = size;
  return (
    <View style={[styles.fill, style]} onLayout={(e) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}>
      <LinearGradient colors={[colors.bgTop, colors.bgBottom]} style={StyleSheet.absoluteFill} />
      {w > 0 ? (
        <Svg style={StyleSheet.absoluteFill} width={w} height={h} pointerEvents="none">
          <Defs>
            <RadialGradient id="purple" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor={colors.glowPurple.slice(0, 7)} stopOpacity={alpha(colors.glowPurple)} />
              <Stop offset="1" stopColor={colors.glowPurple.slice(0, 7)} stopOpacity={0} />
            </RadialGradient>
            <RadialGradient id="green" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor={colors.glowGreen.slice(0, 7)} stopOpacity={alpha(colors.glowGreen)} />
              <Stop offset="1" stopColor={colors.glowGreen.slice(0, 7)} stopOpacity={0} />
            </RadialGradient>
          </Defs>
          <Ellipse cx={w * 0.95} cy={h * 0.02} rx={w * 0.65} ry={h * 0.3} fill="url(#purple)" />
          <Ellipse cx={w * 0.05} cy={h * 0.98} rx={w * 0.6} ry={h * 0.25} fill="url(#green)" />
        </Svg>
      ) : null}
      {children}
    </View>
  );
}

/** the alpha of a #RRGGBBAA color, 0 to 1 */
function alpha(hex: string): number {
  return hex.length === 9 ? parseInt(hex.slice(7, 9), 16) / 255 : 1;
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
