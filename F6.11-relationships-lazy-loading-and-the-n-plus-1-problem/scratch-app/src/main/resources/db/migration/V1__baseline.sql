-- V1__baseline.sql
-- The complete zenith schema as it stood after the hand-made era:
--   F5.2 tables (venue, event, price_tier), F5.3 constraints and indexes,
--   F5.4 cancelled_at, F5.9 users and roles.
-- From this file onward, migrations own the schema. Never edit this file after it runs.

create table venue (
    id              uuid primary key,
    name            text not null,
    city            text not null,
    capacity        integer not null,
    seat_map_hints  jsonb,
    created_at      timestamptz not null default now(),
    constraint venue_capacity_positive check (capacity > 0)
);

create table event (
    id            uuid primary key,
    venue_id      uuid not null,
    name          text not null,
    starts_at     timestamptz not null,
    created_at    timestamptz not null default now(),
    cancelled_at  timestamptz,
    constraint event_venue_fk foreign key (venue_id) references venue (id)
);

create index idx_event_venue_id  on event (venue_id);
create index idx_event_starts_at on event (starts_at);

create table price_tier (
    id          uuid primary key,
    event_id    uuid not null,
    name        text not null,
    amount      numeric(19,4) not null,
    created_at  timestamptz not null default now(),
    constraint price_tier_event_fk foreign key (event_id) references event (id),
    constraint price_tier_name_unique_per_event unique (event_id, name),
    constraint price_tier_amount_non_negative check (amount >= 0)
);

create index idx_price_tier_event_id on price_tier (event_id);

create table app_user (
    id            uuid primary key,
    email         text not null,
    display_name  text not null,
    created_at    timestamptz not null default now(),
    constraint app_user_email_unique unique (email)
);

create table role (
    id    uuid primary key,
    name  text not null,
    constraint role_name_unique unique (name)
);

create table user_role (
    user_id  uuid not null,
    role_id  uuid not null,
    constraint user_role_pk primary key (user_id, role_id),
    constraint user_role_user_fk foreign key (user_id) references app_user (id),
    constraint user_role_role_fk foreign key (role_id) references role (id)
);
