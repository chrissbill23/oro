import { router } from 'expo-router';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Backdrop } from '../../../components/Backdrop';
import { Avatar, Card, ListRow } from '../../../components/Basics';
import { Icon } from '../../../components/Icon';
import { LargeNav } from '../../../components/Nav';
import { T } from '../../../components/Text';
import { displayName, usePeople } from '../../../lib/people';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

/** I0 · You: who you are, the settings, sign out */
export default function You() {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const { me, signOut } = useSession();
  const people = usePeople([me?.userId]);
  const person = people.get(me?.userId);
  const name = displayName(person, me?.userId);
  return (
    <Backdrop>
      <LargeNav title="You" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 16, paddingBottom: insets.bottom + 110 }}>
        <Card>
          <Pressable accessibilityRole="button" accessibilityLabel={`${name}, profile`} onPress={() => router.push('/settings/profile')}
            style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16,
              backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
            <Avatar name={name} id={me?.userId ?? 'me'} size={56} />
            <View style={{ flex: 1, gap: 2 }}>
              <T variant="headline">{name}</T>
              {person ? <T variant="bodySmall" color="textSecondary">@{person.username}</T> : null}
            </View>
            <Icon name="chevron-right" size={18} color="textTertiary" />
          </Pressable>
        </Card>
        <Card>
          <ListRow icon="bell" title="Notifications" onPress={() => router.push('/settings/notifications')} />
          <ListRow icon="shield" title="Privacy" onPress={() => router.push('/settings/privacy')} />
          <ListRow icon="database" title="Your data" onPress={() => router.push('/settings/data')} />
          <ListRow icon="monitor-smartphone" title="Sessions" onPress={() => router.push('/settings/sessions')} />
          <ListRow icon="info" title="About Oro" onPress={() => router.push('/settings/about')} last />
        </Card>
        <Card>
          <ListRow icon="log-out" title="Sign out" destructive chevron={false} onPress={signOut} last />
        </Card>
      </ScrollView>
    </Backdrop>
  );
}
