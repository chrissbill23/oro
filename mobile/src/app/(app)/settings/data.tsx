import { File, Paths } from 'expo-file-system';
import { router } from 'expo-router';
import * as Sharing from 'expo-sharing';
import { useState } from 'react';
import { Platform, View } from 'react-native';
import { oro } from '../../../api/oro';
import { Card } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { Icon, IconName } from '../../../components/Icon';
import { InlineNav } from '../../../components/Nav';
import { Banner, Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { errorText } from '../../../lib/errors';

function Line({ icon, text, color = 'textPrimary' }: { icon: IconName; text: string; color?: 'textPrimary' | 'textSecondary' }) {
  return (
    <View style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
      <Icon name={icon} size={16} color={icon === 'x' ? 'textTertiary' : 'success'} />
      <T variant="bodySmall" color={color} style={{ flex: 1 }}>{text}</T>
    </View>
  );
}

/** a file saved and offered to the share sheet (the web: downloaded) */
async function shareJson(name: string, json: unknown) {
  const text = JSON.stringify(json, null, 2);
  if (Platform.OS === 'web') {
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    link.download = name;
    link.click();
    return;
  }
  const file = new File(Paths.cache, name);
  if (file.exists) {
    file.delete();
  }
  file.create();
  file.write(text);
  await Sharing.shareAsync(file.uri, { mimeType: 'application/json', dialogTitle: name, UTI: 'public.json' });
}

/** I4 · Your data: export (I4b, then the share sheet), erase (I4c) */
export default function Data() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const exportData = async () => {
    setBusy(true);
    setError(null);
    try {
      await shareJson(`oro-export-${new Date().toISOString().slice(0, 10)}.json`, await oro.exportData());
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Screen nav={<InlineNav title="Your data" backLabel="You" />}>
      {error ? <Banner text={error} /> : null}
      <Card>
        <View style={{ padding: 16, gap: 10 }}>
          <T variant="headline">Export my data</T>
          <T variant="bodySmall" color="textSecondary">A file with what you wrote and did in Oro:</T>
          <Line icon="check" text="Your messages, reactions and files" />
          <Line icon="check" text="Your conversations, groups and requests" />
          <Line icon="x" text="Not other people's messages" color="textSecondary" />
          <Button label={busy ? 'Preparing your file' : 'Export my data'} icon="download" kind="secondary" loading={busy} onPress={exportData} />
          {busy ? <T variant="caption" color="textSecondary">When it&apos;s ready, your phone&apos;s share sheet opens so you can save or send it.</T> : null}
        </View>
      </Card>
      <Card>
        <View style={{ padding: 16, gap: 10 }}>
          <T variant="headline" color="danger">Erase my data</T>
          <T variant="label" color="textSecondary">What goes</T>
          <Line icon="check" text="Your messages become “Message deleted” for everyone" />
          <Line icon="check" text="Your reactions and your files" />
          <T variant="label" color="textSecondary">What stays</T>
          <Line icon="check" text="Your Asata account, for Umoja and Mokili" />
          <Button label="Erase my data…" kind="destructive" onPress={() => router.push('/settings/erase')} />
        </View>
      </Card>
    </Screen>
  );
}
