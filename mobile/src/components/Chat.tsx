import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, HostInstance, Pressable, TextInput, View } from 'react-native';
import { Attachment, Message, Reaction } from '../api/types';
import { clock, fileSize } from '../lib/format';
import { useTheme } from '../theme/ThemeProvider';
import { font, fontSize, radius } from '../theme/tokens';
import { Icon, IconName } from './Icon';
import { T } from './Text';

/** a message as the conversation shows it: from Oro, or still on its way (sending, failed) */
export type ChatMessage = Message & {
  local?: 'sending' | 'failed';
  /** a file picked and not uploaded yet */
  pendingFile?: { uri: string; name: string; type: string; size: number; web?: Blob; progress?: number };
};

/** DateSeparator: a day between messages */
export function DateSeparator({ label }: { label: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ alignItems: 'center', paddingVertical: 8 }}>
      <View style={{ paddingVertical: 4, paddingHorizontal: 12, borderRadius: 999, backgroundColor: colors.surfaceRaised }}>
        <T variant="caption" weight="semibold" color="textSecondary">{label}</T>
      </View>
    </View>
  );
}

/** UnreadSeparator: where the new messages start */
export function UnreadSeparator() {
  const { colors } = useTheme();
  return (
    <View accessibilityRole="header" style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 8, paddingHorizontal: 16 }}>
      <View style={{ flex: 1, height: 1, backgroundColor: colors.accent, opacity: 0.4 }} />
      <T variant="caption" weight="bold" color="accent">New messages</T>
      <View style={{ flex: 1, height: 1, backgroundColor: colors.accent, opacity: 0.4 }} />
    </View>
  );
}

/** SystemMessage: a pill in the middle (joined, left, renamed) */
export function SystemMessage({ text }: { text: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ alignItems: 'center', paddingVertical: 6, paddingHorizontal: 32 }}>
      <View style={{ paddingVertical: 5, paddingHorizontal: 12, borderRadius: 999, backgroundColor: colors.surfaceRaised }}>
        <T variant="caption" color="textSecondary" align="center">{text}</T>
      </View>
    </View>
  );
}

/** TypingIndicator: three dots in a bubble of theirs, and who types */
export function TypingIndicator({ who }: { who?: string }) {
  const { colors } = useTheme();
  const [dots] = useState(() => [0, 1, 2].map(() => new Animated.Value(0.3)));
  useEffect(() => {
    const loops = dots.map((v, i) =>
      Animated.loop(Animated.sequence([
        Animated.delay(i * 160),
        Animated.timing(v, { toValue: 1, duration: 320, useNativeDriver: true }),
        Animated.timing(v, { toValue: 0.3, duration: 320, useNativeDriver: true }),
        Animated.delay((2 - i) * 160),
      ])),
    );
    loops.forEach((l) => l.start());
    return () => loops.forEach((l) => l.stop());
  }, [dots]);
  return (
    <View accessibilityLabel={who ? `${who} is typing` : 'typing'} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 16, paddingVertical: 4 }}>
      <View style={{ flexDirection: 'row', gap: 4, paddingVertical: 12, paddingHorizontal: 14, borderRadius: radius.bubble,
        borderBottomLeftRadius: 6, backgroundColor: colors.bubbleTheirs, borderWidth: 1, borderColor: colors.glassStroke }}>
        {dots.map((v, i) => (
          <Animated.View key={i} style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: colors.textTertiary, opacity: v }} />
        ))}
      </View>
      {who ? <T variant="caption" color="textTertiary">{who} is typing</T> : null}
    </View>
  );
}

/** ReactionChip: an emoji and its count; mine on accent-soft with an accent edge */
export function ReactionChip({ reaction, mine, onPress }: { reaction: Reaction; mine: boolean; onPress: () => void }) {
  const { colors } = useTheme();
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ selected: mine }}
      accessibilityLabel={`${reaction.emoji} ${reaction.userIds.length}`} onPress={onPress}
      style={{ flexDirection: 'row', alignItems: 'center', gap: 4, height: 26, paddingHorizontal: 8, borderRadius: 13,
        backgroundColor: mine ? colors.accentSoft : colors.surface, borderWidth: 1, borderColor: mine ? colors.accent : colors.border }}>
      <T size={13}>{reaction.emoji}</T>
      <T variant="caption" weight="bold" color={mine ? 'accent' : 'textSecondary'}>{reaction.userIds.length}</T>
    </Pressable>
  );
}

function fileIcon(mime: string): IconName {
  if (mime.startsWith('image/')) return 'file-image';
  if (mime.includes('sheet') || mime.includes('csv') || mime.includes('excel')) return 'file-spreadsheet';
  if (mime.startsWith('text/') || mime.includes('pdf') || mime.includes('word')) return 'file-text';
  return 'file';
}

/** the file of a message: its type, name and size, its state (uploading, scanning, ready, blocked) */
function FileCard({ attachment, pending, mine, onOpen }: {
  attachment?: Attachment; pending?: ChatMessage['pendingFile']; mine: boolean; onOpen?: (a: Attachment) => void;
}) {
  const { colors } = useTheme();
  const name = attachment?.fileName ?? pending?.name ?? 'File';
  const mime = attachment?.mimeType ?? pending?.type ?? '';
  const size = attachment?.sizeBytes ?? pending?.size ?? 0;
  const text = mine ? 'bubbleMineText' : 'textPrimary';
  const status = pending ? 'Uploading…' : attachment?.available ? fileSize(size) : 'Checking for viruses…';
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${name}, ${status}`}
      disabled={!attachment?.available} onPress={() => attachment && onOpen?.(attachment)}
      style={{ width: 236, flexDirection: 'row', alignItems: 'center', gap: 10, padding: 8, borderRadius: 12,
        backgroundColor: mine ? '#FFFFFF26' : colors.surfaceRaised }}>
      <View style={{ width: 44, height: 44, borderRadius: 10, alignItems: 'center', justifyContent: 'center',
        backgroundColor: mine ? '#FFFFFF33' : colors.accentSoft }}>
        <Icon name={fileIcon(mime)} size={22} color={mine ? 'bubbleMineText' : 'accent'} />
      </View>
      <View style={{ flex: 1, gap: 3 }}>
        <T variant="bodySmall" weight="semibold" color={text} numberOfLines={1}>{name}</T>
        {pending?.progress !== undefined ? (
          <View style={{ height: 4, borderRadius: 2, backgroundColor: mine ? '#FFFFFF40' : colors.border, overflow: 'hidden' }}>
            <View style={{ width: `${Math.round(pending.progress * 100)}%`, height: 4, backgroundColor: mine ? '#FFFFFF' : colors.accent }} />
          </View>
        ) : null}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
          {!pending && !attachment?.available ? <ActivityIndicator size="small" color={mine ? '#FFFFFF' : colors.textTertiary} style={{ transform: [{ scale: 0.6 }], width: 12, height: 12 }} /> : null}
          <T variant="caption" color={mine ? 'bubbleMineText' : 'textTertiary'} style={{ opacity: mine ? 0.8 : 1 }}>{status}</T>
        </View>
      </View>
      {attachment?.available ? <Icon name="download" size={18} color={mine ? 'bubbleMineText' : 'accent'} /> : null}
    </Pressable>
  );
}

export type Delivery = 'sending' | 'failed' | 'sent' | 'seen';

/**
 * MessageBubble: theirs (glass, left, the sender's name in a group) or mine (the gradient, right, its delivery);
 * deleted (italic, an edge), edited, files, reactions under it. A long press opens the actions; a failed one retries.
 */
export function MessageBubble({
  message, mine, sender, first, delivery, myId, onLongPress, onRetry, onReact, onOpenFile, bare,
}: {
  message: ChatMessage; mine: boolean; sender?: string; first: boolean; delivery?: Delivery; myId?: string;
  onLongPress?: (at: Anchor) => void; onRetry?: () => void; onReact?: (emoji: string) => void; onOpenFile?: (a: Attachment) => void;
  /** the bubble alone, for the long-press menu: no row, no reactions, no retry line */
  bare?: boolean;
}) {
  const { colors } = useTheme();
  const bubble = useRef<HostInstance>(null);
  // where the bubble is on the screen, for the menu to lift it in place
  const longPress = onLongPress && (() => bubble.current?.measureInWindow((x, y, width, height) => onLongPress({ x, y, width, height })));
  const text = mine ? 'bubbleMineText' : 'bubbleTheirsText';
  const corner = {
    borderTopLeftRadius: radius.bubble, borderTopRightRadius: radius.bubble,
    borderBottomLeftRadius: mine ? radius.bubble : 6, borderBottomRightRadius: mine ? 6 : radius.bubble,
  };
  const meta = (
    <View style={{ flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-end', gap: 4, marginTop: 2 }}>
      {message.editedAt && !message.deleted ? <T size={11} color={mine ? 'bubbleMineText' : 'textTertiary'} style={{ opacity: mine ? 0.75 : 1 }}>edited</T> : null}
      <T size={11} color={mine ? 'bubbleMineText' : 'textTertiary'} style={{ opacity: mine ? 0.75 : 1 }}>{clock(message.createdAt)}</T>
      {mine && delivery ? (
        delivery === 'sending' ? <Icon name="clock" size={12} color="bubbleMineText" />
          : delivery === 'failed' ? <Icon name="circle-alert" size={12} color="bubbleMineText" />
            : <Icon name={delivery === 'seen' ? 'check-check' : 'check'} size={13} color="bubbleMineText" />
      ) : null}
    </View>
  );

  if (message.deleted) {
    return (
      <View style={{ paddingHorizontal: 16, paddingTop: first ? 6 : 2, alignItems: mine ? 'flex-end' : 'flex-start' }}>
        <View style={[corner, { paddingVertical: 8, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.border, flexDirection: 'row', gap: 6, alignItems: 'center' }]}>
          <Icon name="ban" size={14} color="textTertiary" />
          <T size={15} italic color="textTertiary">{mine ? 'You deleted this message' : 'This message was deleted'}</T>
        </View>
      </View>
    );
  }

  const body = (
    <>
      {!mine && sender && first ? <T variant="caption" weight="bold" color="accent" style={{ marginBottom: 2 }}>{sender}</T> : null}
      {message.attachments.map((a) => <FileCard key={a.id} attachment={a} mine={mine} onOpen={onOpenFile} />)}
      {message.pendingFile ? <FileCard pending={message.pendingFile} mine={mine} /> : null}
      {message.body ? <T color={text} style={{ lineHeight: 22 }} selectable>{message.body}</T> : null}
      {meta}
    </>
  );
  const pad = { paddingTop: 9, paddingHorizontal: 14, paddingBottom: 7, gap: 6 };

  const shape = mine ? (
    // frosted grey glass: a light-to-grey sheen with the glass edge
    <LinearGradient colors={[colors.bubbleMine, colors.bubbleMine2]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }}
      style={[corner, pad, { borderWidth: 1, borderColor: colors.glassStroke, opacity: message.local === 'sending' ? 0.85 : 1 }]}>
      {body}
    </LinearGradient>
  ) : (
    <View style={[corner, pad, { backgroundColor: colors.bubbleTheirs, borderWidth: 1, borderColor: colors.glassStroke }]}>{body}</View>
  );
  if (bare) {
    return shape;
  }

  return (
    <View style={{ paddingHorizontal: 16, paddingTop: first ? 6 : 2, alignItems: mine ? 'flex-end' : 'flex-start' }}>
      <Pressable ref={bubble} onLongPress={longPress} onPress={message.local === 'failed' ? onRetry : undefined} delayLongPress={300}
        accessibilityHint="Double tap and hold for actions" style={{ maxWidth: '80%' }}>
        {shape}
      </Pressable>
      {message.local === 'failed' ? (
        <Pressable accessibilityRole="button" onPress={onRetry} style={{ flexDirection: 'row', gap: 4, alignItems: 'center', marginTop: 4 }}>
          <Icon name="circle-alert" size={14} color="danger" />
          <T variant="caption" weight="semibold" color="danger">Not sent · Tap to retry</T>
        </Pressable>
      ) : null}
      {message.reactions.length ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: 4, justifyContent: mine ? 'flex-end' : 'flex-start' }}>
          {message.reactions.map((r) => (
            <ReactionChip key={r.emoji} reaction={r} mine={!!myId && r.userIds.includes(myId)} onPress={() => onReact?.(r.emoji)} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

/** where a bubble is on the screen */
export type Anchor = { x: number; y: number; width: number; height: number };

/** SeenLabel: under my last message, who saw it */
export function SeenLabel({ text }: { text: string }) {
  return (
    <View style={{ alignItems: 'flex-end', paddingHorizontal: 18, paddingTop: 2 }}>
      <T size={11} weight="semibold" color="textTertiary">{text}</T>
    </View>
  );
}

/** JumpToNew: a pill at the bottom when new messages came while reading higher */
export function JumpToNew({ count, onPress }: { count: number; onPress: () => void }) {
  const { colors } = useTheme();
  return (
    <Pressable accessibilityRole="button" onPress={onPress}
      style={{ position: 'absolute', bottom: 12, alignSelf: 'center', flexDirection: 'row', alignItems: 'center', gap: 6,
        height: 36, paddingHorizontal: 14, borderRadius: 18, backgroundColor: colors.accent,
        shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 8, shadowOffset: { width: 0, height: 4 }, elevation: 4 }}>
      <Icon name="arrow-up" size={16} color="accentContrast" />
      <T variant="label" weight="bold" color="accentContrast">{count > 0 ? `${count} new message${count > 1 ? 's' : ''}` : 'Latest'}</T>
    </Pressable>
  );
}

/**
 * Composer: attach, the field (radius 20), send; editing shows what is edited above it; locked (left, removed,
 * blocked, a request not accepted) says why instead.
 */
export function Composer({
  value, onChangeText, onSend, onAttach, editing, onCancelEdit, locked, sending,
}: {
  value: string; onChangeText: (v: string) => void; onSend: () => void; onAttach?: () => void;
  editing?: string; onCancelEdit?: () => void; locked?: string; sending?: boolean;
}) {
  const { colors } = useTheme();
  if (locked) {
    return (
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 14, paddingHorizontal: 16,
        backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border }}>
        <Icon name="lock" size={16} color="textSecondary" />
        <T variant="bodySmall" color="textSecondary" style={{ flex: 1 }}>{locked}</T>
      </View>
    );
  }
  const canSend = value.trim().length > 0 && !sending;
  return (
    <View style={{ backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border }}>
      {editing !== undefined ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingTop: 8, paddingHorizontal: 16 }}>
          <View style={{ width: 3, alignSelf: 'stretch', borderRadius: 2, backgroundColor: colors.accent }} />
          <Icon name="pencil" size={16} color="accent" />
          <View style={{ flex: 1 }}>
            <T variant="caption" weight="bold" color="accent">Edit message</T>
            <T variant="caption" color="textSecondary" numberOfLines={1}>{editing}</T>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="Cancel edit" hitSlop={12} onPress={onCancelEdit}>
            <Icon name="x" size={18} color="textSecondary" />
          </Pressable>
        </View>
      ) : null}
      <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8, paddingVertical: 8, paddingHorizontal: 12 }}>
        {editing === undefined && onAttach ? (
          <Pressable accessibilityRole="button" accessibilityLabel="Attach a file" onPress={onAttach}
            style={{ width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surfaceRaised, marginBottom: 2 }}>
            <Icon name="plus" size={20} color="accent" />
          </Pressable>
        ) : null}
        <TextInput
          accessibilityLabel="Message"
          value={value}
          onChangeText={onChangeText}
          placeholder="Message"
          placeholderTextColor={colors.textTertiary}
          multiline
          rows={1}
          maxLength={16000}
          style={{ flex: 1, minHeight: 40, maxHeight: 120, paddingTop: 10, paddingBottom: 10, paddingHorizontal: 14,
            borderRadius: 20, backgroundColor: colors.background, color: colors.textPrimary, fontFamily: font.regular,
            fontSize: fontSize.body, borderWidth: 1, borderColor: colors.border }}
        />
        <Pressable accessibilityRole="button" accessibilityLabel={editing !== undefined ? 'Save' : 'Send'}
          accessibilityState={{ disabled: !canSend }} onPress={canSend ? onSend : undefined}
          style={{ width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', marginBottom: 2,
            backgroundColor: canSend ? colors.accent : colors.surfaceRaised }}>
          <Icon name={editing !== undefined ? 'check' : 'arrow-up'} size={20} color={canSend ? 'accentContrast' : 'textTertiary'} />
        </Pressable>
      </View>
    </View>
  );
}
