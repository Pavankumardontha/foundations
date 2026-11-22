-- F5.10 database setup — minimal, self-contained seed for this task.
-- It creates just the two tables this task queries (venue and event) and some sample rows.
-- Safe to run more than once: tables are created only if missing, rows are skipped on conflict.
--
-- Run it against the zenith database like this (container named pg):
--   docker exec -i pg psql -U postgres -d zenith < db-setup.sql

CREATE TABLE IF NOT EXISTS venue (
    id       uuid PRIMARY KEY,
    name     text NOT NULL,
    city     text NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS event (
    id        uuid PRIMARY KEY,
    venue_id  uuid NOT NULL REFERENCES venue(id),
    name      text NOT NULL,
    starts_at timestamptz NOT NULL
);

INSERT INTO venue (id, name, city, capacity) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000),
    ('22222222-2222-2222-2222-222222222222', 'The Blue Note',   'Mumbai',      350),
    ('33333333-3333-3333-3333-333333333333', 'Lakeside Hall',   'Chennai',     800)
ON CONFLICT (id) DO NOTHING;

INSERT INTO event (id, venue_id, name, starts_at) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Monsoon Rock Night', '2026-11-20 19:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Classical Evening',  '2026-12-05 18:30+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Sufi Night',         '2027-07-10 19:30+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Indie Fest',         '2027-07-24 17:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'Jazz at the Note',   '2026-11-28 21:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'Open Mic Night',     '2026-12-12 20:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000007', '22222222-2222-2222-2222-222222222222', 'Blues Brunch',       '2027-07-18 12:00+05:30')
ON CONFLICT (id) DO NOTHING;
