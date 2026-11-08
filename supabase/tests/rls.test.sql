-- pgTAP: RLS + admin role + OTP challenge + rate limit. Ishga tushirish: `supabase start && supabase test db`
begin;
create extension if not exists pgtap with schema extensions;
select no_plan();

-- ===== fixtures (postgres roli) =====
insert into auth.users (id, instance_id, aud, role, email) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',gen_random_uuid(),'authenticated','authenticated','admin@test.local'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1',gen_random_uuid(),'authenticated','authenticated','user@test.local'),
  ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1',gen_random_uuid(),'authenticated','authenticated','disabled@test.local');
insert into public.admin_profiles (user_id, email, disabled) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','admin@test.local', false),
  ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1','disabled@test.local', true);
insert into public.admin_sessions (session_id, user_id, expires_at, revoked_at) values
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', now() + interval '1 hour', null),            -- yaroqli admin
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', now() - interval '1 minute', null),          -- muddati o'tgan
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', now() + interval '1 hour', now()),           -- bekor qilingan
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee4','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', now() + interval '1 hour', null),            -- admin emas
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5','cccccccc-cccc-4ccc-8ccc-ccccccccccc1', now() + interval '1 hour', null);            -- o'chirilgan admin
insert into public.donation_events (amount, provider) values (5000, 'manual_card');
insert into public.audit_logs (admin_email, action) values ('admin@test.local', 'seed');
insert into public.app_versions (version_code, version_name, published) values (900, 'pub', true), (901, 'draft', false);
insert into public.announcements (text, active) values ('on', true), ('off', false);
insert into public.error_logs (message) values ('e');

-- ===== ANON =====
set local role anon;
select throws_ok('select * from public.donation_events', '42501', null, 'anon: donation_events yopiq');
select throws_ok('select * from public.donation_config', '42501', null, 'anon: donation_config yopiq (karta)');
select throws_ok('select * from public.audit_logs', '42501', null, 'anon: audit_logs yopiq');
select throws_ok('select * from public.admin_profiles', '42501', null, 'anon: admin_profiles yopiq');
select throws_ok('select * from public.admin_sessions', '42501', null, 'anon: admin_sessions yopiq');
select throws_ok('select * from public.login_challenges', '42501', null, 'anon: login_challenges yopiq');
select throws_ok('select * from public.rate_limits', '42501', null, 'anon: rate_limits yopiq');
select throws_ok('select * from public.device_stats', '42501', null, 'anon: device_stats yopiq');
select is((select count(*) from public.app_versions)::int, 2, 'anon: faqat published versiyalar');
select is((select count(*) from public.announcements)::int, 1, 'anon: faqat faol e''lon');
select throws_ok($$insert into public.app_versions (version_code, version_name) values (999,'x')$$, '42501', null, 'anon: versiya yoza olmaydi');
select throws_ok($$insert into public.announcements (text) values ('x')$$, '42501', null, 'anon: e''lon yoza olmaydi');
select throws_ok('select public.is_admin()', '42501', null, 'anon: is_admin() chaqira olmaydi');
select throws_ok($$select public.rate_hit('k',1,60)$$, '42501', null, 'anon: rate_hit yopiq');
reset role;

-- ===== ODDIY (admin emas) USER: haqiqiy sessiya bor, lekin admin_profiles yo'q =====
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee4')::text, true);
select is(public.is_admin(), false, 'user: is_admin() = false');
select is((select count(*) from public.donation_events)::int, 0, 'user: donation_events ko''rinmaydi');
select is((select count(*) from public.audit_logs)::int, 0, 'user: audit_logs ko''rinmaydi');
select is((select count(*) from public.donation_config)::int, 0, 'user: donation_config ko''rinmaydi');
select is((select count(*) from public.error_logs)::int, 0, 'user: error_logs ko''rinmaydi');
select is((select count(*) from public.admin_profiles)::int, 0, 'user: boshqa adminlar ko''rinmaydi');
select lives_ok($$update public.donation_events set status = 'success'$$, 'user: RLS orqali donationni tasdiqlay olmaydi');
select throws_ok($$insert into public.app_versions (version_code, version_name) values (998,'x')$$, '42501', null, 'user: versiya yoza olmaydi (RLS)');
select throws_ok($$insert into public.audit_logs (admin_email, action) values ('x','x')$$, '42501', null, 'user: audit_logs yoza olmaydi');
select throws_ok('select * from public.admin_sessions', '42501', null, 'user: admin_sessions yopiq');
select throws_ok($$select public.rate_hit('k',1,60)$$, '42501', null, 'user: rate_hit yopiq');
select throws_ok($$select public.challenge_consume(gen_random_uuid())$$, '42501', null, 'user: challenge_consume yopiq');
reset role;
select is((select status from public.donation_events limit 1), 'pending', 'user: donation statusini o''zgartira olmadi');

-- ===== ADMIN, lekin yaroqsiz sessiyalar =====
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2')::text, true);
select is(public.is_admin(), false, 'admin + muddati o''tgan sessiya: admin EMAS');
select is((select count(*) from public.donation_events)::int, 0, 'muddati o''tgan sessiya: ma''lumot yo''q');
select set_config('request.jwt.claims', json_build_object('sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3')::text, true);
select is(public.is_admin(), false, 'admin + bekor qilingan sessiya: admin EMAS');
select set_config('request.jwt.claims', json_build_object('sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','role','authenticated')::text, true);
select is(public.is_admin(), false, 'admin + session_id yo''q (OTP aylanib o''tilgan): admin EMAS');
select is((select count(*) from public.audit_logs)::int, 0, 'OTP''siz sessiya: audit_logs yo''q');
select set_config('request.jwt.claims', json_build_object('sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5')::text, true);
select is(public.is_admin(), false, 'boshqa userning sessiyasi: admin EMAS');
select set_config('request.jwt.claims', json_build_object('sub','cccccccc-cccc-4ccc-8ccc-ccccccccccc1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5')::text, true);
select is(public.is_admin(), false, 'o''chirilgan (disabled) admin: admin EMAS');
reset role;

-- ===== YAROQLI ADMIN =====
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','role','authenticated','session_id','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1')::text, true);
select is(public.is_admin(), true, 'admin: is_admin() = true');
select is((select count(*) from public.donation_events)::int, 1, 'admin: donation_events ko''radi');
select cmp_ok((select count(*) from public.audit_logs)::int, '>=', 1, 'admin: audit_logs ko''radi');
select is((select count(*) from public.donation_config)::int, 1, 'admin: donation_config ko''radi');
select is((select count(*) from public.app_versions)::int, 3, 'admin: draft versiyani ham ko''radi');
select lives_ok($$update public.donation_events set status = 'success'$$, 'admin: donationni tasdiqlaydi');
select is((select status from public.donation_events limit 1), 'success', 'admin: donation statusi tasdiqlandi');
select lives_ok($$insert into public.app_versions (version_code, version_name) values (902,'x')$$, 'admin: versiya yozadi');
select lives_ok($$insert into public.announcements (text) values ('hello')$$, 'admin: e''lon yozadi');
select throws_ok($$delete from public.donation_events$$, '42501', null, 'admin: donation yozuvini o''chira olmaydi');
select throws_ok($$update public.audit_logs set action = 'x'$$, '42501', null, 'admin: audit_logs o''zgartira olmaydi');
select throws_ok($$delete from public.audit_logs$$, '42501', null, 'admin: audit_logs o''chira olmaydi');
select throws_ok($$insert into public.audit_logs (admin_email, action) values ('x','x')$$, '42501', null, 'admin: klientdan audit yoza olmaydi');
select throws_ok($$delete from public.donation_config$$, '42501', null, 'admin: donation_config qatorini o''chira olmaydi');
select throws_ok('select * from public.admin_sessions', '42501', null, 'admin: admin_sessions klientga yopiq');
select throws_ok($$insert into public.admin_profiles (user_id,email) values ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','user@test.local')$$, '42501', null, 'admin: o''zini/boshqani admin qila olmaydi');
select is((select count(*) from public.admin_profiles)::int, 1, 'admin: faqat o''z admin_profiles qatorini ko''radi');
reset role;

-- ===== audit_logs trigger: egasi ham o'zgartira olmaydi =====
select throws_ok($$update public.audit_logs set action = 'x'$$, 'P0001', null, 'trigger: UPDATE bloklangan');
select throws_ok($$delete from public.audit_logs$$, 'P0001', null, 'trigger: DELETE bloklangan');

-- ===== OTP challenge: urinish limiti, bir martalik, expiry =====
insert into public.login_challenges (id, user_id, email, expires_at) values
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd1','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','admin@test.local', now() + interval '10 minutes'),
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd2','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','admin@test.local', now() - interval '1 minute');
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5), 4, 'OTP: 1-urinish, 4 qoldi');
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5), 3, 'OTP: 2-urinish, 3 qoldi');
select public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5);
select public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5);
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5), 0, 'OTP: 5-urinish, 0 qoldi');
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 5), null, 'OTP: 6-urinish BLOKLANGAN');
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd2', 5), null, 'OTP: muddati o''tgan challenge urinish qabul qilmaydi');
select is(public.challenge_consume('dddddddd-dddd-4ddd-8ddd-ddddddddddd2'), false, 'OTP expiry: o''tgan challenge iste''mol qilinmaydi');
insert into public.login_challenges (id, user_id, email, expires_at) values
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd3','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','admin@test.local', now() + interval '10 minutes');
select is(public.challenge_consume('dddddddd-dddd-4ddd-8ddd-ddddddddddd3'), true, 'OTP: birinchi iste''mol OK');
select is(public.challenge_consume('dddddddd-dddd-4ddd-8ddd-ddddddddddd3'), false, 'OTP REPLAY: ikkinchi iste''mol BLOKLANGAN');
select is(public.challenge_attempt('dddddddd-dddd-4ddd-8ddd-ddddddddddd3', 5), null, 'OTP: ishlatilgan challenge urinish qabul qilmaydi');

-- ===== rate limit =====
select is(public.rate_hit('t:k', 2, 60), true, 'rate: 1-so''rov ruxsat');
select is(public.rate_hit('t:k', 2, 60), true, 'rate: 2-so''rov ruxsat');
select is(public.rate_hit('t:k', 2, 60), false, 'rate: 3-so''rov BLOKLANGAN');
update public.rate_limits set window_start = now() - interval '2 minutes' where key = 't:k';
select is(public.rate_hit('t:k', 2, 60), true, 'rate: oyna tugagach qayta ochiladi');

select * from finish();
rollback;
