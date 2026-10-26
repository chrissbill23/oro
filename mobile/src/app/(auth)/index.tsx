import { router } from 'expo-router';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Backdrop } from '../../components/Backdrop';
import { Button } from '../../components/Button';
import { MessageBubble } from '../../components/Chat';
import { T } from '../../components/Text';

const now = new Date().toISOString();
const sample = { conversationId: 'w', senderUserId: 'x', type: 'TEXT' as const, createdAt: now, deleted: false, reactions: [], attachments: [] };

/** A1 · Welcome: what Oro is, two bubbles, create an account or sign in */
export default function Welcome() {
  const insets = useSafeAreaInsets();
  return (
    <Backdrop>
      <View style={{ flex: 1, paddingTop: insets.top + 24, paddingBottom: insets.bottom + 16, paddingHorizontal: 24 }}>
        <T size={34} weight="extrabold" color="accent" style={{ letterSpacing: -1 }}>oro</T>
        <View style={{ flex: 1, justifyContent: 'center', gap: 4 }} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          <MessageBubble mine={false} first sender="Amani Mwangi" message={{ ...sample, id: '1', seq: 1, body: 'Are we still on for Sunday?' }} />
          <MessageBubble mine first delivery="seen" message={{ ...sample, id: '2', seq: 2, body: 'See you at 10 then' }} />
        </View>
        <View style={{ gap: 12 }}>
          <T size={30} weight="extrabold" style={{ letterSpacing: -0.6, lineHeight: 36 }} accessibilityRole="header">
            Talk with your people. Only them.
          </T>
          <T color="textSecondary" style={{ lineHeight: 22 }}>
            Strangers can&apos;t just write to you, notifications never show your messages, and your data stays yours.
          </T>
          <View style={{ gap: 8, marginTop: 12 }}>
            <Button label="Create an account" onPress={() => router.push('/sign-up')} />
            <Button label="Sign in" kind="secondary" onPress={() => router.push('/sign-in')} />
          </View>
          <T variant="caption" color="textTertiary" align="center">One Asata account for Oro, Umoja and Mokili</T>
        </View>
      </View>
    </Backdrop>
  );
}
