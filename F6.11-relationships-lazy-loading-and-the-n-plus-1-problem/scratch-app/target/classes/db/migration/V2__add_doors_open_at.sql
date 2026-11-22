-- V2__add_doors_open_at.sql
-- The proper fix for the Lesson 17 drift: the entity gained doorsOpenAt,
-- so the schema gains doors_open_at - as a new, versioned, reviewed migration.
-- V1 stays untouched: it is history.

alter table event add column doors_open_at timestamptz;
