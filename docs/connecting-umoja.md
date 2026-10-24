# Connecting Umoja

Umoja (`../umoja`) uses Oro for its Messages page:

- Its frontend calls Oro on `/oro`, proxied by its nginx or by `ng serve`, with the Umoja access token. Oro checks that
  token with Umoja's public keys (`/umoja/.well-known/jwks.json`, RS256, audience `oro`), and the request names the
  organization in `x-organization-id`. Only Umoja holds its signing key.
- Its backend tells Oro who is in each organization and team (`PUT /oro/internal/organizations/{id}`, with its
  `UMOJA_INTEGRATION_KEY`; Oro keeps only its SHA-256, in `UMOJA_INTEGRATION_KEYS`): after every change, when a user
  opens the messages, and every 10 minutes.
- Both reach Oro on the shared Docker network `asataDB` as `oro:8082`.
- Its frontend takes Oro's notifications (Web Push) when the person turns them on (the bell of the Messages page):
  the service worker `/oro-sw.js` shows them ("Design", "New message from Amani": never the text) and opens the
  conversation on a click; `core/push/push.service.ts` subscribes the browser with Oro's key and gives the
  subscription to Oro, and forgets it at sign-out, so that the next person on the browser never gets them.

Without Umoja's public keys, Umoja users cannot call Oro; without the integration key, their organizations are not
synchronized.

The synchronization is described in [Team chats and the Umoja synchronization](../backend/README.md#team-chats-and-the-umoja-synchronization),
and the authentication in [Who may call](../backend/README.md#who-may-call).
