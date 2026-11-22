-- V4__create_ticket_order.sql
-- One row per ticket order, so the rollback lessons have real rows to write and to undo.
-- "generated always as identity": the database numbers the rows 1, 2, 3, ... by itself,
-- and refuses any id you try to give it.

create table ticket_order (
    id              bigint generated always as identity primary key,
    event_id        uuid not null,
    customer_email  text not null,
    quantity        integer not null,
    constraint ticket_order_event_fk foreign key (event_id) references event (id),
    constraint ticket_order_quantity_positive check (quantity > 0)
);

create index idx_ticket_order_event_id on ticket_order (event_id);
