-- =====================================================================
-- Tracker-System — Supabase schema
--
-- Run this ONCE in your Supabase project:
--   Dashboard -> SQL Editor -> New query -> paste this file -> Run
--
-- It creates the three tables the app uses, enables row level security and
-- adds the policies that let signed-in users see each other (which is what
-- makes cross-device messaging and live location possible).
-- =====================================================================

-- ---------------------------------------------------------------- profiles
create table if not exists public.profiles (
    email       text primary key,
    name        text not null default '',
    department  text not null default '',
    phone       text not null default '',
    role        text not null default 'employee',
    created_at  timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Any signed-in user can read profiles (needed for the contact list).
drop policy if exists "profiles readable by signed-in users" on public.profiles;
create policy "profiles readable by signed-in users"
    on public.profiles for select
    to authenticated
    using (true);

-- You may only create/update your own profile row.
drop policy if exists "profiles writable by owner" on public.profiles;
create policy "profiles writable by owner"
    on public.profiles for insert
    to authenticated
    with check (email = auth.jwt() ->> 'email');

drop policy if exists "profiles updatable by owner" on public.profiles;
create policy "profiles updatable by owner"
    on public.profiles for update
    to authenticated
    using (email = auth.jwt() ->> 'email')
    with check (email = auth.jwt() ->> 'email');

-- ---------------------------------------------------------------- locations
create table if not exists public.locations (
    email       text primary key references public.profiles (email) on delete cascade,
    name        text not null default '',
    latitude    double precision not null,
    longitude   double precision not null,
    address     text not null default '',
    updated_at  timestamptz not null default now()
);

create index if not exists locations_updated_at_idx on public.locations (updated_at desc);

alter table public.locations enable row level security;

-- Everyone signed in can see everyone's latest position (that is the point of
-- a mutual-tracking app). Tighten this if you want admin-only visibility.
drop policy if exists "locations readable by signed-in users" on public.locations;
create policy "locations readable by signed-in users"
    on public.locations for select
    to authenticated
    using (true);

drop policy if exists "locations writable by owner" on public.locations;
create policy "locations writable by owner"
    on public.locations for insert
    to authenticated
    with check (email = auth.jwt() ->> 'email');

drop policy if exists "locations updatable by owner" on public.locations;
create policy "locations updatable by owner"
    on public.locations for update
    to authenticated
    using (email = auth.jwt() ->> 'email')
    with check (email = auth.jwt() ->> 'email');

-- ---------------------------------------------------------------- messages
create table if not exists public.messages (
    id          bigserial primary key,
    sender      text not null,
    recipient   text not null,
    body        text not null,
    sent_at     timestamptz not null default now(),
    read_at     timestamptz
);

create index if not exists messages_thread_idx
    on public.messages (sender, recipient, sent_at);
create index if not exists messages_recipient_idx
    on public.messages (recipient, sent_at desc);

alter table public.messages enable row level security;

-- You can read messages you sent or received, and nothing else.
drop policy if exists "messages readable by participants" on public.messages;
create policy "messages readable by participants"
    on public.messages for select
    to authenticated
    using (
        sender    = auth.jwt() ->> 'email'
        or recipient = auth.jwt() ->> 'email'
    );

-- You can only send as yourself.
drop policy if exists "messages insertable by sender" on public.messages;
create policy "messages insertable by sender"
    on public.messages for insert
    to authenticated
    with check (sender = auth.jwt() ->> 'email');

-- Only the recipient can mark a message read.
drop policy if exists "messages updatable by recipient" on public.messages;
create policy "messages updatable by recipient"
    on public.messages for update
    to authenticated
    using (recipient = auth.jwt() ->> 'email')
    with check (recipient = auth.jwt() ->> 'email');

-- =====================================================================
-- Optional: turn OFF "Confirm email" during testing so sign-up logs you in
-- immediately. Dashboard -> Authentication -> Providers -> Email.
-- =====================================================================
