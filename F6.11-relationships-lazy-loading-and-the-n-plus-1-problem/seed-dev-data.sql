INSERT INTO public.app_user (id, email, display_name, created_at) VALUES ('cccccccc-0000-0000-0000-000000000001', 'meera@example.com', 'Meera', '2026-10-25 12:50:24.815716+00');
INSERT INTO public.app_user (id, email, display_name, created_at) VALUES ('cccccccc-0000-0000-0000-000000000002', 'arjun@example.com', 'Arjun', '2026-10-25 12:50:24.815716+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000, NULL, '2026-10-24 13:04:21.937358+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('22222222-2222-2222-2222-222222222222', 'The Blue Note', 'Mumbai', 350, NULL, '2026-10-24 13:04:21.937358+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('33333333-3333-3333-3333-333333333333', 'Lakeside Hall', 'Chennai', 800, NULL, '2026-10-24 19:37:01.766814+00');
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Monsoon Rock Night', '2026-11-20 13:30:00+00', '2026-10-24 13:04:21.939416+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'Jazz at the Note', '2026-11-28 15:30:00+00', '2026-10-24 13:04:21.939416+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'Open Mic Night', '2026-12-12 14:30:00+00', '2026-10-24 20:34:40.288633+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Sufi Night', '2027-07-10 14:00:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Indie Fest', '2027-07-24 11:30:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000007', '22222222-2222-2222-2222-222222222222', 'Blues Brunch', '2027-07-18 06:30:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Classical Evening', '2026-12-05 13:00:00+00', '2026-10-24 13:04:21.939416+00', '2026-10-24 18:05:00.690845+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General', 1499.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP', 4999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General', 999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General', 1999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000001', 'Balcony', 2499.0000, '2026-10-24 21:30:49.481755+00');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000001', 'attendee');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000002', 'organiser');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000003', 'admin');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000002');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001');

-- F6.11 Lesson 2: one seat map for Riverside Arena, four seats in it.
INSERT INTO public.seat_map (id, venue_id, name) VALUES ('eeeeeeee-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Riverside Floor');
INSERT INTO public.seat (id, seat_map_id, row_label, seat_number) VALUES ('ffffffff-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', 'A', 1);
INSERT INTO public.seat (id, seat_map_id, row_label, seat_number) VALUES ('ffffffff-0000-0000-0000-000000000002', 'eeeeeeee-0000-0000-0000-000000000001', 'A', 2);
INSERT INTO public.seat (id, seat_map_id, row_label, seat_number) VALUES ('ffffffff-0000-0000-0000-000000000003', 'eeeeeeee-0000-0000-0000-000000000001', 'B', 1);
INSERT INTO public.seat (id, seat_map_id, row_label, seat_number) VALUES ('ffffffff-0000-0000-0000-000000000004', 'eeeeeeee-0000-0000-0000-000000000001', 'B', 2);

-- F6.11 Lesson 6: role grants - the join table grown up, with data about the link.
INSERT INTO public.role_grant (id, user_id, role_id, granted_by, granted_at) VALUES ('99999999-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001', 'system-signup', '2026-10-25 10:00:00+00');
INSERT INTO public.role_grant (id, user_id, role_id, granted_by, granted_at) VALUES ('99999999-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000002', 'admin@zenith.io', '2026-10-26 09:30:00+00');
INSERT INTO public.role_grant (id, user_id, role_id, granted_by, granted_at) VALUES ('99999999-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001', 'system-signup', '2026-10-27 14:15:00+00');

-- F6.11 Lesson 13: twenty events, each with its OWN venue - the N+1 exhibit.
-- Distinct venues matter: a repeated venue would already sit in the persistence
-- context and not fire a second select, hiding the true size of the problem.
INSERT INTO public.venue (id, name, city, capacity, created_at)
SELECT ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Venue ' || lpad(n::text, 2, '0'),
       'Pune', 500, now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.event (id, venue_id, name, starts_at, slug, created_at)
SELECT ('a1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Event ' || lpad(n::text, 2, '0'),
       timestamptz '2027-09-01 18:00:00+00' + (n || ' days')::interval,
       'load-event-' || lpad(n::text, 2, '0'),
       now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;
