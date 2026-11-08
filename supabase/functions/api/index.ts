// PHONEBOOST MAX backend (Supabase Edge Function, Deno).
// Bitta funksiya, yo'llar: /admin/* (token bilan himoyalangan) va ochiq /update, /donation-config, /status, /ingest, /donation-event.
// Sirlar (SUPABASE_SERVICE_ROLE_KEY, ADMIN_JWT_SECRET, ADMIN_BOOTSTRAP_*) faqat funksiya muhitida: kodda, repoda va APK ichida YO'Q.
// Hech qanday sir yoki karta ma'lumoti logga yozilmaydi.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

const JWT_SECRET = Deno.env.get("ADMIN_JWT_SECRET") ?? "";
const TOKEN_TTL_SEC = 60 * 60; // 1 soat
const enc = new TextEncoder();

// ---------- yordamchilar ----------
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status: number, code: string) => json({ error: code }, status);

const b64u = (buf: ArrayBuffer | Uint8Array) => {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = "";
  bytes.forEach((b) => (s += String.fromCharCode(b)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const unb64u = (s: string) => {
  const p = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  return Uint8Array.from(atob(p), (c) => c.charCodeAt(0));
};

async function hmacKey(usage: KeyUsage[]) {
  return crypto.subtle.importKey("raw", enc.encode(JWT_SECRET), { name: "HMAC", hash: "SHA-256" }, false, usage);
}

async function signToken(sub: string): Promise<string> {
  const payload = b64u(enc.encode(JSON.stringify({ sub, exp: Math.floor(Date.now() / 1000) + TOKEN_TTL_SEC })));
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(["sign"]), enc.encode(payload));
  return `${payload}.${b64u(sig)}`;
}

/** Tokenni tekshiradi va adminni bazadan qayta tasdiqlaydi (o'chirilgan admin darhol chiqib ketadi). */
async function authAdmin(req: Request): Promise<string | null> {
  if (JWT_SECRET.length < 32) return null;
  const h = req.headers.get("authorization") ?? "";
  if (!h.startsWith("Bearer ")) return null;
  const [payload, sig] = h.slice(7).split(".");
  if (!payload || !sig) return null;
  try {
    const ok = await crypto.subtle.verify("HMAC", await hmacKey(["verify"]), unb64u(sig), enc.encode(payload));
    if (!ok) return null;
    const { sub, exp } = JSON.parse(new TextDecoder().decode(unb64u(payload)));
    if (typeof sub !== "string" || typeof exp !== "number" || exp < Date.now() / 1000) return null;
    const { data } = await db.from("admin_users").select("id,role").eq("id", sub).maybeSingle();
    return data && data.role === "admin" ? data.id : null;
  } catch {
    return null;
  }
}

async function pbkdf2(password: string, salt: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: 210000 }, key, 256);
  return b64u(bits);
}
function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

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
async function publicUpdate() {
  const { data } = await db.from("app_versions").select("*").eq("published", true)
    .order("version_code", { ascending: false }).limit(1).maybeSingle();
  if (!data) return fail(404, "no_version");
  return json({
    versionCode: data.version_code,
    versionName: data.version_name,
    changelog: data.changelog,
    downloadUrl: HTTPS(data.download_url) ? data.download_url : "",
    forceUpdate: data.force_update,
  });
}

async function publicDonationConfig() {
  const { data } = await db.from("donation_config").select("*").eq("id", 1).maybeSingle();
  if (!data) return json({ enabled: false });
  return json({
    enabled: data.enabled,
    minAmount: Math.max(1000, Number(data.min_amount)),
    presets: (data.presets as Array<number | string>).map(Number),
    allowCustom: data.allow_custom,
    providers: data.providers,
    instructions: data.instructions,
    bank: data.bank,
    holder: data.holder,
    card: data.card,
  });
}

async function publicStatus() {
  const [{ data: m }, { data: a }] = await Promise.all([
    db.from("app_config").select("value").eq("key", "maintenance").maybeSingle(),
    db.from("announcements").select("id,text,created_at").eq("active", true)
      .order("created_at", { ascending: false }).limit(5),
  ]);
  return json({ maintenance: m?.value ?? { enabled: false, message: "" }, announcements: a ?? [] });
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
async function donationEvent(req: Request) {
  const b = await readBody(req);
  const amount = num(b.amount);
  if (amount === null || amount < 1000 || amount > 1_000_000_000) return fail(400, "bad_request");
  const { error } = await db.from("donation_events").insert({ amount, provider: str(b.provider, 20) || null });
  return error ? fail(500, "server_error") : json({ ok: true, status: "pending" });
}

// ---------- admin ----------
async function login(req: Request) {
  const b = await readBody(req);
  const email = str(b.email, 200).toLowerCase();
  const password = typeof b.password === "string" ? b.password.slice(0, 200) : "";
  if (!email || !password || JWT_SECRET.length < 32) { await sleep(400); return fail(401, "invalid_credentials"); }

  // Birinchi admin: faqat jadval bo'sh va muhit o'zgaruvchilari mos kelganda yaratiladi (sirlar faqat funksiya muhitida).
  const { count: adminCount } = await db.from("admin_users").select("id", { count: "exact", head: true });
  if ((adminCount ?? 0) === 0) {
    const be = (Deno.env.get("ADMIN_BOOTSTRAP_EMAIL") ?? "").toLowerCase();
    const bp = Deno.env.get("ADMIN_BOOTSTRAP_PASSWORD") ?? "";
    if (be && bp.length >= 10 && safeEqual(email, be) && safeEqual(password, bp)) {
      const salt = crypto.getRandomValues(new Uint8Array(16));
      await db.from("admin_users").insert({ email, salt: b64u(salt), password_hash: await pbkdf2(password, salt) });
    }
  }

  const { data: u } = await db.from("admin_users").select("*").eq("email", email).maybeSingle();
  // Foydalanuvchi bor-yo'qligidan qat'i nazar bir xil ish va kechikish (enumeration/brute-force'ni qiyinlashtiradi).
  const salt = u ? unb64u(u.salt) : new Uint8Array(16);
  const hash = await pbkdf2(password, salt);
  if (!u || !safeEqual(hash, u.password_hash)) { await sleep(600); return fail(401, "invalid_credentials"); }
  await db.from("admin_users").update({ last_login: new Date().toISOString() }).eq("id", u.id);
  return json({ token: await signToken(u.id), expiresIn: TOKEN_TTL_SEC });
}

async function adminRoute(req: Request, url: URL, path: string): Promise<Response> {
  const adminId = await authAdmin(req);
  if (!adminId) return fail(401, "unauthorized");
  const d = days(url);
  const from = since(d);
  const m = req.method;

  switch (path) {
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
        const status = str(b.status, 10);
        if (id === null || !["confirmed", "rejected"].includes(status)) return fail(400, "bad_request");
        const { error } = await db.from("donation_events").update({
          status, confirmed_at: status === "confirmed" ? new Date().toISOString() : null,
        }).eq("id", id);
        return error ? fail(500, "server_error") : json({ ok: true });
      }
      const { data, error } = await db.from("donation_events").select("id,amount,provider,status,created_at")
        .gte("created_at", from).order("created_at", { ascending: false }).limit(200);
      if (error) return fail(500, "server_error");
      const rows = data ?? [];
      const confirmed = rows.filter((r) => r.status === "confirmed");
      return json({
        days: d,
        confirmedCount: confirmed.length,
        confirmedTotal: confirmed.reduce((s, r) => s + Number(r.amount), 0),
        pendingCount: rows.filter((r) => r.status === "pending").length,
        items: rows,
      });
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
        if (code === null || code < 1 || !name || !HTTPS(dl)) return fail(400, "bad_request");
        const { error } = await db.from("app_versions").upsert({
          version_code: code, version_name: name, changelog: str(b.changelog, 2000), download_url: dl,
          force_update: b.forceUpdate === true, published: true,
        }, { onConflict: "version_code" });
        return error ? fail(500, "server_error") : json({ ok: true });
      }
      const { data } = await db.from("app_versions").select("*").order("version_code", { ascending: false }).limit(20);
      return json({ items: data ?? [] });
    }
    case "/admin/announcements": {
      if (m === "POST") {
        const b = await readBody(req);
        const id = num(b.id);
        if (id !== null) {
          const { error } = await db.from("announcements").update({ active: b.active === true }).eq("id", id);
          return error ? fail(500, "server_error") : json({ ok: true });
        }
        const text = str(b.text, 500);
        if (!text) return fail(400, "bad_request");
        const { error } = await db.from("announcements").insert({ text });
        return error ? fail(500, "server_error") : json({ ok: true });
      }
      const { data } = await db.from("announcements").select("*").order("created_at", { ascending: false }).limit(50);
      return json({ items: data ?? [] });
    }
    case "/admin/maintenance": {
      if (m !== "POST") return fail(405, "method_not_allowed");
      const b = await readBody(req);
      const { error } = await db.from("app_config").upsert({
        key: "maintenance", value: { enabled: b.enabled === true, message: str(b.message, 300) },
        updated_at: new Date().toISOString(),
      });
      return error ? fail(500, "server_error") : json({ ok: true });
    }
    case "/admin/settings": {
      if (m === "POST") {
        const b = await readBody(req);
        const dn = b.donation as Record<string, unknown> | undefined;
        if (dn && typeof dn === "object") {
          const presets = Array.isArray(dn.presets) ? dn.presets.map(Number).filter((n) => Number.isFinite(n) && n >= 1000) : null;
          const patch: Record<string, unknown> = { updated_at: new Date().toISOString() };
          if (typeof dn.enabled === "boolean") patch.enabled = dn.enabled;
          if (presets && presets.length) patch.presets = presets;
          if (typeof dn.allowCustom === "boolean") patch.allow_custom = dn.allowCustom;
          if (typeof dn.instructions === "string") patch.instructions = str(dn.instructions, 1000);
          if (typeof dn.bank === "string") patch.bank = str(dn.bank, 100);
          if (typeof dn.holder === "string") patch.holder = str(dn.holder, 100);
          if (typeof dn.card === "string") patch.card = str(dn.card, 32).replace(/[^0-9 ]/g, "");
          const { error } = await db.from("donation_config").update(patch).eq("id", 1);
          if (error) return fail(500, "server_error");
        }
        const mt = b.maintenance as Record<string, unknown> | undefined;
        if (mt && typeof mt === "object") {
          const { error } = await db.from("app_config").upsert({
            key: "maintenance", value: { enabled: mt.enabled === true, message: str(mt.message, 300) },
            updated_at: new Date().toISOString(),
          });
          if (error) return fail(500, "server_error");
        }
        return json({ ok: true });
      }
      const [{ data: dc }, { data: mt }] = await Promise.all([
        db.from("donation_config").select("*").eq("id", 1).maybeSingle(),
        db.from("app_config").select("value").eq("key", "maintenance").maybeSingle(),
      ]);
      // Karta raqami admin javobida ham to'liq qaytmaydi (faqat oxirgi 4 raqam).
      const card = (dc?.card ?? "").replace(/\D/g, "");
      return json({
        maintenance: mt?.value ?? { enabled: false, message: "" },
        donation: dc ? { ...dc, card: card ? "•••• " + card.slice(-4) : "" } : null,
      });
    }
    default:
      return fail(404, "not_found");
  }
}

// ---------- kirish nuqtasi ----------
Deno.serve(async (req) => {
  try {
    const url = new URL(req.url);
    const path = url.pathname.replace(/^.*?\/api(?=\/|$)/, "").replace(/\/+$/, "") || "/";
    if (req.method === "OPTIONS") return new Response(null, { status: 204 });

    if (path === "/admin/login" && req.method === "POST") return await login(req);
    if (path.startsWith("/admin/")) return await adminRoute(req, url, path);

    if (req.method === "GET") {
      if (path === "/update") return await publicUpdate();
      if (path === "/donation-config") return await publicDonationConfig();
      if (path === "/status") return await publicStatus();
    }
    if (req.method === "POST") {
      if (path === "/ingest") return await ingest(req);
      if (path === "/donation-event") return await donationEvent(req);
    }
    return fail(404, "not_found");
  } catch (_e) {
    // Xato tafsilotlari (sirlar bo'lishi mumkin) logga yozilmaydi.
    console.error("request failed");
    return fail(500, "server_error");
  }
});
