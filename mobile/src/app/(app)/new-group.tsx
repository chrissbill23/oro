import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { oro } from '../../api/oro';
import { Backdrop } from '../../components/Backdrop';
import { Avatar, Card, NeedsBackend, SectionHeader } from '../../components/Basics';
import { TextField } from '../../components/Fields';
import { Icon } from '../../components/Icon';
import { ModalBar } from '../../components/Nav';
import { Banner } from '../../components/Screen';
import { T } from '../../components/Text';
import { useContacts } from '../../lib/contacts';
import { errorText } from '../../lib/errors';
import { displayName } from '../../lib/people';
import { useTheme } from '../../theme/ThemeProvider';

/** G2 · New group: a name, a description, members from your contacts */
export default function NewGroup() {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const client = useQueryClient();
  const contacts = useContacts();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [members, setMembers] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const c = await oro.createGroup(name.trim(), description.trim() || undefined, members);
      await client.invalidateQueries({ queryKey: ['conversations'] });
      router.dismiss();
      router.push({ pathname: '/chat/[id]', params: { id: c.id } });
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Backdrop>
      <ModalBar title="New group" onCancel={() => router.back()} action="Create" onAction={create} busy={busy} disabled={!name.trim()} top={insets.top} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 16, paddingBottom: insets.bottom + 24 }} keyboardShouldPersistTaps="handled">
        <View style={{ alignItems: 'center', gap: 8 }}>
          <View style={{ width: 96, height: 96, borderRadius: 48, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.accentSoft }}>
            <Icon name="camera" size={32} color="accent" />
          </View>
          <T variant="caption" color="textSecondary">Group photo</T>
          <NeedsBackend what="group photos" />
        </View>
        {error ? <Banner text={error} /> : null}
        <TextField label="Name" value={name} onChangeText={setName} maxLength={128} placeholder="Family" />
        <TextField label="Description (optional)" value={description} onChangeText={setDescription} maxLength={1024} placeholder="Mum, Dad and the cousins" />
        <View>
          <SectionHeader label={`Members · ${members.length}`} />
          <T variant="caption" color="textSecondary" style={{ paddingHorizontal: 16, paddingBottom: 8 }}>From your contacts</T>
          <Card>
            {contacts.ids.length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>Nobody yet: your contacts are the people you talk with.</T> : null}
            {contacts.ids.map((userId) => {
              const p = contacts.people.get(userId);
              const on = members.includes(userId);
              return (
                <Pressable key={userId} accessibilityRole="checkbox" accessibilityState={{ checked: on }}
                  onPress={() => setMembers((m) => (on ? m.filter((x) => x !== userId) : [...m, userId]))}
                  style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16 }}>
                  <Avatar name={displayName(p, userId)} id={userId} />
                  <T weight="semibold" style={{ flex: 1 }}>{displayName(p, userId)}</T>
                  <View style={{ width: 24, height: 24, borderRadius: 12, alignItems: 'center', justifyContent: 'center',
                    backgroundColor: on ? colors.accent : 'transparent', borderWidth: on ? 0 : 2, borderColor: colors.border }}>
                    {on ? <Icon name="check" size={16} color="accentContrast" /> : null}
                  </View>
                </Pressable>
              );
            })}
          </Card>
        </View>
      </ScrollView>
    </Backdrop>
  );
}
