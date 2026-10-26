import { Modal, Pressable, View } from 'react-native';
import { useTheme } from '../theme/ThemeProvider';
import { Button } from './Button';
import { Icon, IconName } from './Icon';
import { T } from './Text';

export interface DialogSpec {
  title: string;
  body: string;
  icon?: IconName;
  confirm?: string;
  destructive?: boolean;
  cancel?: string;
  onConfirm?: () => void;
  /** a second action under the first (J5: Open Settings, then Not now) */
  steps?: string[];
}

/** a dialog in the middle (D3b delete, E5 leave, F3 block, J5/J7/J8): a title, why, Cancel and the action */
export function Dialog({ spec, onClose }: { spec: DialogSpec | null; onClose: () => void }) {
  const { colors } = useTheme();
  return (
    <Modal visible={!!spec} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable accessibilityLabel="Close" onPress={onClose}
        style={{ flex: 1, backgroundColor: colors.scrim, alignItems: 'center', justifyContent: 'center', padding: 32 }}>
        {spec ? (
          <Pressable onPress={() => undefined} accessibilityRole="alert"
            style={{ width: '100%', maxWidth: 340, borderRadius: 20, padding: 20, gap: 12, backgroundColor: colors.background,
              borderWidth: 1, borderColor: colors.glassStroke }}>
            {spec.icon ? (
              <View style={{ width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', alignSelf: 'center',
                backgroundColor: spec.destructive ? colors.dangerSoft : colors.accentSoft }}>
                <Icon name={spec.icon} size={24} color={spec.destructive ? 'danger' : 'accent'} />
              </View>
            ) : null}
            <T variant="headline" align="center">{spec.title}</T>
            <T variant="bodySmall" color="textSecondary" align="center" style={{ lineHeight: 20 }}>{spec.body}</T>
            {spec.steps?.map((step, i) => (
              <View key={step} style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
                <View style={{ width: 22, height: 22, borderRadius: 11, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.accentSoft }}>
                  <T variant="caption" weight="bold" color="accent">{i + 1}</T>
                </View>
                <T variant="bodySmall">{step}</T>
              </View>
            ))}
            <View style={{ gap: 8, marginTop: 4 }}>
              {spec.confirm ? (
                <Button label={spec.confirm} kind={spec.destructive ? 'destructive' : 'primary'} onPress={() => {
                  onClose();
                  spec.onConfirm?.();
                }} />
              ) : null}
              <Button label={spec.cancel ?? 'Cancel'} kind="secondary" onPress={onClose} />
            </View>
          </Pressable>
        ) : null}
      </Pressable>
    </Modal>
  );
}
