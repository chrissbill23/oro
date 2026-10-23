# Oro launch checklist

Go or no-go for putting Oro in front of real users. Every box must be ticked, with its proof, before a launch. The ids
point to [the TODO list](ORO_PRODUCTION_READINESS_TODO.md).

Two launches:

- **Umoja launch**: business messaging for Umoja's organizations. Sections 1 to 7.
- **Oro launch**: the standalone app. Sections 1 to 8.

## 1. Security

- [ ] The API's database login cannot `SET ROLE oro_system`; the worker login is separate (`ORO-SEC-001`)
- [ ] As `oro_app`, a plain member cannot change roles, remove others, add members nor rename (`ORO-SEC-002`)
- [ ] Oro holds no secret that can sign a product's tokens; tokens carry and are checked for the `oro` audience
      (`ORO-SEC-003`, `ORO-SEC-004`)
- [ ] `/oro/internal/*` answers 404 from the public origin; Oro's port is not reachable from outside the host
      (`ORO-SEC-005`)
- [ ] Integration keys can be rotated without downtime, and a rotation was done once (`ORO-SEC-006`)
- [ ] An open stream closes on token expiry, suspension, deactivation and access revocation (`ORO-SEC-007`)
- [ ] Overlapping organization synchronizations cannot bring back a revoked access (`ORO-SEC-008`)
- [ ] Rate limits, stream limits and storage quotas are on, and return 429, 503 or 413 (`ORO-SEC-010`, `011`, `012`)
- [ ] Logs hold no message content, metadata, token, key or failing row (`ORO-SEC-009`, `ORO-TST-005`)
- [ ] `metadata` is validated against a schema (`ORO-SEC-015`)
- [ ] Swagger and the API description are off (`ORO-SEC-017`)
- [ ] Statement, lock and idle-transaction timeouts are set on both logins (`ORO-SEC-020`)
- [ ] Every file is scanned before it can be downloaded; production refuses `scan: none` (`ORO-LCH-004`)
- [ ] File types are detected from their bytes (`ORO-LCH-005`)
- [ ] TLS with HSTS at the edge; CORS lists the production origins only (`ORO-LCH-015`)
- [ ] No development secret or fallback password in production; startup fails if one is set (`ORO-LCH-008`)
- [ ] The dependency and image scan shows no known critical vulnerability

## 2. Data integrity and privacy

- [ ] The retention policy is written: messages, deleted content, files, logs, backups (`ORO-SEC-014`, `ORO-LCH-018`)
- [ ] Files of deleted messages and abandoned uploads are purged on schedule (`ORO-LCH-006`)
- [ ] The privacy notice and Umoja's processing terms match what the system does (`ORO-LCH-018`)
- [ ] All 66 tests and the new ones pass in CI on the release commit (`ORO-LCH-017`)

## 3. Data recovery

- [ ] Point-in-time recovery is on; backups are encrypted and stored off the database host (`ORO-LCH-007`)
- [ ] The files are in object storage, or on a volume covered by the backups (`ORO-LCH-003`)
- [ ] A full restore was done in the last 30 days; its time is written down: ____ minutes (`ORO-LCH-007`)
- [ ] The automated restore check runs and alerts (`ORO-TST-010`)
- [ ] Recovery targets are agreed: data loss at most ____ minutes, back online within ____ hours

## 4. Observability

- [ ] JSON logs with request ids; metrics exported (`ORO-LCH-010`)
- [ ] Alerts fire and reach me: 5xx rate, failed outbox events, outbox age over 1 minute, disk space, backup age,
      database down (`ORO-LCH-010`, `ORO-LCH-013`)
- [ ] Errors reach an error tracker
- [ ] Readiness and liveness checks are wired to the orchestrator (`ORO-LCH-011`)

## 5. Deployment

- [ ] Schema changes are versioned and applied by the runner; a database at the previous version upgrades cleanly
      (`ORO-LCH-009`, `ORO-TST-009`)
- [ ] The previous backend version runs on the new schema (rollback possible)
- [ ] A restart loses no event and every client reconnects (`ORO-LCH-012`, `ORO-LCH-014`)
- [ ] The image runs as a non-root user (done: `USER ubuntu` in `backend/Dockerfile`) and is pinned to a digest
- [ ] Staging matches production, and the release ran there first

## 6. Capacity

- [ ] Query plans checked on realistic data (`ORO-SCL-001`)
- [ ] A slow client cannot hold up the others' events (`ORO-SCL-004`)
- [ ] The load test shows at least 3 times the expected peak; pools sized from it (`ORO-SCL-007`, `ORO-SCL-009`)

## 7. Operations

- [ ] Runbooks exist and were each run once on staging: deploy, rollback, restore, rotate a key, replay events,
      full disk, revoke a user urgently (`ORO-LCH-016`)
- [ ] Umoja's synchronization runs and its failures are visible on Umoja's side
- [ ] Someone is reachable for alerts during the launch week
- [ ] There is a way for users and customers to report a security problem

## 8. Oro launch only

- [ ] Sign-up, sign-in, sign-out, recovery; Argon2id; no account enumeration; refresh token reuse detection
      (`ORO-LCH-001`, `ORO-TST-011`)
- [ ] Account deletion erases what the policy says (`ORO-LCH-002`)
- [ ] Blocking, message requests from strangers and reporting work (`ORO-SEC-013`, `ORO-FEA-005`)
- [ ] Photo metadata (GPS) is removed (`ORO-LCH-005`)
- [ ] Push notifications, if there is a mobile app, carry no message text by default and are not duplicated
      (`ORO-FEA-001`, `ORO-SCL-010`)

## Decision

| Launch | Date | Decision | Open items accepted, and why |
|---|---|---|---|
| Umoja | | | |
| Oro | | | |
