import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { ApiError } from '../api/client';
import { LiveStatus } from '../live/LiveProvider';
import { useTheme } from '../theme/ThemeProvider';
import { EmptyState } from './Basics';
import { Button } from './Button';
import { Icon } from './Icon';
import { T } from './Text';

/** J3 (Oro restarting: 503) and J4 (something went wrong), with Try again */
export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const restarting = error instanceof ApiError && error.status === 503;
  const offline = error instanceof ApiError && error.offline;
  return (
    <EmptyState icon={restarting ? 'server-cog' : offline ? 'wifi-off' : 'circle-alert'}
      title={restarting ? 'Oro is restarting' : offline ? 'No internet connection' : 'Something went wrong'}
      body={restarting ? 'Try again in a few seconds. Your messages are safe.'
        : "We couldn't load your conversations. Check your connection and try again."}
      action={<Button label="Try again" kind="secondary" onPress={onRetry} style={{ marginTop: 8, alignSelf: 'stretch' }} />} />
  );
}

/** J1: loading, rows of shapes where the conversations come */
export function ListSkeleton({ rows = 7 }: { rows?: number }) {
  const { colors } = useTheme();
  return (
    <View accessibilityLabel="Loading" style={{ paddingTop: 8 }}>
      {Array.from({ length: rows }, (_, i) => (
        <View key={i} style={{ flexDirection: 'row', gap: 12, paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center' }}>
          <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: colors.surfaceRaised }} />
          <View style={{ flex: 1, gap: 8 }}>
            <View style={{ width: `${45 + ((i * 17) % 30)}%`, height: 12, borderRadius: 6, backgroundColor: colors.surfaceRaised }} />
            <View style={{ width: `${60 + ((i * 23) % 30)}%`, height: 10, borderRadius: 5, backgroundColor: colors.surfaceRaised }} />
          </View>
        </View>
      ))}
    </View>
  );
}

export function Spinner() {
  const { colors } = useTheme();
  return <View style={{ padding: 32 }}><ActivityIndicator color={colors.accent} /></View>;
}

/** B4: Connecting… / No internet connection / Back online (for 3 s after a cut) under the title */
export function ConnectionStatus({ status }: { status: LiveStatus }) {
  const [backOnline, setBackOnline] = useState(false);
  const was = useRef(status);
  useEffect(() => {
    if (status === 'live' && (was.current === 'offline' || was.current === 'connecting')) {
      if (was.current === 'offline') {
        setBackOnline(true);
      }
      const t = setTimeout(() => setBackOnline(false), 3000);
      was.current = status;
      return () => clearTimeout(t);
    }
    was.current = status;
  }, [status]);
  if (status === 'live' && !backOnline) {
    return null;
  }
  const text = backOnline ? 'Back online' : status === 'offline' ? 'No internet connection'
    : status === 'updating' ? 'Updating…' : 'Connecting…';
  return (
    <View accessibilityLiveRegion="polite" style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
      {backOnline ? <Icon name="circle-check" size={14} color="success" />
        : status === 'offline' ? <Icon name="wifi-off" size={14} color="danger" />
          : <ActivityIndicator size="small" style={{ transform: [{ scale: 0.7 }] }} />}
      <T variant="caption" weight="semibold" color={backOnline ? 'success' : status === 'offline' ? 'danger' : 'textSecondary'}>{text}</T>
    </View>
  );
}
