-- V5__create_seat_map_and_seat.sql
-- Tables for the seating domain: a venue has seat maps, a seat map has seats.
-- The foreign key always lives on the "many" side:
--   seat_map points at venue  (column seat_map.venue_id),
--   seat     points at seat_map (column seat.seat_map_id).

create table seat_map (
    id        uuid primary key,
    venue_id  uuid not null,
    name      text not null,
    constraint seat_map_venue_fk foreign key (venue_id) references venue (id),
    constraint seat_map_name_unique_per_venue unique (venue_id, name)
);

create index idx_seat_map_venue_id on seat_map (venue_id);

create table seat (
    id           uuid primary key,
    seat_map_id  uuid not null,
    row_label    text not null,
    seat_number  integer not null,
    constraint seat_seat_map_fk foreign key (seat_map_id) references seat_map (id),
    constraint seat_unique_within_map unique (seat_map_id, row_label, seat_number),
    constraint seat_number_positive check (seat_number > 0)
);

create index idx_seat_seat_map_id on seat (seat_map_id);
