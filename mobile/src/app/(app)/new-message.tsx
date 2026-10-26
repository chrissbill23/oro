import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { oro } from '../../api/oro';
import { Backdrop } from '../../components/Backdrop';
import { Avatar, Card, ListRow, NeedsBackend, SectionHeader } from '../../components/Basics';
import { SearchField } from '../../components/Fields';
import { ModalBar } from '../../components/Nav';
import { Banner } from '../../components/Screen';
import { Spinner } from '../../components/States';
import { T } from '../../components/Text';
import { useContacts } from '../../lib/contacts';
import { errorText } from '../../lib/errors';
import { displayName } from '../../lib/people';
import { useTheme } from '../../theme/ThemeProvider';

/** G1 · New message: your contacts, a new group; finding someone new needs the backend */
export default function NewMessage() {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const contacts = useContacts();
  const [q, setQ] = useState('');
  const [error, setError] = useState<string | null>(null);
  const shown = contacts.ids.filter((id) => {
    const p = contacts.people.get(id);
    const needle = q.trim().toLowerCase();
    return !needle || displayName(p, id).toLowerCase().includes(needle) || p?.username.toLowerCase().includes(needle);
  });

  const open = async (userId: string) => {
    try {
      const c = await oro.openDirect(userId);
      router.dismiss();
      router.push({ pathname: '/chat/[id]', params: { id: c.id } });
    } catch (e) {
      setError(errorText(e));
    }
  };

  return (
    <Backdrop>
      <ModalBar title="New message" onCancel={() => router.back()} top={insets.top} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: insets.bottom + 24 }} keyboardShouldPersistTaps="handled">
        <SearchField value={q} onChangeText={setQ} placeholder="Find by username" />
        <NeedsBackend what="finding a person" />
        {error ? <Banner text={error} /> : null}
        <Card>
          <ListRow icon="users" title="New group" onPress={() => router.replace('/new-group')} />
          <ListRow icon="qr-code" title="Share my username or QR code" subtitle="Let people you know find you" last
            onPress={() => router.push('/settings/username')} />
        </Card>
        <SectionHeader label="Your contacts" />
        {contacts.loading ? <Spinner /> : (
          <Card>
            {shown.length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>Nobody yet: your contacts are the people you talk with.</T> : null}
            {shown.map((userId, i) => {
              const p = contacts.people.get(userId);
              return (
                <Pressable key={userId} accessibilityRole="button" onPress={() => open(userId)}
                  style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16,
                    backgroundColor: pressed ? colors.surfaceRaised : 'transparent',
                    borderBottomWidth: i < shown.length - 1 ? 1 : 0, borderBottomColor: colors.border })}>
                  <Avatar name={displayName(p, userId)} id={userId} />
                  <View style={{ flex: 1 }}>
                    <T weight="semibold">{displayName(p, userId)}</T>
                    {p ? <T variant="caption" color="textSecondary">@{p.username}</T> : null}
                  </View>
                </Pressable>
              );
            })}
          </Card>
        )}
      </ScrollView>
    </Backdrop>
  );
}
