-- B BOSQICH (yakun): donation backend, Top 10 donors, e'lonlar, ilova yangilanishi, provayderlar, audit natijasi.
-- Oldingi migrationlarga TEGILMAYDI; faqat ustiga qo'shiladi. Soxta to'lov muvaffaqiyati yo'q:
--  * donation_events faqat 'pending' bilan yaratiladi (trigger majburlaydi);
--  * Click/Payme/Uzum/TBC uchun 'success' qilib bo'lmaydi (merchant integratsiya yo'q);
--  * 'success'ni faqat admin (RLS is_admin) pending'dan qo'yishi mumkin.

-- ---------- umumiy updated_at trigger ----------
create or replace function public.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;

-- ---------- donation_events ----------
alter table public.donation_events
  add column if not exists currency text not null default 'UZS',
  add column if not exists donor_name text,
  add column if not exists anonymous boolean not null default true,
  add column if not exists consent boolean not null default false,
  add column if not exists hidden boolean not null default false,
  add column if not exists reference text,
  add column if not exists updated_at timestamptz not null default now();

-- Eski qiymatlarni yangi modelga o'tkazamiz (ma'lumot yo'qolmaydi).
alter table public.donation_events drop constraint if exists donation_events_status_check;
update public.donation_events set status = 'success' where status = 'confirmed';
update public.donation_events set status = 'failed' where status = 'rejected';
update public.donation_events set provider = 'manual_card' where provider is null or provider in ('manual', 'card');
update public.donation_events set provider = 'manual_card'
  where provider not in ('manual_card', 'qr', 'click', 'payme', 'uzum', 'tbc');
update public.donation_events
  set reference = 'PB-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 16))
  where reference is null;

alter table public.donation_events
  alter column provider set not null,
  alter column provider set default 'manual_card',
  alter column reference set not null,
  alter column reference set default ('PB-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 16)));

alter table public.donation_events
  add constraint donation_events_status_check check (status in ('pending', 'success', 'failed', 'cancelled')),
  add constraint donation_events_provider_check check (provider in ('manual_card', 'qr', 'click', 'payme', 'uzum', 'tbc')),
  add constraint donation_events_currency_check check (currency = 'UZS'),
  add constraint donation_events_donor_name_check check (donor_name is null or char_length(donor_name) between 1 and 60),
  add constraint donation_events_reference_key unique (reference);

create index if not exists idx_don_status on public.donation_events(status, created_at desc);

-- Himoya trigger'i (service_role ham, egasi ham o'tib keta olmaydi).
create or replace function public.donation_events_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    if new.status <> 'pending' then
      raise exception 'donation must start as pending' using errcode = 'P0001';
    end if;
    new.confirmed_at := null;
    new.hidden := false;
    return new;
  end if;
  -- UPDATE
  if new.amount <> old.amount or new.provider <> old.provider or new.reference <> old.reference
     or new.currency <> old.currency or new.created_at <> old.created_at then
    raise exception 'donation core fields are immutable' using errcode = 'P0001';
  end if;
  if new.status <> old.status then
    if old.status <> 'pending' then
      raise exception 'donation status is final' using errcode = 'P0001';
    end if;
    if new.status = 'success' then
      if new.provider in ('click', 'payme', 'uzum', 'tbc') then
        raise exception 'provider not configured: cannot mark success' using errcode = 'P0001';
      end if;
      new.confirmed_at := now();
    else
      new.confirmed_at := null;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_donation_guard on public.donation_events;
create trigger trg_donation_guard before insert or update on public.donation_events
  for each row execute function public.donation_events_guard();
drop trigger if exists trg_donation_touch on public.donation_events;
create trigger trg_donation_touch before update on public.donation_events
  for each row execute function public.touch_updated_at();

-- ---------- Top 10 donors ----------
-- Faqat 'success' + admin yashirmagan. Haqiqiy ism FAQAT anonymous=false VA consent=true bo'lsa chiqadi.
-- Anonim/roziliksiz to'lovlar bitta-bitta (ulab bo'lmaydi), roziligi bor bir xil ismlar yig'iladi.
create or replace function public.top_donors(p_limit int default 10)
returns table (rank_position int, display_name text, anonymous boolean, amount bigint, currency text, donated_at timestamptz, consent boolean)
language sql stable security definer set search_path = '' as $$
  with s as (
    select e.id, e.amount, e.created_at,
           (not e.anonymous and e.consent and coalesce(btrim(e.donor_name), '') <> '') as shown,
           btrim(e.donor_name) as nm
    from public.donation_events e
    where e.status = 'success' and not e.hidden
  ), k as (
    select s.*, case when s.shown then 'n:' || lower(s.nm) else 'a:' || s.id::text end as grp from s
  ), g as (
    select grp,
           sum(amount)::bigint as amt,
           max(created_at) as last_at,
           bool_and(shown) as shown,
           (array_agg(nm order by created_at desc))[1] as nm
    from k group by grp
  )
  select (row_number() over (order by amt desc, last_at asc))::int,
         case when shown then nm else null end,
         not shown,
         amt,
         'UZS'::text,
         last_at,
         shown
  from g
  order by 1
  limit least(greatest(coalesce(p_limit, 10), 1), 10);
$$;
revoke all on function public.top_donors(int) from public, anon, authenticated;
grant execute on function public.top_donors(int) to service_role;

-- ---------- donation_config kengaytmasi ----------
alter table public.donation_config
  add column if not exists description text not null default '',
  add column if not exists qr_payload text not null default '';   -- QR matni/havolasi (karta CVV/PIN EMAS)
alter table public.donation_config
  add constraint donation_config_qr_len check (char_length(qr_payload) <= 1000),
  add constraint donation_config_desc_len check (char_length(description) <= 1000);
drop trigger if exists trg_donation_config_touch on public.donation_config;
create trigger trg_donation_config_touch before update on public.donation_config
  for each row execute function public.touch_updated_at();

-- Provayderlar: faqat yoqilgan/o'chirilgan bayrog'i. Merchant sirlari bazada ham YO'Q (faqat Edge Function env).
create table if not exists public.payment_providers (
  provider text primary key check (provider in ('manual_card', 'qr', 'click', 'payme', 'uzum', 'tbc')),
  enabled boolean not null default false,
  updated_at timestamptz not null default now()
);
insert into public.payment_providers (provider) values ('manual_card'), ('qr'), ('click'), ('payme'), ('uzum'), ('tbc')
  on conflict do nothing;
drop trigger if exists trg_providers_touch on public.payment_providers;
create trigger trg_providers_touch before update on public.payment_providers
  for each row execute function public.touch_updated_at();

-- ---------- announcements ----------
alter table public.announcements
  add column if not exists title text not null default '',
  add column if not exists image_url text,
  add column if not exists publish_at timestamptz not null default now(),
  add column if not exists updated_at timestamptz not null default now();
alter table public.announcements
  add constraint announcements_title_len check (char_length(title) <= 120),
  add constraint announcements_image_https check (image_url is null or (image_url ~ '^https://' and char_length(image_url) <= 500));
drop trigger if exists trg_announcements_touch on public.announcements;
create trigger trg_announcements_touch before update on public.announcements
  for each row execute function public.touch_updated_at();

-- Ommaviy o'qish: faqat faol VA nashr vaqti kelgan e'lonlar.
drop policy if exists announcements_select_public on public.announcements;
create policy announcements_select_public on public.announcements for select to anon, authenticated
  using (active and publish_at <= now());

-- ---------- app_versions ----------
alter table public.app_versions
  add column if not exists min_supported_code int not null default 0,
  add column if not exists updated_at timestamptz not null default now();
alter table public.app_versions
  add constraint app_versions_min_supported check (min_supported_code >= 0 and min_supported_code <= version_code),
  add constraint app_versions_url_https check (download_url = '' or download_url ~ '^https://');
drop trigger if exists trg_versions_touch on public.app_versions;
create trigger trg_versions_touch before update on public.app_versions
  for each row execute function public.touch_updated_at();

-- ---------- audit_logs: natija ----------
alter table public.audit_logs
  add column if not exists result text not null default 'success' check (result in ('success', 'failure', 'denied'));

-- ---------- RLS: yangi jadval ----------
alter table public.payment_providers enable row level security;
alter table public.payment_providers force row level security;
revoke all on public.payment_providers from anon, authenticated;
grant select, insert, update on public.payment_providers to authenticated;
create policy payment_providers_select_admin on public.payment_providers for select to authenticated using ((select public.is_admin()));
create policy payment_providers_insert_admin on public.payment_providers for insert to authenticated with check ((select public.is_admin()));
create policy payment_providers_update_admin on public.payment_providers for update to authenticated
  using ((select public.is_admin())) with check ((select public.is_admin()));
