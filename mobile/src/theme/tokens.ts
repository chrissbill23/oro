/**
 * The design tokens of oro.pen (its variables), light and dark: the names are the design's, so that a change there
 * finds its place here.
 */
export type ColorName =
  | 'background' | 'surface' | 'surfaceRaised' | 'textPrimary' | 'textSecondary' | 'textTertiary' | 'border'
  | 'accent' | 'accentPressed' | 'accentSoft' | 'accentContrast' | 'accent2' | 'accent2Contrast'
  | 'bubbleMine' | 'bubbleMine2' | 'bubbleMineText' | 'bubbleTheirs' | 'bubbleTheirsText'
  | 'online' | 'danger' | 'dangerSoft' | 'warning' | 'warningSoft' | 'success' | 'successSoft'
  | 'scrim' | 'glass' | 'glassStroke' | 'bgTop' | 'bgBottom' | 'glowPurple' | 'glowPurple0' | 'glowGreen' | 'glowGreen0'
  | 'avatar1' | 'avatar2' | 'avatar3' | 'avatar4' | 'avatar5' | 'avatar6';

export type Palette = Record<ColorName, string>;

export const light: Palette = {
  background: '#ECEBFF', surface: '#FFFFFFB8', surfaceRaised: '#1E1A4D0F', textPrimary: '#16123A',
  textSecondary: '#45416B', textTertiary: '#5C5884', border: '#1E1A4D1A', accent: '#1E3A8A', accentPressed: '#172C6B',
  accentSoft: '#1E3A8A1F', accentContrast: '#FFFFFF', accent2: '#0B6B4A', accent2Contrast: '#FFFFFF',
  bubbleMine: '#F1F2F6EB', bubbleMine2: '#C9CDD8E0', bubbleMineText: '#16123A', bubbleTheirs: '#FFFFFFD1',
  bubbleTheirsText: '#16123A', online: '#16A34A', danger: '#C8243F', dangerSoft: '#C8243F1A', warning: '#7A4F00',
  warningSoft: '#F5B7001F', success: '#0B6B4A', successSoft: '#0B6B4A1A', scrim: '#16123A59', glass: '#FFFFFFA6',
  glassStroke: '#FFFFFFE6', bgTop: '#EFE9FF', bgBottom: '#E3EDFF', glowPurple: '#C4B5FD80', glowPurple0: '#C4B5FD00',
  glowGreen: '#A7F3D080', glowGreen0: '#A7F3D000', avatar1: '#E4D9FF', avatar2: '#D3F2E4', avatar3: '#D6E4FF',
  avatar4: '#EADCFB', avatar5: '#CFEFF5', avatar6: '#DDE3F7',
};

export const dark: Palette = {
  background: '#110C33', surface: '#FFFFFF12', surfaceRaised: '#FFFFFF1F', textPrimary: '#F4F2FF',
  textSecondary: '#BDB7DE', textTertiary: '#ACA6D2', border: '#FFFFFF1F', accent: '#BBA6FF', accentPressed: '#A28BF5',
  accentSoft: '#BBA6FF2E', accentContrast: '#16093A', accent2: '#3DDC97', accent2Contrast: '#03241A',
  bubbleMine: '#C3C7D485', bubbleMine2: '#7A80945C', bubbleMineText: '#F4F2FF', bubbleTheirs: '#FFFFFF17',
  bubbleTheirsText: '#F4F2FF', online: '#4ADE80', danger: '#FF8597', dangerSoft: '#FF859724', warning: '#FBBF24',
  warningSoft: '#FBBF2424', success: '#4ADE80', successSoft: '#4ADE8024', scrim: '#03021099', glass: '#1A1442A6',
  glassStroke: '#FFFFFF24', bgTop: '#1B0B45', bgBottom: '#06142E', glowPurple: '#7C3AED66', glowPurple0: '#7C3AED00',
  glowGreen: '#10B98140', glowGreen0: '#10B98100', avatar1: '#4C2F96', avatar2: '#1F5B47', avatar3: '#23407E',
  avatar4: '#5A2D82', avatar5: '#1E5563', avatar6: '#33386E',
};

export const space = { 1: 4, 2: 8, 3: 12, 4: 16, 5: 20, 6: 24, 7: 28, 8: 32 } as const;

export const radius = { sm: 8, md: 12, lg: 20, bubble: 18, full: 999 } as const;

export const fontSize = { title: 30, headline: 17, body: 16, bodySmall: 14, caption: 12, label: 13 } as const;

/** Plus Jakarta Sans, its weights as loaded (expo-font): a family per weight on Android and the web */
export const font = {
  regular: 'PlusJakartaSans_400Regular',
  regularItalic: 'PlusJakartaSans_400Regular_Italic',
  medium: 'PlusJakartaSans_500Medium',
  semibold: 'PlusJakartaSans_600SemiBold',
  bold: 'PlusJakartaSans_700Bold',
  extrabold: 'PlusJakartaSans_800ExtraBold',
} as const;

export type FontWeight = keyof typeof font;

/** the colors of the avatars without a photo, chosen by the user id: the same person, the same color */
export const avatarColors: ColorName[] = ['avatar1', 'avatar2', 'avatar3', 'avatar4', 'avatar5', 'avatar6'];
