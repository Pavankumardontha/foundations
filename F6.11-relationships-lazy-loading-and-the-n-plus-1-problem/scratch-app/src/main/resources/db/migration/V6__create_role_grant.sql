-- V6__create_role_grant.sql
-- Lesson 6: the join table grows up. When the relationship itself carries
-- data (who granted the role, and when), the pair table becomes a real
-- table: its own primary key, its own columns, and the old pair rule
-- kept alive by a unique constraint.

create table role_grant (
    id          uuid primary key,
    user_id     uuid not null,
    role_id     uuid not null,
    granted_by  text not null,
    granted_at  timestamptz not null default now(),
    constraint role_grant_user_fk foreign key (user_id) references app_user (id),
    constraint role_grant_role_fk foreign key (role_id) references role (id),
    constraint role_grant_pair_unique unique (user_id, role_id)
);

create index idx_role_grant_user_id on role_grant (user_id);
create index idx_role_grant_role_id on role_grant (role_id);
