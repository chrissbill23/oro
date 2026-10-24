-- Realistic data for the query plans (ORO-SCL-001), on a throwaway database built by CREATE_ORO_TABLES.sql:
-- 20 Umoja organizations of 500 people (10,000 identities), 90,000 direct conversations and 10,000 groups, about
-- 10 million messages, skewed: one group of a million, a hundred of 20,000, the rest about 70 each. One power user
-- (u1-1) is in about 800 conversations. Loaded as the superuser with the triggers off (session_replication_role):
-- every row is written consistent by hand (seqs, last messages, members, read positions).
\set ON_ERROR_STOP on
\timing on
SET session_replication_role = replica;
SET maintenance_work_mem = '1GB';
SELECT setseed(0.42);

-- the trigram index is built once at the end, not row by row
DROP INDEX IF EXISTS idx_oro_message_body_trgm;

CREATE TEMP TABLE orgs AS SELECT g, gen_random_uuid() AS id FROM generate_series(1, 20) g;
CREATE TEMP TABLE people AS
    SELECT gen_random_uuid() AS id, o.g AS org_n, o.id AS org, u AS n FROM orgs o, generate_series(1, 500) u;
CREATE INDEX ON people(org_n, n);
INSERT INTO oro_messaging_identity(id, product_id, external_user_id)
    SELECT id, 'UMOJA', 'u' || org_n || '-' || n FROM people;
INSERT INTO oro_organization_access(identity_id, product_id, organization_id) SELECT id, 'UMOJA', org FROM people;

-- direct conversations: random pairs of one organization, and the power user with everyone of its organization
CREATE TEMP TABLE pairs AS
    SELECT DISTINCT ON (least(a.id, b.id), greatest(a.id, b.id)) a.org, least(a.id, b.id) AS low, greatest(a.id, b.id) AS high, a.id AS creator
    FROM (SELECT org_n, 1 + floor(random() * 500)::int AS x, 1 + floor(random() * 500)::int AS y
          FROM generate_series(1, 92000) s, LATERAL (SELECT 1 + floor(random() * 20)::int + 0 * s AS org_n OFFSET 0) o) r
    JOIN people a ON a.org_n = r.org_n AND a.n = r.x
    JOIN people b ON b.org_n = r.org_n AND b.n = r.y
    WHERE r.x <> r.y
    UNION
    SELECT a.org, least(a.id, b.id), greatest(a.id, b.id), a.id FROM people a JOIN people b ON b.org_n = 1 AND b.n > 1
    WHERE a.org_n = 1 AND a.n = 1;
CREATE TEMP TABLE convs AS
    SELECT DISTINCT ON (low, high) gen_random_uuid() AS id, 'direct'::text AS type, org, low, high, creator, NULL::text AS name
    FROM pairs;
-- groups: most small, some large, a few of hundreds; the power user in 300 of its organization's
CREATE TEMP TABLE groups AS
    SELECT gen_random_uuid() AS id, 1 + floor(random() * 20)::int AS org_n,
           CASE WHEN r < 0.70 THEN 3 + floor(random() * 8) WHEN r < 0.95 THEN 10 + floor(random() * 40)
                ELSE 50 + floor(random() * 450) END::int AS size, g
    FROM generate_series(1, 10000) g, LATERAL (SELECT random() + 0 * g AS r OFFSET 0) x;
UPDATE groups SET org_n = 1 WHERE g <= 300;
INSERT INTO convs SELECT gr.id, 'group', p.org, NULL, NULL, p.id, 'Group ' || gr.g
    FROM groups gr JOIN people p ON p.org_n = gr.org_n AND p.n = 1 + (gr.g % 500);

CREATE TEMP TABLE members AS
    SELECT c.id AS conversation_id, c.low AS user_id FROM convs c WHERE c.type = 'direct'
    UNION ALL SELECT c.id, c.high FROM convs c WHERE c.type = 'direct'
    UNION ALL
    SELECT gr.id, m.id FROM groups gr
    CROSS JOIN LATERAL (SELECT p.id FROM people p WHERE p.org_n = gr.org_n ORDER BY random() LIMIT gr.size) m;
-- the creator and, for its 300 groups, the power user
INSERT INTO members SELECT c.id, c.creator FROM convs c WHERE c.type = 'group'
    AND NOT EXISTS (SELECT 1 FROM members m WHERE m.conversation_id = c.id AND m.user_id = c.creator);
INSERT INTO members SELECT gr.id, p.id FROM groups gr JOIN people p ON p.org_n = 1 AND p.n = 1
    WHERE gr.g <= 300 AND NOT EXISTS (SELECT 1 FROM members m WHERE m.conversation_id = gr.id AND m.user_id = p.id);

-- how many messages: one group of a million (the power user's first), a hundred of 20,000, about 70 for the rest
ALTER TABLE convs ADD COLUMN n INT, ADD COLUMN created TIMESTAMPTZ;
UPDATE convs SET n = greatest(1, floor(-ln(1 - random()) * 70)::int), created = now() - interval '400 days' + random() * interval '30 days';
UPDATE convs SET n = 20000 WHERE id IN (SELECT id FROM convs ORDER BY random() LIMIT 100);
UPDATE convs SET n = 1000000 WHERE id = (SELECT id FROM groups WHERE g = 1);
SELECT count(*) AS conversations, sum(n) AS messages FROM convs;

INSERT INTO oro_conversation(id, product_id, context_type, organization_id, conversation_type, direct_identity_low,
                             direct_identity_high, name, created_by, last_message_seq, last_message_at, created_at)
    SELECT id, 'UMOJA', 'organization', org, type, low, high, name, creator, n,
           created + n * (interval '365 days' / greatest(n, 1)), created
    FROM convs;
CREATE TEMP TABLE member_arrays AS SELECT conversation_id, array_agg(user_id) AS ids FROM members GROUP BY 1;
CREATE INDEX ON member_arrays(conversation_id);

-- the words of a chat, the frequent ones first
CREATE TEMP TABLE vocabulary AS SELECT string_to_array(
    'ok yes no thanks the a to and is it you i we for on in this that of can will be have do meeting today tomorrow ' ||
    'please call later sure great good morning hello hi team project update done check send file report review ' ||
    'deadline client invoice design budget plan week monday friday lunch coffee quick question issue fix release ' ||
    'deploy server database login password reset access document draft final version slides agenda notes minutes ' ||
    'sales marketing hiring interview candidate onboarding training holiday leave sick office remote travel flight ' ||
    'hotel visa contract signature approval payment transfer bank account receipt expense customer support ticket ' ||
    'bug feature roadmap sprint retro demo launch campaign newsletter website mobile app android ios backend frontend', ' ') AS w;

INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, message_type, body, created_at)
    SELECT gen_random_uuid(), c.id, 'UMOJA', s, ma.ids[1 + ((s::bigint * 7919) % array_length(ma.ids, 1))::int],
           'gen-' || s || '-' || left(c.id::text, 8), 'text',
           v.w[1 + floor(random() * random() * 120)::int] || ' ' || v.w[1 + floor(random() * random() * 120)::int] || ' ' ||
           v.w[1 + floor(random() * 120)::int] || ' ' || v.w[1 + floor(random() * random() * 120)::int] || ' ' ||
           v.w[1 + floor(random() * 120)::int] || ' ' || v.w[1 + floor(random() * random() * 120)::int],
           c.created + s * (interval '365 days' / greatest(c.n, 1))
    FROM convs c JOIN member_arrays ma ON ma.conversation_id = c.id, vocabulary v, generate_series(1, c.n) s;

-- members: joined at the start with the history, read up to near the end (a few unread)
INSERT INTO oro_conversation_member(conversation_id, product_id, user_id, role, shared_history, joined_seq,
                                    last_delivered_seq, last_read_seq, joined_at)
    SELECT m.conversation_id, 'UMOJA', m.user_id, CASE WHEN m.user_id = c.creator THEN 'owner' ELSE 'member' END, true, 0,
           c.n, greatest(0, c.n - floor(random() * random() * 10)::int), c.created
    FROM members m JOIN convs c ON c.id = m.conversation_id;

SET session_replication_role = origin;
CREATE INDEX idx_oro_message_body_trgm ON oro_message USING gin (body gin_trgm_ops) WHERE deleted_at IS NULL;
VACUUM ANALYZE;
SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE relname LIKE 'oro_%' ORDER BY n_live_tup DESC;
SELECT pg_size_pretty(pg_database_size(current_database())) AS size;
