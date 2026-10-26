import { ApiError } from '../api/client';

/** an error in the design's words (J, D8, A5b–d): what happened and what to do */
export function errorText(e: unknown): string {
  if (!(e instanceof ApiError)) {
    return 'Something went wrong. Try again.';
  }
  if (e.offline) {
    return 'No internet connection';
  }
  switch (e.status) {
    case 429:
      return 'Too many attempts. Try again in a few minutes.';
    case 503:
      return 'Oro is restarting. Try again in a few seconds. Your messages are safe.';
    case 500:
    case 502:
    case 504:
      return 'Something went wrong. Try again.';
    default:
      return e.message.endsWith('.') ? e.message : `${e.message}.`;
  }
}

export const MAX_FILE_BYTES = 100 * 1024 * 1024;
