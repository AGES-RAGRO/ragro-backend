INSERT INTO producer_profiles (id, member_since)
SELECT
  u.id,
  (u.created_at AT TIME ZONE 'America/Sao_Paulo')::date
FROM users u
JOIN farmers f ON f.id = u.id
LEFT JOIN producer_profiles pp ON pp.id = u.id
WHERE pp.id IS NULL;

UPDATE producer_profiles pp
SET member_since = (u.created_at AT TIME ZONE 'America/Sao_Paulo')::date
FROM users u
WHERE u.id = pp.id
  AND pp.member_since IS NULL;

ALTER TABLE producer_profiles
ALTER COLUMN member_since SET NOT NULL;
