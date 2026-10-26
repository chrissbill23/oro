import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { oro } from '../../api/oro';
import { ReportCategory } from '../../api/types';
import { Backdrop } from '../../components/Backdrop';
import { Card, SectionHeader } from '../../components/Basics';
import { Button } from '../../components/Button';
import { TextField } from '../../components/Fields';
import { Icon } from '../../components/Icon';
import { ModalBar } from '../../components/Nav';
import { Banner } from '../../components/Screen';
import { Spinner } from '../../components/States';
import { T } from '../../components/Text';
import { errorText } from '../../lib/errors';
import { displayName, firstName, usePeople } from '../../lib/people';
import { useSession } from '../../session/SessionProvider';
import { useTheme } from '../../theme/ThemeProvider';

const CATEGORIES: { key: ReportCategory; title: string; body: string }[] = [
  { key: 'spam', title: 'Spam', body: 'Ads, scams, repeated unwanted messages' },
  { key: 'harassment', title: 'Harassment', body: 'Insults, threats, bullying' },
  { key: 'illegal', title: 'Illegal', body: 'Content that breaks the law' },
  { key: 'other', title: 'Other', body: 'Something else' },
];

/** H1 · Report: the messages to include (theirs), what's wrong, a note; H2 · sent, and block them too */
export default function Report() {
  const { conversationId, messageIds = '', userId } = useLocalSearchParams<{ conversationId: string; messageIds?: string; userId?: string }>();
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const client = useQueryClient();
  const { me } = useSession();
  const page = useQuery({ queryKey: ['report-messages', conversationId], queryFn: () => oro.newest(conversationId, 50) });
  const candidates = (page.data?.messages ?? []).filter((m) => !m.deleted && m.type !== 'SYSTEM' && m.senderUserId !== me?.userId
    && (!userId || m.senderUserId === userId)).slice(0, 20);
  const people = usePeople([userId, ...candidates.map((m) => m.senderUserId)]);
  const [picked, setPicked] = useState<string[] | null>(null);
  const chosen = picked ?? (messageIds ? messageIds.split(',').filter(Boolean) : candidates.slice(0, 1).map((m) => m.id));
  const [category, setCategory] = useState<ReportCategory | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const who = userId ? displayName(people.get(userId), userId) : 'this conversation';
  const first = userId ? firstName(people.get(userId), userId) : '';

  const send = async () => {
    setBusy(true);
    setError(null);
    try {
      await oro.report({ conversationId, messageIds: chosen, category: category!, reason: reason.trim() || undefined });
      void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => undefined);
      setSent(true);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  if (sent) {
    return (
      <Backdrop>
        <View style={{ flex: 1, padding: 24, paddingTop: insets.top + 64, gap: 16, alignItems: 'center' }}>
          <View style={{ width: 72, height: 72, borderRadius: 36, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.successSoft }}>
            <Icon name="shield-check" size={32} color="success" />
          </View>
          <T variant="title" align="center">Thanks for telling us</T>
          <T color="textSecondary" align="center">We keep a copy of these messages for 90 days to review them.</T>
          {userId ? <T color="textSecondary" align="center">Do you also want to block {first}? They won&apos;t be able to message you or add you to groups.</T> : null}
          <View style={{ alignSelf: 'stretch', gap: 8, marginTop: 8 }}>
            {userId ? <Button label={`Block ${first}`} kind="destructive" onPress={async () => {
              await oro.block(userId).catch(() => undefined);
              await client.invalidateQueries({ queryKey: ['blocks'] });
              router.back();
            }} /> : null}
            <Button label="Done" kind="secondary" onPress={() => router.back()} />
          </View>
        </View>
      </Backdrop>
    );
  }

  return (
    <Backdrop>
      <ModalBar title="Report" onCancel={() => router.back()} top={insets.top} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: insets.bottom + 24 }} keyboardShouldPersistTaps="handled">
        <T variant="headline" size={22} weight="extrabold">Report {who}</T>
        {error ? <Banner text={error} /> : null}
        <SectionHeader label="Messages to include" />
        {page.isPending ? <Spinner /> : (
          <Card>
            {candidates.length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>No message of theirs to include.</T> : null}
            {candidates.map((m) => {
              const on = chosen.includes(m.id);
              return (
                <Pressable key={m.id} accessibilityRole="checkbox" accessibilityState={{ checked: on }}
                  onPress={() => setPicked(on ? chosen.filter((x) => x !== m.id) : [...chosen, m.id].slice(0, 20))}
                  style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14 }}>
                  <View style={{ width: 22, height: 22, borderRadius: 6, alignItems: 'center', justifyContent: 'center',
                    backgroundColor: on ? colors.accent : 'transparent', borderWidth: on ? 0 : 2, borderColor: colors.border }}>
                    {on ? <Icon name="check" size={14} color="accentContrast" /> : null}
                  </View>
                  <T variant="bodySmall" numberOfLines={2} style={{ flex: 1 }}>
                    “{m.body || m.attachments.map((a) => a.fileName).join(', ')}”
                  </T>
                </Pressable>
              );
            })}
          </Card>
        )}
        <SectionHeader label="What's wrong?" />
        <Card>
          {CATEGORIES.map((c) => (
            <Pressable key={c.key} accessibilityRole="radio" accessibilityState={{ checked: category === c.key }} onPress={() => setCategory(c.key)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14 }}>
              <View style={{ flex: 1 }}>
                <T weight="semibold">{c.title}</T>
                <T variant="caption" color="textSecondary">{c.body}</T>
              </View>
              <View style={{ width: 22, height: 22, borderRadius: 11, borderWidth: category === c.key ? 7 : 2,
                borderColor: category === c.key ? colors.accent : colors.border }} />
            </Pressable>
          ))}
        </Card>
        <TextField label="Note (optional)" placeholder="Tell us more" value={reason} onChangeText={setReason} maxLength={1000} multiline
          helper="We keep a copy of the selected messages for 90 days to review them." />
        <Button label="Send report" kind="destructive" onPress={send} loading={busy} disabled={!category || chosen.length === 0} />
      </ScrollView>
    </Backdrop>
  );
}
