-- pgTAP: donation backend, Top 10 donors, e'lonlar, app update, provayderlar, audit natijasi.
-- Ishga tushirish: `supabase start && supabase test db`. Bu fayl rls.test.sql'ni almashtirmaydi, ustiga qo'shiladi.
begin;
create extension if not exists pgtap with schema extensions;
select no_plan();

-- ===== fixtures (postgres) =====
insert into auth.users (id, instance_id, aud, role, email) values
  ('a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1', gen_random_uuid(), 'authenticated', 'authenticated', 'admin2@test.local'),
  ('b2b2b2b2-b2b2-4b2b-8b2b-b2b2b2b2b2b2', gen_random_uuid(), 'authenticated', 'authenticated', 'user2@test.local');
insert into public.admin_profiles (user_id, email) values ('a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1', 'admin2@test.local');
insert into public.admin_sessions (session_id, user_id, expires_at) values
  ('e1e1e1e1-e1e1-4e1e-8e1e-e1e1e1e1e1e1', 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1', now() + interval '1 hour'),
  ('e2e2e2e2-e2e2-4e2e-8e2e-e2e2e2e2e2e2', 'b2b2b2b2-b2b2-4b2b-8b2b-b2b2b2b2b2b2', now() + interval '1 hour');

-- Donorlar: hammasi 'pending' bilan boshlanadi, keyin owner 'success'ga o'tkazadi.
insert into public.donation_events (id, amount, provider, donor_name, anonymous, consent) overriding system value values
  (9001, 50000, 'manual_card', 'Ali',     false, true),    -- ko'rinadi (rozilik bor)
  (9002, 80000, 'manual_card', 'Vali',    false, false),   -- ism bor, rozilik YO'Q -> anonim ko'rinadi
  (9003, 30000, 'qr',          'Secret',  true,  true),    -- anonim -> ism yashirin
  (9004, 10000, 'manual_card', 'Ali',     false, true),    -- Ali bilan yig'iladi (60000)
  (9005, 999999, 'manual_card','Hidden',  false, true),    -- admin yashiradi
  (9006, 777777, 'manual_card','Pending', false, true),    -- hali pending
  (9007, 555555, 'manual_card','Failed',  false, true);    -- failed
update public.donation_events set status = 'success' where id in (9001, 9002, 9003, 9004, 9005);
update public.donation_events set hidden = true where id = 9005;
update public.donation_events set status = 'failed' where id = 9007;

-- ===== donation_events qoidalari =====
select throws_ok($$insert into public.donation_events (amount, provider, status) values (5000, 'manual_card', 'success')$$, 'P0001', null, 'donation: success bilan yaratib bo''lmaydi');
select throws_ok($$insert into public.donation_events (amount, provider, status) values (5000, 'manual_card', 'failed')$$, 'P0001', null, 'donation: faqat pending bilan yaratiladi');
select throws_ok($$insert into public.donation_events (amount, provider) values (5000, 'paypal')$$, '23514', null, 'donation: noma''lum provider rad etiladi');
select throws_ok($$insert into public.donation_events (amount, provider, currency) values (5000, 'qr', 'USD')$$, '23514', null, 'donation: faqat UZS');
select throws_ok($$insert into public.donation_events (amount, provider) values (999, 'qr')$$, '23514', null, 'donation: minimal summa 1000');
select throws_ok($$insert into public.donation_events (amount, provider, donor_name) values (5000, 'qr', repeat('x', 61))$$, '23514', null, 'donation: donor_name <= 60');
select matches((select reference from public.donation_events where id = 9001), '^PB-[0-9A-F]{16}$', 'donation: reference avtomatik');
select is((select count(distinct reference) from public.donation_events)::int, (select count(*) from public.donation_events)::int, 'donation: reference noyob');
select isnt((select confirmed_at from public.donation_events where id = 9001), null, 'donation: success -> confirmed_at to''ldirilgan');
select is((select confirmed_at from public.donation_events where id = 9006), null, 'donation: pending -> confirmed_at yo''q');
select is((select status from public.donation_events where id = 9006), 'pending', 'donation: avtomatik success bo''lmaydi (pending qoldi)');
select throws_ok($$update public.donation_events set status = 'failed' where id = 9001$$, 'P0001', null, 'donation: success yakuniy, o''zgarmaydi');
select throws_ok($$update public.donation_events set status = 'pending' where id = 9007$$, 'P0001', null, 'donation: failed yakuniy, pending''ga qaytmaydi');
select throws_ok($$update public.donation_events set amount = 1 where id = 9006$$, 'P0001', null, 'donation: summa o''zgarmaydi');
select throws_ok($$update public.donation_events set provider = 'qr' where id = 9006$$, 'P0001', null, 'donation: provider o''zgarmaydi');
insert into public.donation_events (id, amount, provider) overriding system value values (9010, 5000, 'click'), (9011, 5000, 'tbc'), (9012, 5000, 'qr');
select throws_ok($$update public.donation_events set status = 'success' where id = 9010$$, 'P0001', null, 'provider Click: sozlanmagan, success BLOKLANGAN');
select throws_ok($$update public.donation_events set status = 'success' where id = 9011$$, 'P0001', null, 'provider TBC: sozlanmagan, success BLOKLANGAN');
select lives_ok($$update public.donation_events set status = 'failed' where id = 9010$$, 'provider Click: failed ruxsat');
select lives_ok($$update public.donation_events set status = 'cancelled' where id = 9012$$, 'donation: cancelled ruxsat');

-- ===== Top 10 donors =====
select is((select count(*) from public.top_donors(10))::int, 3, 'top: faqat success, yashirilmagan, guruhlangan (3 qator)');
select is((select amount from public.top_donors(10) where rank_position = 1), 80000::bigint, 'top: 1-o''rin eng katta summa');
select is((select display_name from public.top_donors(10) where rank_position = 1), null, 'top: rozilik yo''q -> ism NULL');
select is((select anonymous from public.top_donors(10) where rank_position = 1), true, 'top: rozilik yo''q -> anonymous=true');
select is((select display_name from public.top_donors(10) where rank_position = 2), 'Ali', 'top: rozilik bor -> ism ko''rinadi');
select is((select amount from public.top_donors(10) where rank_position = 2), 60000::bigint, 'top: bir xil ism yig''iladi (50000+10000)');
select is((select consent from public.top_donors(10) where rank_position = 2), true, 'top: consent=true');
select is((select count(*) from public.top_donors(10) where display_name in ('Vali', 'Secret', 'Hidden', 'Pending', 'Failed'))::int, 0, 'top: roziliksiz/anonim/yashirin/pending/failed ismlar HECH QACHON chiqmaydi');
select is((select count(*) from public.top_donors(10) where amount in (999999, 777777, 555555))::int, 0, 'top: hidden/pending/failed summalar kirmaydi');
select is((select count(*) from public.top_donors(1))::int, 1, 'top: limit 1');
select cmp_ok((select count(*) from public.top_donors(1000))::int, '<=', 10, 'top: limit 10 dan oshmaydi');

-- ===== announcements =====
insert into public.announcements (id, title, text, active, publish_at) overriding system value values
  (9101, 'live',     'visible now',   true,  now() - interval '1 hour'),
  (9102, 'future',   'scheduled',     true,  now() + interval '1 day'),
  (9103, 'inactive', 'unpublished',   false, now() - interval '1 hour');
select throws_ok($$insert into public.announcements (text, image_url) values ('x', 'http://insecure.example/i.png')$$, '23514', null, 'e''lon: rasm faqat https');
select throws_ok($$insert into public.announcements (text, title) values ('x', repeat('t', 121))$$, '23514', null, 'e''lon: title <= 120');

-- ===== app_versions =====
select throws_ok($$insert into public.app_versions (version_code, version_name, min_supported_code) values (950, 'x', 951)$$, '23514', null, 'versiya: min_supported <= latest');
select throws_ok($$insert into public.app_versions (version_code, version_name, download_url) values (951, 'x', 'http://insecure.example/a.apk')$$, '23514', null, 'versiya: download_url faqat https');
select lives_ok($$insert into public.app_versions (version_code, version_name, min_supported_code, force_update) values (952, 'ok', 900, true)$$, 'versiya: min_supported + force yoziladi');

-- ===== payment_providers =====
select is((select count(*) from public.payment_providers)::int, 6, 'providerlar: 6 ta (manual_card, qr, click, payme, uzum, tbc)');
select is((select count(*) from public.payment_providers where enabled)::int, 0, 'providerlar: default hammasi o''chiq');
select throws_ok($$insert into public.payment_providers (provider) values ('paypal')$$, '23514', null, 'providerlar: noma''lum nom rad');

-- ===== audit_logs.result =====
select throws_ok($$insert into public.audit_logs (admin_email, action, result) values ('a@b.c', 'x', 'bogus')$$, '23514', null, 'audit: result faqat success/failure/denied');
insert into public.audit_logs (admin_email, action, result) values ('a@b.c', 'probe', 'failure');
select is((select result from public.audit_logs where action = 'probe'), 'failure', 'audit: result yoziladi');
select throws_ok($$update public.audit_logs set result = 'success' where action = 'probe'$$, 'P0001', null, 'audit: result o''zgartirib bo''lmaydi');

-- ===== ANON =====
set local role anon;
select throws_ok('select * from public.payment_providers', '42501', null, 'anon: payment_providers yopiq');
select throws_ok('select * from public.top_donors(10)', '42501', null, 'anon: top_donors() to''g''ridan-to''g''ri chaqira olmaydi');
select throws_ok('select * from public.donation_events', '42501', null, 'anon: donation_events yopiq');
select is((select count(*) from public.announcements)::int, 1, 'anon: faqat faol va nashr vaqti kelgan e''lon (kelajak/nofaol ko''rinmaydi)');
select is((select title from public.announcements), 'live', 'anon: ko''rinadigan e''lon to''g''ri');
select is((select count(*) from public.app_versions where version_code = 952)::int, 1, 'anon: nashr qilingan versiya min_supported bilan ko''rinadi');
reset role;

-- ===== ODDIY USER =====
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', 'b2b2b2b2-b2b2-4b2b-8b2b-b2b2b2b2b2b2', 'role', 'authenticated', 'session_id', 'e2e2e2e2-e2e2-4e2e-8e2e-e2e2e2e2e2e2')::text, true);
select is((select count(*) from public.payment_providers)::int, 0, 'user: payment_providers ko''rinmaydi');
select lives_ok($$update public.payment_providers set enabled = true$$, 'user: RLS providerlarni o''zgartirishga yo''l qo''ymaydi');
select throws_ok('select * from public.top_donors(10)', '42501', null, 'user: top_donors() yopiq');
select is((select count(*) from public.donation_events)::int, 0, 'user: donationlar (ism/summa) ko''rinmaydi');
select lives_ok($$update public.donation_events set hidden = true$$, 'user: RLS donationlarni o''zgartirishga yo''l qo''ymaydi');
select is((select count(*) from public.announcements)::int, 1, 'user: faqat ommaviy e''lon');
select throws_ok($$insert into public.announcements (text) values ('hack')$$, '42501', null, 'user: e''lon yoza olmaydi');
reset role;
select is((select count(*) from public.payment_providers where enabled)::int, 0, 'user: provider holati o''zgarmagan');
select is((select count(*) from public.donation_events where hidden)::int, 1, 'user: donation hidden holati o''zgarmagan');

-- ===== ADMIN =====
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1', 'role', 'authenticated', 'session_id', 'e1e1e1e1-e1e1-4e1e-8e1e-e1e1e1e1e1e1')::text, true);
select is(public.is_admin(), true, 'admin2: is_admin()');
select is((select count(*) from public.payment_providers)::int, 6, 'admin: providerlarni ko''radi');
select lives_ok($$update public.payment_providers set enabled = true where provider = 'qr'$$, 'admin: providerni yoqadi');
select throws_ok($$delete from public.payment_providers$$, '42501', null, 'admin: providerni o''chira olmaydi');
select throws_ok('select * from public.top_donors(10)', '42501', null, 'admin: top_donors() klientdan yopiq (faqat Edge Function)');
select is((select count(*) from public.announcements)::int, 3, 'admin: barcha e''lonlarni ko''radi (draft/kelajak ham)');
select lives_ok($$insert into public.announcements (title, text, image_url, publish_at) values ('t', 'm', 'https://example.com/i.png', now() + interval '2 days')$$, 'admin: e''lon (rasm + rejalashtirilgan vaqt) yozadi');
select lives_ok($$update public.announcements set active = false where id = 9101$$, 'admin: e''lonni unpublish qiladi');
select lives_ok($$delete from public.announcements where id = 9103$$, 'admin: e''lonni o''chiradi');
select lives_ok($$update public.donation_events set status = 'success' where id = 9006$$, 'admin: manual_card pending -> success (qo''lda tasdiqlash)');
select lives_ok($$update public.donation_events set hidden = true where id = 9004$$, 'admin: donorni Top 10 dan yashiradi');
select throws_ok($$update public.donation_events set status = 'success' where id = 9011$$, 'P0001', null, 'admin: TBC (sozlanmagan) success qila olmaydi');
select throws_ok($$update public.donation_events set status = 'pending' where id = 9001$$, 'P0001', null, 'admin: yakuniy statusni qaytara olmaydi');
select lives_ok($$update public.donation_config set qr_payload = 'https://example.com/pay', description = 'test' where id = 1$$, 'admin: QR + description yozadi');
select lives_ok($$update public.app_versions set min_supported_code = 4 where version_code = 4$$, 'admin: minimal versiyani o''zgartiradi');
reset role;

select * from finish();
rollback;
