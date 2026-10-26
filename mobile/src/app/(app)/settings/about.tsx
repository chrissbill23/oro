import Constants from 'expo-constants';
import { View } from 'react-native';
import { Card, ListRow, NeedsBackend } from '../../../components/Basics';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';

/** I6 · About Oro */
export default function About() {
  const version = Constants.expoConfig?.version ?? '1.0.0';
  return (
    <Screen nav={<InlineNav title="About Oro" backLabel="You" />}>
      <View style={{ alignItems: 'center', gap: 4, paddingVertical: 16 }}>
        <T size={40} weight="extrabold" color="accent" style={{ letterSpacing: -1 }}>oro</T>
        <T variant="bodySmall" color="textSecondary">Version {version}</T>
      </View>
      <Card>
        <ListRow icon="shield" title="Privacy notice" chevron={false} />
        <ListRow icon="file-text" title="Terms of use" chevron={false} last />
      </Card>
      <NeedsBackend what="the privacy notice (legal)" />
      <T variant="caption" color="textSecondary" align="center">Oro is made by Asata. One account for Oro, Umoja and Mokili.</T>
    </Screen>
  );
}
