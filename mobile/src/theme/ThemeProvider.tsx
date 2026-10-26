import { createContext, PropsWithChildren, useContext, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import { dark, light, Palette } from './tokens';

export type Scheme = 'light' | 'dark';

export interface Theme {
  scheme: Scheme;
  colors: Palette;
}

const ThemeContext = createContext<Theme>({ scheme: 'light', colors: light });

/** light and dark follow the system (the design: M); `scheme` forces one (tests, previews) */
export function ThemeProvider({ children, scheme }: PropsWithChildren<{ scheme?: Scheme }>) {
  const system = useColorScheme();
  const current: Scheme = scheme ?? (system === 'dark' ? 'dark' : 'light');
  const theme = useMemo<Theme>(() => ({ scheme: current, colors: current === 'dark' ? dark : light }), [current]);
  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
  return useContext(ThemeContext);
}
