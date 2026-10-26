import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';
import * as ImagePicker from 'expo-image-picker';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';
import { CLIENT_HEADERS, getAccessToken, refreshSession } from '../api/client';
import { ORO_URL } from '../api/config';
import { Attachment } from '../api/types';

/** a file picked on the phone, ready to upload: on the web, the browser's File */
export interface Picked {
  uri: string;
  name: string;
  type: string;
  size: number;
  web?: Blob;
}

export type PickResult = { picked: Picked } | { denied: true } | null;

/** D4 · Photos: the library (J5: refused) */
export async function pickPhoto(): Promise<PickResult> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted && Platform.OS !== 'web') {
    return { denied: true };
  }
  const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85, exif: false });
  return fromImage(result);
}

/** D4 · Camera */
export async function takePhoto(): Promise<PickResult> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    return { denied: true };
  }
  const result = await ImagePicker.launchCameraAsync({ quality: 0.85, exif: false });
  return fromImage(result);
}

function fromImage(result: ImagePicker.ImagePickerResult): PickResult {
  const asset = result.canceled ? undefined : result.assets[0];
  if (!asset) {
    return null;
  }
  const type = asset.mimeType ?? 'image/jpeg';
  const name = asset.fileName ?? `photo-${Date.now()}.${type === 'image/png' ? 'png' : 'jpg'}`;
  return { picked: { uri: asset.uri, name, type, size: asset.fileSize ?? asset.file?.size ?? 0, web: asset.file } };
}

/** D4 · File: any document */
export async function pickDocument(): Promise<PickResult> {
  const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true, multiple: false });
  const asset = result.canceled ? undefined : result.assets[0];
  if (!asset) {
    return null;
  }
  return {
    picked: {
      uri: asset.uri, name: asset.name, type: asset.mimeType ?? 'application/octet-stream', size: asset.size ?? asset.file?.size ?? 0,
      web: asset.file,
    },
  };
}

/**
 * A file of a message, opened: downloaded with the session's token (its content is never public), then the share
 * sheet (save, open with…). On the web: a new tab.
 */
export async function openAttachment(a: Attachment): Promise<void> {
  const url = `${ORO_URL}/attachments/${a.id}/content`;
  const headers = () => ({ ...CLIENT_HEADERS, Authorization: `Bearer ${getAccessToken() ?? ''}` });
  if (Platform.OS === 'web') {
    let res = await fetch(url, { headers: headers() });
    if (res.status === 401 && (await refreshSession())) {
      res = await fetch(url, { headers: headers() });
    }
    if (!res.ok) {
      throw new Error('download failed');
    }
    const blob = await res.blob();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = a.fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 60_000);
    return;
  }
  await refreshSession();
  const safe = a.fileName.replace(/[/\\]/g, '_');
  const file = await File.downloadFileAsync(url, new File(Paths.cache, `${a.id}-${safe}`), { headers: headers(), idempotent: true });
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(file.uri, { mimeType: a.mimeType, dialogTitle: a.fileName });
  }
}
