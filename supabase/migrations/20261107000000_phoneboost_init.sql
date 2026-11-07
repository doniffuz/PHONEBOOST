-- PHONEBOOST MAX: bazaviy struktura. Shaxsiy ma'lumot yig'ilmaydi (anonim install_id, ismlar/telefon/email yo'q).
-- RLS yoqilgan va policy YO'Q: anon/authenticated rollari hech narsani o'qiy/yoza olmaydi.
-- Barcha kirish faqat Edge Function (service_role) orqali, u serverda autentifikatsiya qiladi.

create extension if not exists pgcrypto;

create table if not exists public.admin_users (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  password_hash text not null,
  salt text not null,
  role text not null default 'admin' check (role in ('admin')),
  created_at timestamptz not null default now(),
  last_login timestamptz
);

create table if not exists public.app_config (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.donation_config (
  id int primary key default 1 check (id = 1),
  enabled boolean not null default false,
  min_amount bigint not null default 1000 check (min_amount >= 1000),
  presets bigint[] not null default '{1000,5000,10000,20000,50000,100000}',
  allow_custom boolean not null default true,
  providers jsonb not null default '["payme","click"]'::jsonb,
  instructions text not null default '',
  bank text not null default '',
  holder text not null default '',
  card text not null default '',          -- faqat admin orqali kiritiladi; repoda YO'Q
  updated_at timestamptz not null default now()
);

create table if not exists public.app_versions (
  id bigint generated always as identity primary key,
  version_code int not null unique,
  version_name text not null,
  changelog text not null default '',
  download_url text not null default '',
  force_update boolean not null default false,
  published boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.announcements (
  id bigint generated always as identity primary key,
  text text not null check (char_length(text) between 1 and 500),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.device_stats (
  id bigint generated always as identity primary key,
  install_id text not null unique check (char_length(install_id) between 8 and 64),
  app_version text,
  android_sdk int,
  created_at timestamptz not null default now()
);

create table if not exists public.optimization_history (
  id bigint generated always as identity primary key,
  install_id text not null,
  score int check (score between 0 and 100),
  result text check (result in ('GOOD','WARNING','CRITICAL')),
  created_at timestamptz not null default now()
);

create table if not exists public.game_boost_history (
  id bigint generated always as identity primary key,
  install_id text not null,
  result text check (result in ('GOOD','WARNING','CRITICAL')),
  created_at timestamptz not null default now()
);

create table if not exists public.diagnostics_history (
  id bigint generated always as identity primary key,
  install_id text not null,
  score int check (score between 0 and 100),
  result text check (result in ('GOOD','WARNING','CRITICAL')),
  created_at timestamptz not null default now()
);

-- Faqat admin "confirmed" qila oladi. Ilova to'lovni hech qachon o'zi tasdiqlamaydi.
create table if not exists public.donation_events (
  id bigint generated always as identity primary key,
  amount bigint not null check (amount >= 1000),
  provider text,
  status text not null default 'pending' check (status in ('pending','confirmed','rejected')),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz
);

create table if not exists public.error_logs (
  id bigint generated always as identity primary key,
  source text not null default 'app',
  message text not null check (char_length(message) <= 500),
  app_version text,
  created_at timestamptz not null default now()
);

create index if not exists idx_opt_created on public.optimization_history(created_at);
create index if not exists idx_game_created on public.game_boost_history(created_at);
create index if not exists idx_diag_created on public.diagnostics_history(created_at);
create index if not exists idx_dev_created on public.device_stats(created_at);
create index if not exists idx_don_created on public.donation_events(created_at);
create index if not exists idx_err_created on public.error_logs(created_at);

-- RLS: hamma jadvalda yoqilgan, policy yo'q = anon/authenticated uchun to'liq yopiq.
do $$
declare t text;
begin
  foreach t in array array['admin_users','app_config','donation_config','app_versions','announcements',
    'device_stats','optimization_history','game_boost_history','diagnostics_history','donation_events','error_logs']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('alter table public.%I force row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;
end $$;

-- Boshlang'ich qiymatlar (haqiqiy ma'lumot emas, faqat konfiguratsiya): joriy ilova versiyasi va o'chiq maintenance.
insert into public.donation_config (id) values (1) on conflict do nothing;
insert into public.app_config (key, value) values ('maintenance', '{"enabled": false, "message": ""}') on conflict do nothing;
insert into public.app_versions (version_code, version_name, changelog, download_url)
  values (4, '0.4.0', 'PHONEBOOST MAX', '') on conflict do nothing;
