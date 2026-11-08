-- B BOSQICH: Supabase Auth + admin role + haqiqiy RLS policy'lar.
-- Admin login = Supabase Auth parol + email OTP. Ikkala qadam ham serverda (Edge Function) tekshiriladi;
-- to'liq tasdiqlangan sessiya admin_sessions jadvalida yoziladi va is_admin() faqat shunday sessiyani admin deb biladi.
-- Shu sababli kimdir anon kalit bilan GoTrue'ga to'g'ridan-to'g'ri murojaat qilib OTP'siz sessiya olsa ham, admin ma'lumotiga kira olmaydi.

-- Eski custom admin (parol hash) jadvali endi ishlatilmaydi.
drop table if exists public.admin_users;

-- ---------- admin tizimi ----------
create table if not exists public.admin_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  role text not null default 'admin' check (role = 'admin'),
  disabled boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.admin_sessions (
  session_id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz
);
create index if not exists idx_admin_sessions_user on public.admin_sessions(user_id);

-- OTP bosqichi holati. Kodning o'zi bu yerda saqlanmaydi (uni Supabase Auth yuboradi va tekshiradi).
create table if not exists public.login_challenges (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  email text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  attempts int not null default 0,
  used_at timestamptz
);
create index if not exists idx_login_challenges_user on public.login_challenges(user_id, created_at);

create table if not exists public.rate_limits (
  key text primary key,
  window_start timestamptz not null default now(),
  hits int not null default 0
);

create table if not exists public.audit_logs (
  id bigint generated always as identity primary key,
  admin_user_id uuid,
  admin_email text not null,
  action text not null check (char_length(action) <= 80),
  target text check (char_length(target) <= 200),
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists idx_audit_created on public.audit_logs(created_at desc);

-- Audit log o'zgartirilmaydi va o'chirilmaydi (service_role ham, egasi ham trigger orqali bloklanadi).
create or replace function public.audit_logs_immutable() returns trigger
language plpgsql set search_path = '' as $$
begin
  raise exception 'audit_logs is append-only';
end $$;
drop trigger if exists trg_audit_immutable on public.audit_logs;
create trigger trg_audit_immutable before update or delete on public.audit_logs
  for each row execute function public.audit_logs_immutable();

-- ---------- yordamchi funksiyalar ----------
-- Admin = (1) admin_profiles'da, o'chirilmagan VA (2) JWT'dagi session_id server tomonidan to'liq (parol + OTP) tasdiqlangan, bekor qilinmagan, muddati o'tmagan.
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select
    exists (select 1 from public.admin_profiles p where p.user_id = auth.uid() and not p.disabled)
    and exists (
      select 1 from public.admin_sessions s
      where s.user_id = auth.uid()
        and s.session_id = nullif(auth.jwt() ->> 'session_id', '')::uuid
        and s.revoked_at is null
        and s.expires_at > now()
    );
$$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

-- Rate limit (atomik). true = ruxsat, false = limitdan oshdi. Faqat service_role (Edge Function) chaqiradi.
create or replace function public.rate_hit(p_key text, p_limit int, p_window_seconds int) returns boolean
language plpgsql security definer set search_path = '' as $$
declare h int;
begin
  insert into public.rate_limits as r (key, window_start, hits) values (p_key, now(), 1)
  on conflict (key) do update set
    window_start = case when r.window_start < now() - make_interval(secs => p_window_seconds) then now() else r.window_start end,
    hits = case when r.window_start < now() - make_interval(secs => p_window_seconds) then 1 else r.hits + 1 end
  returning r.hits into h;
  return h <= p_limit;
end $$;
revoke all on function public.rate_hit(text, int, int) from public, anon, authenticated;
grant execute on function public.rate_hit(text, int, int) to service_role;

-- OTP urinishini atomik hisoblaydi: faqat ishlatilmagan, muddati o'tmagan va urinishi < max bo'lsa. Qolgan urinishlar sonini qaytaradi, aks holda null.
create or replace function public.challenge_attempt(p_id uuid, p_max int) returns int
language plpgsql security definer set search_path = '' as $$
declare a int;
begin
  update public.login_challenges
     set attempts = attempts + 1
   where id = p_id and used_at is null and expires_at > now() and attempts < p_max
  returning attempts into a;
  if a is null then return null; end if;
  return p_max - a;
end $$;
revoke all on function public.challenge_attempt(uuid, int) from public, anon, authenticated;
grant execute on function public.challenge_attempt(uuid, int) to service_role;

-- Bir martalik: used_at null bo'lsa belgilaydi va true qaytaradi; ikkinchi chaqiruv false (replay bloklanadi).
create or replace function public.challenge_consume(p_id uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
declare n int;
begin
  update public.login_challenges set used_at = now()
   where id = p_id and used_at is null and expires_at > now();
  get diagnostics n = row_count;
  return n = 1;
end $$;
revoke all on function public.challenge_consume(uuid) from public, anon, authenticated;
grant execute on function public.challenge_consume(uuid) to service_role;

-- ---------- RLS: avval hamma ruxsatni yopamiz, keyin aniq grant + policy ----------
do $$
declare t text;
begin
  foreach t in array array['admin_profiles','admin_sessions','login_challenges','rate_limits','audit_logs',
    'app_config','donation_config','app_versions','announcements','device_stats',
    'optimization_history','game_boost_history','diagnostics_history','donation_events','error_logs']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('alter table public.%I force row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;
end $$;
revoke all on all sequences in schema public from anon, authenticated;

-- Eski policy'larni (agar qayta ishga tushsa) tozalaymiz.
do $$
declare r record;
begin
  for r in select schemaname, tablename, policyname from pg_policies where schemaname = 'public' loop
    execute format('drop policy %I on %I.%I', r.policyname, r.schemaname, r.tablename);
  end loop;
end $$;

-- admin_profiles: foydalanuvchi faqat o'z qatorini ko'radi. Yozish/o'chirish YO'Q (adminlar faqat SQL egasi tomonidan boshqariladi).
grant select on public.admin_profiles to authenticated;
create policy admin_profiles_select_own on public.admin_profiles for select to authenticated
  using (user_id = (select auth.uid()));

-- admin_sessions, login_challenges, rate_limits: grant ham, policy ham yo'q = faqat service_role (Edge Function).

-- audit_logs: faqat admin o'qiydi. INSERT/UPDATE/DELETE klientdan yo'q (yozuvni faqat Edge Function qo'shadi; trigger o'zgartirishni bloklaydi).
grant select on public.audit_logs to authenticated;
create policy audit_logs_select_admin on public.audit_logs for select to authenticated
  using ((select public.is_admin()));

-- app_config
grant select, insert, update, delete on public.app_config to authenticated;
create policy app_config_select_admin on public.app_config for select to authenticated using ((select public.is_admin()));
create policy app_config_insert_admin on public.app_config for insert to authenticated with check ((select public.is_admin()));
create policy app_config_update_admin on public.app_config for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy app_config_delete_admin on public.app_config for delete to authenticated using ((select public.is_admin()));

-- donation_config: bitta qator (id=1). Karta/merchant ma'lumoti faqat admin. DELETE policy ataylab yo'q.
grant select, insert, update on public.donation_config to authenticated;
create policy donation_config_select_admin on public.donation_config for select to authenticated using ((select public.is_admin()));
create policy donation_config_insert_admin on public.donation_config for insert to authenticated with check ((select public.is_admin()) and id = 1);
create policy donation_config_update_admin on public.donation_config for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()) and id = 1);

-- app_versions: nashr qilinganlarini hamma o'qiy oladi (update tekshiruvi uchun), yozish faqat admin.
grant select on public.app_versions to anon;
grant select, insert, update, delete on public.app_versions to authenticated;
create policy app_versions_select_public on public.app_versions for select to anon, authenticated using (published);
create policy app_versions_select_admin on public.app_versions for select to authenticated using ((select public.is_admin()));
create policy app_versions_insert_admin on public.app_versions for insert to authenticated with check ((select public.is_admin()));
create policy app_versions_update_admin on public.app_versions for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy app_versions_delete_admin on public.app_versions for delete to authenticated using ((select public.is_admin()));

-- announcements: faol e'lonlarni hamma o'qiydi, yozish faqat admin.
grant select on public.announcements to anon;
grant select, insert, update, delete on public.announcements to authenticated;
create policy announcements_select_public on public.announcements for select to anon, authenticated using (active);
create policy announcements_select_admin on public.announcements for select to authenticated using ((select public.is_admin()));
create policy announcements_insert_admin on public.announcements for insert to authenticated with check ((select public.is_admin()));
create policy announcements_update_admin on public.announcements for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy announcements_delete_admin on public.announcements for delete to authenticated using ((select public.is_admin()));

-- Ilova anonim: hisobi yo'q, shuning uchun "o'z ma'lumoti" tushunchasi yo'q. Klientlar bu jadvallarga to'g'ridan-to'g'ri yozmaydi:
-- yozuv faqat Edge Function (validatsiya + rate limit) orqali. O'qish va retention-o'chirish faqat admin.
grant select, delete on public.device_stats, public.optimization_history, public.game_boost_history,
  public.diagnostics_history, public.error_logs to authenticated;
create policy device_stats_select_admin on public.device_stats for select to authenticated using ((select public.is_admin()));
create policy device_stats_delete_admin on public.device_stats for delete to authenticated using ((select public.is_admin()));
create policy optimization_select_admin on public.optimization_history for select to authenticated using ((select public.is_admin()));
create policy optimization_delete_admin on public.optimization_history for delete to authenticated using ((select public.is_admin()));
create policy game_boost_select_admin on public.game_boost_history for select to authenticated using ((select public.is_admin()));
create policy game_boost_delete_admin on public.game_boost_history for delete to authenticated using ((select public.is_admin()));
create policy diagnostics_select_admin on public.diagnostics_history for select to authenticated using ((select public.is_admin()));
create policy diagnostics_delete_admin on public.diagnostics_history for delete to authenticated using ((select public.is_admin()));
create policy error_logs_select_admin on public.error_logs for select to authenticated using ((select public.is_admin()));
create policy error_logs_delete_admin on public.error_logs for delete to authenticated using ((select public.is_admin()));

-- donation_events: pul yozuvlari. O'qish va holatni o'zgartirish faqat admin. INSERT faqat Edge Function (pending). DELETE yo'q: moliyaviy yozuv saqlanadi.
grant select, update on public.donation_events to authenticated;
create policy donation_events_select_admin on public.donation_events for select to authenticated using ((select public.is_admin()));
create policy donation_events_update_admin on public.donation_events for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
