--
-- PostgreSQL database dump
--

\restrict IPwXY8boUk1WO1th6Uxd90JPlwwDGEoKfD6KyayvKLetcYgpfqJgcZbHD9riMVR

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: event; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.event (
    id uuid NOT NULL,
    venue_id uuid NOT NULL,
    name text NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    cancelled_at timestamp with time zone
);


ALTER TABLE public.event OWNER TO postgres;

--
-- Name: price_tier; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.price_tier (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    name text NOT NULL,
    amount numeric(19,4) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT price_tier_amount_non_negative CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.price_tier OWNER TO postgres;

--
-- Name: venue; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.venue (
    id uuid NOT NULL,
    name text NOT NULL,
    city text NOT NULL,
    capacity integer NOT NULL,
    seat_map_hints jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT venue_capacity_positive CHECK ((capacity > 0))
);


ALTER TABLE public.venue OWNER TO postgres;

--
-- Data for Name: event; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.event (id, venue_id, name, starts_at, created_at, cancelled_at) FROM stdin;
aaaaaaaa-0000-0000-0000-000000000001	11111111-1111-1111-1111-111111111111	Monsoon Rock Night	2026-11-20 13:30:00+00	2026-10-24 13:04:21.939416+00	\N
aaaaaaaa-0000-0000-0000-000000000003	22222222-2222-2222-2222-222222222222	Jazz at the Note	2026-11-28 15:30:00+00	2026-10-24 13:04:21.939416+00	\N
aaaaaaaa-0000-0000-0000-000000000002	11111111-1111-1111-1111-111111111111	Classical Evening	2026-12-05 13:00:00+00	2026-10-24 13:04:21.939416+00	2026-10-24 18:05:00.690845+00
aaaaaaaa-0000-0000-0000-000000000004	22222222-2222-2222-2222-222222222222	Open Mic Night	2026-12-12 14:30:00+00	2026-10-24 20:34:40.288633+00	\N
aaaaaaaa-0000-0000-0000-000000000005	830e26a1-1772-4645-9617-4c1123759ca2	Harbour Lights Festival 2027	2027-01-15 12:30:00+00	2026-10-25 11:30:35.681619+00	\N
\.


--
-- Data for Name: price_tier; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.price_tier (id, event_id, name, amount, created_at) FROM stdin;
bbbbbbbb-0000-0000-0000-000000000001	aaaaaaaa-0000-0000-0000-000000000001	General	1499.0000	2026-10-24 13:04:21.940448+00
bbbbbbbb-0000-0000-0000-000000000002	aaaaaaaa-0000-0000-0000-000000000001	VIP	4999.0000	2026-10-24 13:04:21.940448+00
bbbbbbbb-0000-0000-0000-000000000003	aaaaaaaa-0000-0000-0000-000000000002	General	999.0000	2026-10-24 13:04:21.940448+00
bbbbbbbb-0000-0000-0000-000000000004	aaaaaaaa-0000-0000-0000-000000000003	General	1999.0000	2026-10-24 13:04:21.940448+00
bbbbbbbb-0000-0000-0000-000000000005	aaaaaaaa-0000-0000-0000-000000000003	Front Table	3499.0000	2026-10-24 13:04:21.940448+00
bbbbbbbb-0000-0000-0000-000000000006	aaaaaaaa-0000-0000-0000-000000000001	Balcony	2499.0000	2026-10-24 21:30:49.481755+00
bbbbbbbb-0000-0000-0000-000000000007	aaaaaaaa-0000-0000-0000-000000000005	General	799.0000	2026-10-25 11:30:35.691982+00
bbbbbbbb-0000-0000-0000-000000000008	aaaaaaaa-0000-0000-0000-000000000005	Gold	1999.0000	2026-10-25 11:30:35.691982+00
bbbbbbbb-0000-0000-0000-000000000010	aaaaaaaa-0000-0000-0000-000000000005	Early Bird	42.0000	2026-10-25 11:58:56.173184+00
\.


--
-- Data for Name: venue; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.venue (id, name, city, capacity, seat_map_hints, created_at) FROM stdin;
11111111-1111-1111-1111-111111111111	Riverside Arena	Hyderabad	12000	\N	2026-10-24 13:04:21.937358+00
22222222-2222-2222-2222-222222222222	The Blue Note	Mumbai	350	\N	2026-10-24 13:04:21.937358+00
33333333-3333-3333-3333-333333333333	Lakeside Hall	Chennai	800	\N	2026-10-24 19:37:01.766814+00
830e26a1-1772-4645-9617-4c1123759ca2	Harbour Dome	Kochi	5000	\N	2026-10-25 11:30:35.679052+00
\.


--
-- Name: event event_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.event
    ADD CONSTRAINT event_pkey PRIMARY KEY (id);


--
-- Name: price_tier price_tier_name_unique_per_event; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.price_tier
    ADD CONSTRAINT price_tier_name_unique_per_event UNIQUE (event_id, name);


--
-- Name: price_tier price_tier_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.price_tier
    ADD CONSTRAINT price_tier_pkey PRIMARY KEY (id);


--
-- Name: venue venue_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.venue
    ADD CONSTRAINT venue_pkey PRIMARY KEY (id);


--
-- Name: event event_venue_fk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.event
    ADD CONSTRAINT event_venue_fk FOREIGN KEY (venue_id) REFERENCES public.venue(id);


--
-- Name: price_tier price_tier_event_fk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.price_tier
    ADD CONSTRAINT price_tier_event_fk FOREIGN KEY (event_id) REFERENCES public.event(id);


--
-- PostgreSQL database dump complete
--

\unrestrict IPwXY8boUk1WO1th6Uxd90JPlwwDGEoKfD6KyayvKLetcYgpfqJgcZbHD9riMVR

