-- BIR MARTA, qo'lda ishga tushiriladi (Supabase Dashboard > SQL Editor). Migration EMAS, parol YO'Q.
-- 1) Dashboard > Authentication > Users > Add user: email = admin email, parolni O'ZINGIZ o'rnating ("Auto Confirm User" yoqilgan).
-- 2) Quyidagini ishga tushiring:
insert into public.admin_profiles (user_id, email)
select id, lower(email) from auth.users where lower(email) = 'donyorabdullayev892@gmail.com'
on conflict (user_id) do nothing;
