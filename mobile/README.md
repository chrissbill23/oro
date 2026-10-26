# Oro app

The Oro app for iOS and Android: Expo SDK 58, React Native, TypeScript, expo-router. Its screens follow the design
in `UI/oro.pen` (the tokens, the components and the screens by their names there: A1 Welcome, B1 Chats, D1 a
conversation...). It talks to Oro's API only; what the API does not do yet is marked **Needs backend** on its screen.

## Run

```bash
npm install
npx expo start            # then Expo Go on a phone, or i / a for a simulator
npx expo start --web      # a preview in a browser (http://localhost:8081, allowed by the dev Oro's CORS)
```

Oro's address is `EXPO_PUBLIC_ORO_URL`, by default the dev stack's `http://localhost:8082/oro`. A phone reaches the
computer by its address on the network, not localhost:

```bash
EXPO_PUBLIC_ORO_URL=http://192.168.1.20:8082/oro npx expo start
```

The dev Oro listens on 127.0.0.1 only: to reach it from a phone, publish its port on the network for the session.

## Check

```bash
npx tsc --noEmit      # types
npx expo lint         # lint (the React Compiler rules)
npm test              # Jest: the stream parser, the formats, the merge of messages
npx expo-doctor       # the dependencies of the SDK
```

## How it works

| What | Where |
|---|---|
| Routes (expo-router) | `src/app`: `(auth)` signed out, `(app)` signed in (`(tabs)`: Chats, Requests, You), `verify-email` and `reset-password` for the links of the emails |
| Oro's API | `src/api` (`oro.ts`: every call; `client.ts`: the token, renewed once on a 401, errors with their status and `Retry-After`) |
| The session | `src/session`: the refresh token in the Keychain or the Keystore (`expo-secure-store`), the browser's storage on the web preview only |
| The stream | `src/live`: `GET /stream` read with `fetch` (Server-Sent Events), again after a cut with Umoja's delays (1, 2, 5, 10, 30 s) or Oro's `Retry-After`; every hello catches up |
| A conversation | `src/lib/messages.ts`: pages, the catch-up (`?after=`), sends shown at once and kept on failure to retry with the same client id |
| Names | `src/lib/people.tsx`: `GET /people`, in batches of what the screens show |
| The design | `src/theme` (the tokens of `oro.pen`, light and dark), `src/components` |

- **Sign-in**: the app sends `X-Oro-Client: native`, so Oro returns the refresh token in the body instead of a cookie.
  The app keeps it in the phone's secure storage and sends it to `/auth/refresh` and `/auth/logout`.
- **Nothing lost**: a message is shown with a clock until Oro answers. A failed one says "Not sent · Tap to retry",
  and it is sent again when the stream comes back. The client id makes a second send a no-op.
- **Files**: checked against 100 MB before the upload; downloaded with the session's token, then the share sheet.
- **Presence**: Oro sends it for contacts only, and the app shows only what it gets.

## Not yet (needs the backend)

Finding a person by username or QR code (New message lists your contacts: the people of your direct conversations),
profile and group photos, mobile push, the list of sessions, changing a member's role, the privacy notice. The links
of the emails open the app once `ORO_ACCOUNTS_LINK_BASE` is `oro://` (or universal links on a domain); until then
they open the web address.
