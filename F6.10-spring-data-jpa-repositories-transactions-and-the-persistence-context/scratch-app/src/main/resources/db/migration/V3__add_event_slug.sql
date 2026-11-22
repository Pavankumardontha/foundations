-- V3__add_event_slug.sql
-- A slug is the event's name written for a web address: small letters, spaces turned into "-".
-- 'Sufi Night' -> 'sufi-night', as in /events/sufi-night

alter table event add column slug text;

update event set slug = lower(replace(name, ' ', '-'));

alter table event add constraint event_slug_unique unique (slug);

-- No "not null" yet: seed-dev-data.sql inserts events without a slug, and those inserts would fail.
