-- The queries of Oro on the generated data (generate.sql), as the API runs them: role oro_app, the product and the
-- identity transaction-local, each in a transaction rolled back. EXPLAIN (ANALYZE, BUFFERS) after one warm-up run.
\set ON_ERROR_STOP on
\pset pager off

SELECT id AS power FROM oro_messaging_identity WHERE external_user_id = 'u1-1' \gset
SELECT id AS typical FROM oro_messaging_identity WHERE external_user_id = 'u5-100' \gset
SELECT organization_id AS org1 FROM oro_organization_access WHERE identity_id = :'power' \gset
SELECT organization_id AS org5 FROM oro_organization_access WHERE identity_id = :'typical' \gset
SELECT id AS hot FROM oro_conversation ORDER BY last_message_seq DESC LIMIT 1 \gset
SELECT c.id AS direct FROM oro_conversation c JOIN oro_conversation_member m ON m.conversation_id = c.id
    WHERE m.user_id = :'typical' AND c.conversation_type = 'direct' ORDER BY c.last_message_seq DESC LIMIT 1 \gset
SELECT c.id AS biggroup FROM oro_conversation c WHERE c.conversation_type = 'group'
    ORDER BY (SELECT count(*) FROM oro_conversation_member m WHERE m.conversation_id = c.id) DESC LIMIT 1 \gset
SELECT (SELECT count(*) FROM oro_conversation_member WHERE user_id = :'power' AND left_at IS NULL) AS power_conversations,
       (SELECT count(*) FROM oro_conversation_member WHERE user_id = :'typical' AND left_at IS NULL) AS typical_conversations,
       (SELECT last_message_seq FROM oro_conversation WHERE id = :'hot') AS hot_messages,
       (SELECT count(*) FROM oro_conversation_member WHERE conversation_id = :'biggroup') AS biggroup_members;

-- the power user is in the hot group
INSERT INTO oro_conversation_member(conversation_id, product_id, user_id, role, shared_history)
    SELECT :'hot', 'UMOJA', :'power', 'member', true
    WHERE NOT EXISTS (SELECT 1 FROM oro_conversation_member WHERE conversation_id = :'hot' AND user_id = :'power');

\set as_power 'BEGIN; SET LOCAL ROLE oro_app; SELECT set_config(''oro.product_id'', ''UMOJA'', true), set_config(''oro.identity_id'', :''power'', true) \\g /dev/null'
\set as_typical 'BEGIN; SET LOCAL ROLE oro_app; SELECT set_config(''oro.product_id'', ''UMOJA'', true), set_config(''oro.identity_id'', :''typical'', true) \\g /dev/null'
\set ex 'EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)'

\echo '=== 1. list: the typical user'
:as_typical
\set list 'SELECT c.id, c.conversation_type, c.name, c.team_id, c.last_message_seq, c.last_message_at, c.created_at, c.last_message_seq - m.last_read_seq AS unread, m.muted_until, m.role, oro_member_count(c.id) AS member_count, other.external_user_id AS other_user_id, lm.id AS lm_id, lm.seq AS lm_seq, sender.external_user_id AS lm_sender, lm.message_type AS lm_type, left(lm.body, 200) AS lm_body, CASE WHEN lm.message_type = ''system'' THEN lm.metadata::text END AS lm_metadata, lm.deleted_at IS NOT NULL AS lm_deleted, lm.created_at AS lm_created_at FROM oro_conversation_member m JOIN oro_conversation c ON c.id = m.conversation_id LEFT JOIN oro_messaging_identity other ON c.conversation_type = ''direct'' AND other.id = CASE WHEN c.direct_identity_low = m.user_id THEN c.direct_identity_high ELSE c.direct_identity_low END LEFT JOIN oro_message lm ON lm.conversation_id = c.id AND lm.seq = c.last_message_seq LEFT JOIN oro_messaging_identity sender ON sender.id = lm.sender_id WHERE m.user_id = oro_current_identity() AND m.left_at IS NULL AND c.product_id = ''UMOJA'' AND c.organization_id = '
SELECT count(*) FROM (:list :'org5') x;
:ex :list :'org5' ORDER BY coalesce(c.last_message_at, c.created_at) DESC, c.id;
ROLLBACK;

\echo '=== 2. list: the power user (about 800 conversations)'
:as_power
SELECT count(*) FROM (:list :'org1') x;
:ex :list :'org1' ORDER BY coalesce(c.last_message_at, c.created_at) DESC, c.id;
ROLLBACK;

\echo '=== 3. page: the newest 40 of the hot group (a million messages)'
:as_power
SELECT count(*) FROM (SELECT * FROM oro_message WHERE conversation_id = :'hot' ORDER BY seq DESC LIMIT 40) x;
:ex SELECT * FROM oro_message WHERE conversation_id = :'hot' ORDER BY seq DESC LIMIT 40;
ROLLBACK;

\echo '=== 4. page: 40 before seq 500000 in the hot group (scrolled far back)'
:as_power
:ex SELECT * FROM oro_message WHERE conversation_id = :'hot' AND seq < 500000 ORDER BY seq DESC LIMIT 40;
ROLLBACK;

\echo '=== 5. after: what came after the last seq the client has (catch-up)'
:as_power
:ex SELECT * FROM oro_message WHERE conversation_id = :'hot' AND seq > 999960 ORDER BY seq LIMIT 100;
ROLLBACK;

\echo '=== 6. page: a direct conversation of the typical user'
:as_typical
:ex SELECT * FROM oro_message WHERE conversation_id = :'direct' ORDER BY seq DESC LIMIT 40;
ROLLBACK;

\echo '=== 7. the reactions and attachments of a page (40 ids)'
:as_power
SELECT string_agg(quote_literal(id), ',') AS page_ids FROM (SELECT id FROM oro_message WHERE conversation_id = :'hot' ORDER BY seq DESC LIMIT 40) x \gset
:ex SELECT r.message_id, r.emoji, i.external_user_id FROM oro_message_reaction r JOIN oro_messaging_identity i ON i.id = r.user_id WHERE r.message_id IN (:page_ids) ORDER BY r.created_at, r.emoji;
:ex SELECT * FROM oro_attachment WHERE message_id IN (:page_ids) ORDER BY created_at;
ROLLBACK;

\echo '=== 8. send in the hot group: lock, idempotency, seq, insert, read position, outbox (all of it)'
:as_power
:ex SELECT * FROM oro_conversation WHERE id = :'hot' FOR NO KEY UPDATE;
:ex SELECT * FROM oro_conversation_member WHERE conversation_id = :'hot' AND user_id = :'power' AND left_at IS NULL;
:ex SELECT * FROM oro_message WHERE conversation_id = :'hot' AND sender_id = :'power' AND client_message_id = 'plans-0000001';
:ex UPDATE oro_conversation SET last_message_seq = last_message_seq + 1 WHERE id = :'hot';
:ex INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, message_type, body, metadata, created_at) SELECT gen_random_uuid(), :'hot', 'UMOJA', last_message_seq, :'power', 'plans-0000001', 'text', 'the plans of the send', '{}', now() FROM oro_conversation WHERE id = :'hot';
:ex UPDATE oro_conversation_member SET last_read_seq = last_read_seq + 1, last_delivered_seq = last_delivered_seq + 1 WHERE conversation_id = :'hot' AND user_id = :'power' AND left_at IS NULL;
:ex INSERT INTO oro_outbox_event(id, product_id, event_type, aggregate_type, aggregate_id, payload) VALUES (gen_random_uuid(), 'UMOJA', 'message.created', 'message', gen_random_uuid(), '{}');
ROLLBACK;

\echo '=== 9. search: a frequent word, every conversation of the power user'
:as_power
\set search 'SELECT msg.* FROM oro_message msg JOIN oro_conversation c ON c.id = msg.conversation_id JOIN oro_conversation_member m ON m.conversation_id = c.id AND m.user_id = oro_current_identity() AND m.left_at IS NULL WHERE c.product_id = ''UMOJA'' AND msg.deleted_at IS NULL AND msg.message_type <> ''system'' AND c.organization_id = '
:ex SELECT * FROM oro_search_messages('%invoice%', :'org1', NULL, 30);
ROLLBACK;

\echo '=== 10. search: a rare text, every conversation of the typical user'
:as_typical
:ex SELECT * FROM oro_search_messages('%visa contract%', :'org5', NULL, 30);
ROLLBACK;

\echo '=== 11. search: in the hot group only'
:as_power
:ex SELECT * FROM oro_search_messages('%invoice%', :'org1', :'hot', 30);
ROLLBACK;

\echo '=== 12. the members of the biggest group (its page of information)'
SELECT user_id AS bigmember FROM oro_conversation_member WHERE conversation_id = :'biggroup' LIMIT 1 \gset
BEGIN; SET LOCAL ROLE oro_app; SELECT set_config('oro.product_id', 'UMOJA', true), set_config('oro.identity_id', :'bigmember', true) \g /dev/null
:ex SELECT * FROM oro_conversation_member WHERE conversation_id = :'biggroup' AND left_at IS NULL ORDER BY joined_at, id;
ROLLBACK;
