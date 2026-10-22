# Connecting Umoja

Umoja (`../umoja`) uses Oro for its Messages page:

- Its frontend calls Oro on `/oro`, proxied by its nginx or by `ng serve`, with the Umoja access token. Oro checks that
  token with the same `UMOJA_JWT_SECRET`, and the request names the organization in `x-organization-id`.
- Its backend tells Oro who is in each organization and team (`PUT /oro/internal/organizations/{id}`, with
  `UMOJA_INTEGRATION_KEY`): after every change, when a user opens the messages, and every 10 minutes.
- Both reach Oro on the shared Docker network `asataDB` as `oro:8082`.

Without the shared secret and key, Umoja users cannot call Oro, and their organizations are not synchronized.

The synchronization is described in [Team chats and the Umoja synchronization](../backend/README.md#team-chats-and-the-umoja-synchronization),
and the authentication in [Who may call](../backend/README.md#who-may-call).
