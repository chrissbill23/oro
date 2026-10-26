import { createContext, PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { onSignedOut, refreshSession, setAccessToken } from '../api/client';
import { oro } from '../api/oro';
import { Me } from '../api/types';
import { tokenStore } from './tokenStore';

export type SessionState = 'restoring' | 'signedOut' | 'signedIn';

interface Session {
  state: SessionState;
  me: Me | null;
  username: string | null;
  signIn: (login: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const SessionContext = createContext<Session | null>(null);

/**
 * The session of the person on this device: restored at launch from the refresh token in the secure storage, ended
 * by a sign-out (here or elsewhere: a refresh refused) or an erasure.
 */
export function SessionProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<SessionState>('restoring');
  const [me, setMe] = useState<Me | null>(null);
  const [username, setUsername] = useState<string | null>(null);

  const ended = useCallback(() => {
    setAccessToken(null);
    setMe(null);
    setState('signedOut');
  }, []);

  useEffect(() => {
    onSignedOut(ended);
    (async () => {
      if (await refreshSession()) {
        try {
          setMe(await oro.me());
          setState('signedIn');
          return;
        } catch {
          // fall through: signed out
        }
      }
      setState('signedOut');
    })();
  }, [ended]);

  const signIn = useCallback(async (login: string, password: string) => {
    const signedIn = await oro.login(login, password);
    setAccessToken(signedIn.accessToken);
    if (signedIn.refreshToken) {
      await tokenStore.set(signedIn.refreshToken);
    }
    setUsername(signedIn.username);
    setMe(await oro.me());
    setState('signedIn');
  }, []);

  const signOut = useCallback(async () => {
    const token = await tokenStore.get();
    await tokenStore.clear();
    if (token) {
      await oro.logout(token).catch(() => undefined);
    }
    ended();
  }, [ended]);

  const value = useMemo(() => ({ state, me, username, signIn, signOut }), [state, me, username, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('useSession outside SessionProvider');
  }
  return session;
}
