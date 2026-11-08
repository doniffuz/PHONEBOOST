// PHONEBOOST backend (Supabase Edge Function, Deno).
// Admin kirish: Supabase Auth parol + email OTP (ikkalasi ham SERVERDA tekshiriladi), keyin admin_sessions orqali to'liq tasdiqlangan sessiya.
// Ochiq endpointlar: /update, /donation-config, /donation-status, /top-donors, /announcements, /status, /ingest, /donation-event (hammasida rate limit).
// Sirlar (SUPABASE_SERVICE_ROLE_KEY) faqat funksiya muhitida: kodda, repoda, APK va frontendda YO'Q. Parol/OTP/token/karta logga yozilmaydi.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import {
  PROVIDER_IDS, cleanDonorName, clientIp, corsOrigin, decideUpdate, donationTransition, isEmail, isGateway, isHttpsStrict,
  isOtp, isProvider, isReference, isUuid, maskEmail, normalizeCard, providerStatus, redactDetails, sessionIdOf,
} from "./lib.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ALLOWED_ORIGINS = Deno.env.get("ADMIN_ALLOWED_ORIGINS") ?? ""; // vergul bilan: admin web panel domenlari

const db = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

const CHALLENGE_TTL_SEC = 600;
const OTP_MAX_ATTEMPTS = 5;
const ADMIN_SESSION_TTL_SEC = 8 * 3600;
const enc = new TextEncoder();

// ---------- yordamchilar ----------
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status: number, code: string) => json({ error: code }, status);
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** IP/email xom holda saqlanmaydi: HMAC(service kalit) qisqartmasi. */
async function keyHash(label: string, value: string): Promise<string> {
  const k = await crypto.subtle.importKey("raw", enc.encode(SERVICE_KEY), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", k, enc.encode(`${label}:${value}`)));
  return Array.from(sig.slice(0, 16)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** true = ruxsat. Bazada xato bo'lsa YOPIQ (false). */
async function allow(key: string, max: number, windowSec: number): Promise<boolean> {
  const { data, error } = await db.rpc("rate_hit", { p_key: key, p_limit: max, p_window_seconds: windowSec });
  return !error && data === true;
}

async function gotrue(path: string, body: unknown, bearer?: string) {
  try {
    const r = await fetch(`${SUPABASE_URL}/auth/v1${path}`, {
      method: "POST",
      headers: { apikey: ANON_KEY, "content-type": "application/json", ...(bearer ? { authorization: `Bearer ${bearer}` } : {}) },
      body: JSON.stringify(body),
    });
    let j: Record<string, any> | null = null;
    try { j = await r.json(); } catch { /* bo'sh javob */ }
    return { ok: r.ok, status: r.status, json: j };
  } catch {
    return { ok: false, status: 0, json: null };
  }
}
const dropSession = (accessToken: string) => gotrue("/logout?scope=local", {}, accessToken);

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const t = await req.text();
    if (t.length > 20000) return {};
    const j = JSON.parse(t);
    return j && typeof j === "object" ? j : {};
  } catch {
    return {};
  }
}
const str = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");
const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : null);
const days = (url: URL) => {
  const d = Number(url.searchParams.get("days"));
  return [7, 30, 90].includes(d) ? d : 7;
};
const since = (d: number) => new Date(Date.now() - d * 86400_000).toISOString();
const HTTPS = (u: string) => u === "" || /^https:\/\//i.test(u);

async function count(table: string, from: string, extra?: (q: any) => any): Promise<number> {
  let q = db.from(table).select("id", { count: "exact", head: true }).gte("created_at", from);
  if (extra) q = extra(q);
  const { count: c, error } = await q;
  if (error) throw error;
  return c ?? 0;
}

async function historyStats(table: string, d: number, withScore: boolean) {
  const from = since(d);
  const cols = withScore ? "score,result,created_at" : "result,created_at";
  const { data, error } = await db.from(table).select(cols).gte("created_at", from).limit(20000);
  if (error) throw error;
  const rows = (data ?? []) as Array<{ score?: number | null; result?: string | null; created_at: string }>;
  const byResult: Record<string, number> = { GOOD: 0, WARNING: 0, CRITICAL: 0 };
  const perDay: Record<string, number> = {};
  let sum = 0, n = 0;
  for (const r of rows) {
    if (r.result && r.result in byResult) byResult[r.result]++;
    const day = r.created_at.slice(0, 10);
    perDay[day] = (perDay[day] ?? 0) + 1;
    if (typeof r.score === "number") { sum += r.score; n++; }
  }
  return {
    days: d,
    count: rows.length,
    avgScore: n ? Math.round(sum / n) : null,
    byResult,
    series: Object.entries(perDay).sort().map(([date, c]) => ({ date, count: c })),
  };
}

// ---------- ochiq endpointlar ----------
async function publicUpdate(url: URL) {
  const { data } = await db.from("app_versions").select("*").eq("published", true)
    .order("version_code", { ascending: false }).limit(1).maybeSingle();
  if (!data) return fail(404, "no_version");
  const link = isHttpsStrict(data.download_url ?? "") ? data.download_url : "";
  const out: Record<string, unknown> = {
    versionCode: data.version_code,
    versionName: data.version_name,
    minSupportedVersionCode: data.min_supported_code,
    releaseNotes: data.changelog,
    changelog: data.changelog,
    downloadUrl: link,
    url: link,
    forceUpdate: data.force_update,
  };
  const cur = Number(url.searchParams.get("current"));
  if (Number.isInteger(cur) && cur >= 0) out.decision = decideUpdate(cur, data.version_code, data.min_supported_code, data.force_update);
  return json(out);
}

async function providerState(): Promise<{ dc: any; list: Array<{ id: string; enabled: boolean; status: string }> }> {
  const [{ data: dc }, { data: pp }] = await Promise.all([
    db.from("donation_config").select("*").eq("id", 1).maybeSingle(),
    db.from("payment_providers").select("provider,enabled"),
  ]);
  const flags = new Map<string, boolean>((pp ?? []).map((r: any) => [r.provider, r.enabled === true]));
  const cardSet = ((dc?.card ?? "") as string).replace(/\D/g, "").length >= 12;
  const qrSet = ((dc?.qr_payload ?? "") as string).trim().length > 0;
  const list = PROVIDER_IDS.map((id) => ({ id, enabled: flags.get(id) === true, status: providerStatus(id, { cardSet, qrSet }) as string }));
  return { dc, list };
}

async function publicDonationConfig() {
  const { dc, list } = await providerState();
  if (!dc || !dc.enabled) return json({ enabled: false });
  const usable = (id: string) => list.some((p) => p.id === id && p.enabled && p.status === "available");
  // Karta/QR faqat shu usul YOQILGAN va sozlangan bo'lsa beriladi. Merchant sirlari bu yerda HECH QACHON yo'q.
  return json({
    enabled: true,
    minAmount: Math.max(1000, Number(dc.min_amount)),
    presets: (dc.presets as Array<number | string>).map(Number),
    allowCustom: dc.allow_custom,
    description: dc.description,
    instructions: dc.instructions,
    providers: list,
    bank: usable("manual_card") ? dc.bank : "",
    holder: usable("manual_card") ? dc.holder : "",
    card: usable("manual_card") ? dc.card : "",
    qrPayload: usable("qr") ? dc.qr_payload : "",
  });
}

async function publicAnnouncements() {
  const { data } = await db.from("announcements").select("id,title,text,image_url,publish_at")
    .eq("active", true).lte("publish_at", new Date().toISOString())
    .order("publish_at", { ascending: false }).limit(20);
  return (data ?? []).map((a: any) => ({
    id: a.id, title: a.title, message: a.text, imageUrl: isHttpsStrict(a.image_url ?? "") ? a.image_url : null, publishAt: a.publish_at,
  }));
}

async function publicStatus() {
  const [{ data: m }, items] = await Promise.all([
    db.from("app_config").select("value").eq("key", "maintenance").maybeSingle(),
    publicAnnouncements(),
  ]);
  return json({ maintenance: m?.value ?? { enabled: false, message: "" }, announcements: items.slice(0, 5) });
}

const topDonorRows = async () => {
  const { data, error } = await db.rpc("top_donors", { p_limit: 10 });
  if (error) return null;
  return (data ?? []).map((r: any) => ({
    rank: r.rank_position, displayName: r.display_name, anonymous: r.anonymous,
    amount: Number(r.amount), currency: r.currency, date: r.donated_at, consent: r.consent,
  }));
};

async function publicTopDonors() {
  const items = await topDonorRows();
  return items ? json({ items }) : fail(500, "server_error");
}

/** To'lov holati faqat noyob reference bo'yicha (taxmin qilib bo'lmaydi, rate limit bilan). Ism/karta qaytmaydi. */
async function publicDonationStatus(url: URL) {
  const ref = url.searchParams.get("ref");
  if (!isReference(ref)) return fail(400, "bad_request");
  const { data } = await db.from("donation_events").select("reference,status,provider,amount,currency,updated_at").eq("reference", ref).maybeSingle();
  if (!data) return fail(404, "not_found");
  return json({ reference: data.reference, status: data.status, provider: data.provider, amount: Number(data.amount), currency: data.currency, updatedAt: data.updated_at });
}

/** Anonim, minimal statistika. Faqat ilova foydalanuvchi roziligi bilan yuboradi; shaxsiy ma'lumot qabul qilinmaydi. */
async function ingest(req: Request) {
  const b = await readBody(req);
  const type = str(b.type, 20);
  const id = str(b.installId, 64);
  if (id.length < 8) return fail(400, "bad_request");
  const result = ["GOOD", "WARNING", "CRITICAL"].includes(str(b.result, 10)) ? str(b.result, 10) : null;
  const score = num(b.score);
  const sc = score !== null && score >= 0 && score <= 100 ? score : null;
  let err;
  if (type === "device") {
    ({ error: err } = await db.from("device_stats").upsert(
      { install_id: id, app_version: str(b.appVersion, 20) || null, android_sdk: num(b.sdk) },
      { onConflict: "install_id", ignoreDuplicates: true },
    ));
  } else if (type === "optimization") {
    ({ error: err } = await db.from("optimization_history").insert({ install_id: id, score: sc, result }));
  } else if (type === "game_boost") {
    ({ error: err } = await db.from("game_boost_history").insert({ install_id: id, result }));
  } else if (type === "diagnostics") {
    ({ error: err } = await db.from("diagnostics_history").insert({ install_id: id, score: sc, result }));
  } else if (type === "error") {
    ({ error: err } = await db.from("error_logs").insert({
      source: "app", message: str(b.message, 500) || "error", app_version: str(b.appVersion, 20) || null,
    }));
  } else return fail(400, "bad_request");
  return err ? fail(500, "server_error") : json({ ok: true });
}

/** Foydalanuvchi to'lov NIYATINI yozadi (pending). Muvaffaqiyatni faqat admin haqiqiy tushumni ko'rgach tasdiqlaydi. */
/** Ilova to'lov NIYATINI yozadi: har doim 'pending'. Success faqat admin tasdiqlagandan keyin (va faqat Manual card/QR). */
async function donationEvent(req: Request) {
  const b = await readBody(req);
  const amount = num(b.amount);
  if (amount === null || amount < 1000 || amount > 1_000_000_000) return fail(400, "bad_request");
  if (!isProvider(b.provider)) return fail(400, "bad_request");
  const { dc, list } = await providerState();
  if (!dc || !dc.enabled) return fail(403, "donation_disabled");
  if (amount < Number(dc.min_amount)) return fail(400, "bad_request");
  const p = list.find((x) => x.id === b.provider);
  if (!p || !p.enabled || p.status !== "available" || isGateway(b.provider)) return fail(409, "provider_not_configured");
  const anonymous = b.anonymous !== false;                 // standart: anonim
  const consent = b.consent === true;                      // ochiq rozilik bo'lmasa ism saqlanmaydi
  const name = !anonymous && consent ? cleanDonorName(b.donorName) : null;
  if (!anonymous && consent && name === null) return fail(400, "bad_request");
  const { data, error } = await db.from("donation_events").insert({
    amount, provider: b.provider, currency: "UZS", donor_name: name, anonymous: name === null, consent: name !== null,
  }).select("reference").single();
  if (error || !data) return fail(500, "server_error");
  return json({ ok: true, status: "pending", provider: b.provider, reference: data.reference });
}

// ---------- admin auth ----------
type Admin = { userId: string; email: string; sessionId: string };

/** Token Supabase Auth'da haqiqiy + admin_profiles + server tasdiqlagan (parol+OTP) sessiya. is_admin() bilan bir xil qoida. */
async function authAdmin(req: Request): Promise<Admin | null> {
  const h = req.headers.get("authorization") ?? "";
  if (!h.startsWith("Bearer ")) return null;
  const token = h.slice(7);
  if (token.length < 20 || token.length > 4096) return null;
  const sid = sessionIdOf(token);
  if (!sid) return null;
  const { data: u, error } = await db.auth.getUser(token);
  if (error || !u?.user) return null;
  const [{ data: prof }, { data: sess }] = await Promise.all([
    db.from("admin_profiles").select("email,disabled").eq("user_id", u.user.id).maybeSingle(),
    db.from("admin_sessions").select("user_id,expires_at,revoked_at").eq("session_id", sid).maybeSingle(),
  ]);
  if (!prof || prof.disabled) return null;
  if (!sess || sess.user_id !== u.user.id || sess.revoked_at || new Date(sess.expires_at).getTime() <= Date.now()) return null;
  return { userId: u.user.id, email: prof.email, sessionId: sid };
}

async function audit(a: { userId: string | null; email: string }, action: string, target: string | null, details: Record<string, unknown> = {}, result: "success" | "failure" | "denied" = "success") {
  const { error } = await db.from("audit_logs").insert({
    admin_user_id: a.userId, admin_email: a.email, action, target, details: redactDetails(details), result,
  });
  if (error) console.error("audit write failed");
}

/** 1-qadam: email + parol (Supabase Auth). Sessiya mijozga BERILMAYDI; OTP emailga yuboriladi. */
async function login(req: Request) {
  const ip = await keyHash("ip", clientIp(req.headers));
  if (!(await allow(`login:ip:${ip}`, 10, 900))) return fail(429, "rate_limited");
  const reject = async () => { await sleep(500 + Math.floor(Math.random() * 300)); return fail(401, "invalid_credentials"); };

  const b = await readBody(req);
  const email = str(b.email, 254).toLowerCase();
  const password = typeof b.password === "string" ? b.password.slice(0, 200) : "";
  if (!isEmail(email) || !password) return reject();

  const emailKey = await keyHash("email", email);
  if (!(await allow(`login:email:${emailKey}`, 5, 900))) return fail(429, "rate_limited");

  const { data: prof } = await db.from("admin_profiles").select("user_id,disabled").eq("email", email).maybeSingle();
  if (!prof || prof.disabled) return reject();

  const pw = await gotrue("/token?grant_type=password", { email, password });
  if (!pw.ok || typeof pw.json?.access_token !== "string" || pw.json?.user?.id !== prof.user_id) {
    await audit({ userId: prof.user_id, email }, "admin_login_failed", null, { step: "password" }, "failure");
    return reject();
  }
  await dropSession(pw.json.access_token); // parol sessiyasi ishlatilmaydi

  if (!(await allow(`otp:send:${emailKey}`, 5, 3600))) return fail(429, "rate_limited");
  const sent = await gotrue("/otp", { email, create_user: false });
  if (!sent.ok) return fail(sent.status === 429 ? 429 : 502, sent.status === 429 ? "rate_limited" : "otp_send_failed");

  // Eski ishlatilmagan challenge'lar bekor qilinadi: bir vaqtda faqat bitta OTP bosqichi.
  const nowIso = new Date().toISOString();
  await db.from("login_challenges").update({ used_at: nowIso }).eq("user_id", prof.user_id).is("used_at", null);
  const { data: ch, error } = await db.from("login_challenges").insert({
    user_id: prof.user_id, email, expires_at: new Date(Date.now() + CHALLENGE_TTL_SEC * 1000).toISOString(),
  }).select("id").single();
  if (error || !ch) return fail(500, "server_error");
  return json({ challengeId: ch.id, expiresIn: CHALLENGE_TTL_SEC, sentTo: maskEmail(email) });
}

/** 2-qadam: OTP. Bir martalik, muddatli, 5 urinish, replay bloklangan. Faqat shundan keyin sessiya beriladi. */
async function verifyOtp(req: Request) {
  const ip = await keyHash("ip", clientIp(req.headers));
  if (!(await allow(`otp:verify:ip:${ip}`, 30, 900))) return fail(429, "rate_limited");
  const b = await readBody(req);
  const challengeId = b.challengeId;
  const code = typeof b.code === "string" ? b.code.trim() : "";
  if (!isUuid(challengeId) || !isOtp(code)) return fail(400, "bad_request");

  const { data: ch } = await db.from("login_challenges").select("id,user_id,email,used_at,expires_at")
    .eq("id", challengeId).maybeSingle();
  if (!ch || ch.used_at || new Date(ch.expires_at).getTime() <= Date.now()) return fail(401, "challenge_expired");

  const { data: left } = await db.rpc("challenge_attempt", { p_id: challengeId, p_max: OTP_MAX_ATTEMPTS });
  if (typeof left !== "number") return fail(401, "challenge_expired");

  const v = await gotrue("/verify", { type: "email", email: ch.email, token: code });
  const at = v.json?.access_token;
  if (!v.ok || typeof at !== "string" || v.json?.user?.id !== ch.user_id) {
    await audit({ userId: ch.user_id, email: ch.email }, "admin_login_failed", null, { step: "otp", attemptsLeft: left }, "failure");
    await sleep(300);
    return json({ error: "invalid_code", attemptsLeft: left }, 401);
  }

  // Replay himoyasi: challenge faqat bir marta "iste'mol" qilinadi.
  const { data: consumed } = await db.rpc("challenge_consume", { p_id: challengeId });
  const sid = sessionIdOf(at);
  if (consumed !== true || !sid) { await dropSession(at); return fail(401, "challenge_expired"); }

  const { data: prof } = await db.from("admin_profiles").select("disabled").eq("user_id", ch.user_id).maybeSingle();
  if (!prof || prof.disabled) { await dropSession(at); return fail(401, "unauthorized"); }

  const { error } = await db.from("admin_sessions").insert({
    session_id: sid, user_id: ch.user_id, expires_at: new Date(Date.now() + ADMIN_SESSION_TTL_SEC * 1000).toISOString(),
  });
  if (error) { await dropSession(at); return fail(500, "server_error"); }
  await audit({ userId: ch.user_id, email: ch.email }, "admin_login", null, {});
  return json({ accessToken: at, refreshToken: v.json?.refresh_token ?? null, expiresIn: v.json?.expires_in ?? 3600, email: ch.email });
}

/** Access token yangilash. Faqat server tasdiqlagan, muddati o'tmagan admin sessiyasi uchun. */
async function refresh(req: Request) {
  const ip = await keyHash("ip", clientIp(req.headers));
  if (!(await allow(`refresh:ip:${ip}`, 30, 900))) return fail(429, "rate_limited");
  const b = await readBody(req);
  const rt = str(b.refreshToken, 500);
  if (rt.length < 10) return fail(400, "bad_request");
  const r = await gotrue("/token?grant_type=refresh_token", { refresh_token: rt });
  const at = r.json?.access_token;
  if (!r.ok || typeof at !== "string") return fail(401, "unauthorized");
  const sid = sessionIdOf(at);
  const uid = r.json?.user?.id;
  const [{ data: sess }, { data: prof }] = await Promise.all([
    sid ? db.from("admin_sessions").select("user_id,expires_at,revoked_at").eq("session_id", sid).maybeSingle() : Promise.resolve({ data: null }),
    db.from("admin_profiles").select("disabled").eq("user_id", uid).maybeSingle(),
  ]);
  if (!sess || sess.user_id !== uid || sess.revoked_at || new Date(sess.expires_at).getTime() <= Date.now() || !prof || prof.disabled) {
    await dropSession(at);
    return fail(401, "unauthorized");
  }
  return json({ accessToken: at, refreshToken: r.json?.refresh_token ?? null, expiresIn: r.json?.expires_in ?? 3600 });
}

async function logout(req: Request, admin: Admin) {
  await db.from("admin_sessions").update({ revoked_at: new Date().toISOString() }).eq("session_id", admin.sessionId);
  const token = (req.headers.get("authorization") ?? "").slice(7);
  await dropSession(token);
  await audit({ userId: admin.userId, email: admin.email }, "admin_logout", null, {});
  return json({ ok: true });
}

async function adminRoute(req: Request, url: URL, path: string): Promise<Response> {
  const admin = await authAdmin(req);
  if (!admin) return fail(401, "unauthorized");
  if (!(await allow(`admin:${admin.userId}`, 120, 60))) return fail(429, "rate_limited");
  const actor = { userId: admin.userId, email: admin.email };
  const d = days(url);
  const from = since(d);
  const m = req.method;

  switch (path) {
    case "/admin/me": return json({ email: admin.email });
    case "/admin/logout": return m === "POST" ? await logout(req, admin) : fail(405, "method_not_allowed");
    case "/admin/audit-logs": {
      const offset = Math.max(0, Math.min(10000, Number(url.searchParams.get("offset")) || 0));
      const { data, error } = await db.from("audit_logs").select("id,admin_email,action,target,details,created_at,result")
        .order("created_at", { ascending: false }).range(offset, offset + 99);
      return error ? fail(500, "server_error") : json({ offset, items: data ?? [] });
    }
    case "/admin/stats": {
      const [ver, maint, devices, opt, game, diag, don, errs] = await Promise.all([
        db.from("app_versions").select("version_name").eq("published", true).order("version_code", { ascending: false }).limit(1).maybeSingle(),
        db.from("app_config").select("value").eq("key", "maintenance").maybeSingle(),
        count("device_stats", from),
        count("optimization_history", from),
        count("game_boost_history", from),
        count("diagnostics_history", from),
        count("donation_events", from, (q) => q.eq("status", "confirmed")),
        count("error_logs", from),
      ]);
      const ids = new Set<string>();
      for (const t of ["optimization_history", "game_boost_history", "diagnostics_history"]) {
        const { data } = await db.from(t).select("install_id").gte("created_at", from).limit(20000);
        (data ?? []).forEach((r: { install_id: string }) => ids.add(r.install_id));
      }
      return json({
        days: d,
        users: ids.size,
        devices,
        optimizations: opt,
        gameBoosts: game,
        diagnostics: diag,
        donations: don,
        errors: errs,
        appVersion: ver.data?.version_name ?? null,
        maintenance: (maint.data?.value as { enabled?: boolean } | undefined)?.enabled ?? false,
      });
    }
    case "/admin/users": {
      const { data, error } = await db.from("device_stats").select("install_id,app_version,android_sdk,created_at")
        .gte("created_at", from).order("created_at", { ascending: false }).limit(200);
      if (error) return fail(500, "server_error");
      // To'liq install_id ko'rsatilmaydi.
      return json({ days: d, items: (data ?? []).map((r) => ({ ...r, install_id: r.install_id.slice(0, 6) + "…" })) });
    }
    case "/admin/optimization": return json(await historyStats("optimization_history", d, true));
    case "/admin/game-boost": return json(await historyStats("game_boost_history", d, false));
    case "/admin/diagnostics": return json(await historyStats("diagnostics_history", d, true));
    case "/admin/donations": {
      if (m === "POST") {
        const b = await readBody(req);
        const id = num(b.id);
        if (id === null) return fail(400, "bad_request");
        const { data: row } = await db.from("donation_events").select("id,provider,status,reference").eq("id", id).maybeSingle();
        if (!row) return fail(404, "not_found");
        if (typeof b.hidden === "boolean" && b.status === undefined) {
          if (row.status !== "success") return fail(400, "invalid_transition");
          const { error } = await db.from("donation_events").update({ hidden: b.hidden }).eq("id", id);
          if (error) return fail(500, "server_error");
          await audit(actor, "donation_visibility_changed", `donation:${row.reference}`, { hidden: b.hidden });
          return json({ ok: true });
        }
        const to = str(b.status, 10);
        const bad = donationTransition(row.provider, row.status, to);
        if (bad) {
          await audit(actor, "donation_status_change_denied", `donation:${row.reference}`, { from: row.status, to, reason: bad }, "denied");
          return fail(bad === "provider_not_configured" ? 409 : 400, bad);
        }
        const { error } = await db.from("donation_events").update({ status: to }).eq("id", id).eq("status", "pending");
        if (error) {
          await audit(actor, "donation_status_changed", `donation:${row.reference}`, { to }, "failure");
          return fail(500, "server_error");
        }
        await audit(actor, "donation_status_changed", `donation:${row.reference}`, { from: row.status, to });
        return json({ ok: true });
      }
      const sf = url.searchParams.get("status") ?? "";
      let q = db.from("donation_events")
        .select("id,reference,amount,currency,provider,status,donor_name,anonymous,consent,hidden,created_at,updated_at")
        .gte("created_at", from).order("created_at", { ascending: false }).limit(200);
      if (["pending", "success", "failed", "cancelled"].includes(sf)) q = q.eq("status", sf);
      const [{ data, error }, { data: sums }, pend] = await Promise.all([
        q,
        db.from("donation_events").select("amount").eq("status", "success").gte("created_at", from).limit(100000),
        count("donation_events", from, (x) => x.eq("status", "pending")),
      ]);
      if (error) return fail(500, "server_error");
      return json({
        days: d,
        successCount: (sums ?? []).length,
        successTotal: (sums ?? []).reduce((t: number, r: any) => t + Number(r.amount), 0),
        pendingCount: pend,
        currency: "UZS",
        items: data ?? [],
      });
    }
    case "/admin/top-donors": {
      const items = await topDonorRows();
      return items ? json({ items }) : fail(500, "server_error");
    }
    case "/admin/errors":
    case "/admin/logs": {
      const { data, error } = await db.from("error_logs").select("created_at,source,message,app_version")
        .order("created_at", { ascending: false }).limit(100);
      if (error) return fail(500, "server_error");
      const items = (data ?? []).map((r) => `${r.created_at.slice(0, 16).replace("T", " ")} [${r.source}] ${r.app_version ?? ""} ${r.message}`.trim());
      return json({ items });
    }
    case "/admin/version": {
      if (m === "POST") {
        const b = await readBody(req);
        const code = num(b.versionCode);
        const name = str(b.versionName, 30);
        const dl = str(b.downloadUrl, 500);
        const min = b.minSupportedVersionCode === undefined ? 0 : num(b.minSupportedVersionCode);
        if (code === null || code < 1 || !name || (dl !== "" && !isHttpsStrict(dl))) return fail(400, "bad_request");
        if (min === null || min < 0 || min > code) return fail(400, "bad_request");
        const notes = str(b.releaseNotes ?? b.changelog, 2000);
        const { error } = await db.from("app_versions").upsert({
          version_code: code, version_name: name, changelog: notes, download_url: dl,
          force_update: b.forceUpdate === true, min_supported_code: min, published: b.published !== false,
        }, { onConflict: "version_code" });
        if (error) return fail(500, "server_error");
        await audit(actor, "app_version_saved", `version:${code}`, { versionName: name, minSupported: min, forceUpdate: b.forceUpdate === true, published: b.published !== false });
        return json({ ok: true });
      }
      const { data } = await db.from("app_versions").select("*").order("version_code", { ascending: false }).limit(20);
      return json({ items: data ?? [] });
    }
    case "/admin/announcements": {
      if (m === "POST") {
        const b = await readBody(req);
        const id = num(b.id);
        if (id !== null && b.delete === true) {
          const { error } = await db.from("announcements").delete().eq("id", id);
          if (error) return fail(500, "server_error");
          await audit(actor, "announcement_deleted", `announcement:${id}`, {});
          return json({ ok: true });
        }
        const patch: Record<string, unknown> = {};
        if (typeof b.title === "string") patch.title = str(b.title, 120);
        if (typeof b.message === "string") { const t = str(b.message, 500); if (!t) return fail(400, "bad_request"); patch.text = t; }
        if (b.imageUrl !== undefined) {
          const u = b.imageUrl === null ? "" : str(b.imageUrl, 500);
          if (u !== "" && !isHttpsStrict(u)) return fail(400, "bad_request");
          patch.image_url = u === "" ? null : u;
        }
        if (typeof b.publishAt === "string") {
          const t = Date.parse(b.publishAt);
          if (!Number.isFinite(t)) return fail(400, "bad_request");
          patch.publish_at = new Date(t).toISOString();
        }
        if (typeof b.active === "boolean") patch.active = b.active;
        if (b.unpublish === true) patch.active = false;
        if (b.publish === true) { patch.active = true; if (patch.publish_at === undefined) patch.publish_at = new Date().toISOString(); }
        if (id === null) {
          if (typeof patch.text !== "string") return fail(400, "bad_request");
          const { error } = await db.from("announcements").insert(patch);
          if (error) return fail(500, "server_error");
          await audit(actor, "announcement_created", null, { active: patch.active !== false });
          return json({ ok: true });
        }
        if (Object.keys(patch).length === 0) return fail(400, "bad_request");
        const { error } = await db.from("announcements").update(patch).eq("id", id);
        if (error) return fail(500, "server_error");
        await audit(actor, b.publish === true ? "announcement_published" : b.unpublish === true ? "announcement_unpublished" : "announcement_updated",
          `announcement:${id}`, { changed: Object.keys(patch) });
        return json({ ok: true });
      }
      const { data } = await db.from("announcements").select("*").order("created_at", { ascending: false }).limit(50);
      return json({ items: (data ?? []).map(({ text, ...r }: any) => ({ ...r, message: text })) });
    }
    case "/admin/maintenance": {
      if (m !== "POST") return fail(405, "method_not_allowed");
      const b = await readBody(req);
      const { error } = await db.from("app_config").upsert({
        key: "maintenance", value: { enabled: b.enabled === true, message: str(b.message, 300) },
        updated_at: new Date().toISOString(),
      });
      if (error) return fail(500, "server_error");
      await audit(actor, "maintenance_changed", null, { enabled: b.enabled === true });
      return json({ ok: true });
    }
    case "/admin/settings": {
      if (m === "POST") {
        const b = await readBody(req);
        const dn = b.donation as Record<string, unknown> | undefined;
        if (dn && typeof dn === "object") {
          const presets = Array.isArray(dn.presets) ? dn.presets.map(Number).filter((n) => Number.isFinite(n) && n >= 1000 && n <= 1_000_000_000) : null;
          const patch: Record<string, unknown> = {};
          if (typeof dn.enabled === "boolean") patch.enabled = dn.enabled;
          if (presets && presets.length) patch.presets = presets;
          if (typeof dn.allowCustom === "boolean") patch.allow_custom = dn.allowCustom;
          if (typeof dn.instructions === "string") patch.instructions = str(dn.instructions, 1000);
          if (typeof dn.description === "string") patch.description = str(dn.description, 1000);
          if (typeof dn.qrPayload === "string") patch.qr_payload = str(dn.qrPayload, 1000);
          if (typeof dn.bank === "string") patch.bank = str(dn.bank, 100);
          if (typeof dn.holder === "string") patch.holder = str(dn.holder, 100);
          if (typeof dn.card === "string") {
            const card = normalizeCard(str(dn.card, 32));
            if (card === null) return fail(400, "bad_request");
            patch.card = card;
          }
          if (typeof dn.minAmount === "number") {
            const min = Math.trunc(dn.minAmount);
            if (!Number.isFinite(min) || min < 1000 || min > 1_000_000_000) return fail(400, "bad_request");
            patch.min_amount = min;
          }
          if (Object.keys(patch).length) {
            const { error } = await db.from("donation_config").update(patch).eq("id", 1);
            if (error) return fail(500, "server_error");
            // Faqat maydon NOMLARI yoziladi (karta/QR qiymati emas).
            await audit(actor, "donation_settings_changed", "donation_config", { changed: Object.keys(patch) });
          }
        }
        if (Array.isArray(b.providers)) {
          const changed: string[] = [];
          for (const it of b.providers as Array<Record<string, unknown>>) {
            if (!it || !isProvider(it.id) || typeof it.enabled !== "boolean") return fail(400, "bad_request");
            if (it.enabled && isGateway(it.id)) {
              await audit(actor, "provider_enable_denied", `provider:${it.id}`, {}, "denied");
              return fail(409, "provider_not_configured");
            }
            const { error } = await db.from("payment_providers").update({ enabled: it.enabled }).eq("provider", it.id);
            if (error) return fail(500, "server_error");
            changed.push(`${it.id}:${it.enabled ? "on" : "off"}`);
          }
          if (changed.length) await audit(actor, "payment_providers_changed", "payment_providers", { changed });
        }
        const mt = b.maintenance as Record<string, unknown> | undefined;
        if (mt && typeof mt === "object") {
          const { error } = await db.from("app_config").upsert({
            key: "maintenance", value: { enabled: mt.enabled === true, message: str(mt.message, 300) },
            updated_at: new Date().toISOString(),
          });
          if (error) return fail(500, "server_error");
          await audit(actor, "maintenance_changed", null, { enabled: mt.enabled === true });
        }
        return json({ ok: true });
      }
      const [{ data: mt }, ps] = await Promise.all([
        db.from("app_config").select("value").eq("key", "maintenance").maybeSingle(),
        providerState(),
      ]);
      // Karta raqami admin javobida ham to'liq qaytmaydi (faqat oxirgi 4 raqam).
      const digits = ((ps.dc?.card ?? "") as string).replace(/\D/g, "");
      return json({
        maintenance: mt?.value ?? { enabled: false, message: "" },
        donation: ps.dc ? { ...ps.dc, card: digits ? "•••• " + digits.slice(-4) : "", cardSet: digits.length > 0 } : null,
        providers: ps.list,
      });
    }
    default:
      return fail(404, "not_found");
  }
}

// ---------- kirish nuqtasi ----------
const CORS_HEADERS = {
  "access-control-allow-headers": "authorization, content-type",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-max-age": "600",
  vary: "Origin",
};
function withCors(res: Response, origin: string | null): Response {
  const allowed = corsOrigin(origin, ALLOWED_ORIGINS);
  const h = new Headers(res.headers);
  h.set("vary", "Origin");
  if (allowed) { h.set("access-control-allow-origin", allowed); for (const [k, v] of Object.entries(CORS_HEADERS)) h.set(k, v); }
  return new Response(res.body, { status: res.status, headers: h });
}

async function handle(req: Request): Promise<Response> {
  const url = new URL(req.url);
  const path = url.pathname.replace(/^.*?\/api(?=\/|$)/, "").replace(/\/+$/, "") || "/";
  const m = req.method;
  const ip = await keyHash("ip", clientIp(req.headers));

  if (path === "/admin/login" && m === "POST") return await login(req);
  if (path === "/admin/verify-otp" && m === "POST") return await verifyOtp(req);
  if (path === "/admin/refresh" && m === "POST") return await refresh(req);
  if (path.startsWith("/admin/")) return await adminRoute(req, url, path);

  if (m === "GET" && ["/update", "/donation-config", "/status", "/top-donors", "/announcements", "/donation-status"].includes(path)) {
    if (!(await allow(`pub:get:${ip}`, 600, 3600))) return fail(429, "rate_limited");
    if (path === "/update") return await publicUpdate(url);
    if (path === "/donation-config") return await publicDonationConfig();
    if (path === "/top-donors") return await publicTopDonors();
    if (path === "/announcements") return json({ items: await publicAnnouncements() });
    if (path === "/donation-status") {
      if (!(await allow(`pub:dstatus:${ip}`, 60, 3600))) return fail(429, "rate_limited");
      return await publicDonationStatus(url);
    }
    return await publicStatus();
  }
  if (m === "POST" && path === "/ingest") {
    if (!(await allow(`pub:ingest:${ip}`, 120, 3600))) return fail(429, "rate_limited");
    return await ingest(req);
  }
  if (m === "POST" && path === "/donation-event") {
    if (!(await allow(`pub:donation:${ip}`, 20, 3600))) return fail(429, "rate_limited");
    return await donationEvent(req);
  }
  return fail(404, "not_found");
}

Deno.serve(async (req) => {
  const origin = req.headers.get("origin");
  try {
    if (req.method === "OPTIONS") return withCors(new Response(null, { status: 204 }), origin);
    return withCors(await handle(req), origin);
  } catch (_e) {
    // Xato tafsilotlari (sirlar bo'lishi mumkin) logga yozilmaydi.
    console.error("request failed");
    return withCors(fail(500, "server_error"), origin);
  }
});
