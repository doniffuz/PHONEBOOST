// Sof (toza) yordamchilar: Deno'ga bog'liq emas, shuning uchun Node/Deno'da test qilinadi (lib.test.ts).

export const OTP_RE = /^\d{6}$/;
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/;

export const isOtp = (v: unknown): v is string => typeof v === "string" && OTP_RE.test(v);
export const isUuid = (v: unknown): v is string => typeof v === "string" && UUID_RE.test(v);
export const isEmail = (v: unknown): v is string => typeof v === "string" && v.length <= 254 && EMAIL_RE.test(v);

/** d***@g***.com ko'rinishida (to'liq email javobda qaytmaydi). */
export function maskEmail(email: string): string {
  const [u, d = ""] = email.split("@");
  const dot = d.lastIndexOf(".");
  const host = dot > 0 ? d.slice(0, dot) : d;
  const tld = dot > 0 ? d.slice(dot) : "";
  return `${u.slice(0, 1)}***@${host.slice(0, 1)}***${tld}`;
}

/** JWT payload'ni O'QIYDI (imzoni tekshirmaydi: imzoni Supabase Auth tekshirgan bo'lishi shart). */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const p = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = p + "===".slice((p.length + 3) % 4);
    const json = typeof atob === "function" ? atob(pad) : Buffer.from(pad, "base64").toString("binary");
    const obj = JSON.parse(decodeURIComponent(escape(json)));
    return obj && typeof obj === "object" ? obj : null;
  } catch {
    return null;
  }
}

export function sessionIdOf(token: string): string | null {
  const sid = decodeJwtPayload(token)?.session_id;
  return isUuid(sid) ? sid : null;
}

/** Mijoz IP'si (Supabase gateway x-forwarded-for / cf-connecting-ip beradi). Topilmasa "unknown". */
export function clientIp(h: { get(name: string): string | null }): string {
  const raw = h.get("cf-connecting-ip") ?? h.get("x-forwarded-for") ?? "";
  const first = raw.split(",")[0].trim();
  return first && first.length <= 64 ? first : "unknown";
}

/** Origin allowlist (ALLOWED_ORIGINS, vergul bilan). "*" HECH QACHON qaytarilmaydi. */
export function corsOrigin(origin: string | null, allowed: string): string | null {
  if (!origin) return null;
  const list = allowed.split(",").map((s) => s.trim()).filter(Boolean);
  return list.includes(origin) ? origin : null;
}

const SENSITIVE_KEYS = /card|password|pass|otp|token|secret|key|code/i;
/** Audit log tafsilotlari: sezgir maydonlarning QIYMATI yozilmaydi, faqat o'zgargani ("changed") belgilanadi. */
export function redactDetails(obj: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (SENSITIVE_KEYS.test(k)) out[k] = "[changed]";
    else if (typeof v === "string") out[k] = v.slice(0, 200);
    else if (typeof v === "number" || typeof v === "boolean" || v === null) out[k] = v;
    else if (Array.isArray(v)) out[k] = v.slice(0, 20).map((x) => (typeof x === "number" || typeof x === "boolean" ? x : String(x).slice(0, 50)));
    else out[k] = "[object]";
  }
  return out;
}

/** Karta: faqat raqam/bo'shliq, 12-19 raqam. Aks holda null (bo'sh qator = o'chirish uchun "" qaytadi). */
export function normalizeCard(v: string): string | null {
  const cleaned = v.replace(/[^0-9 ]/g, "").trim();
  if (cleaned === "") return "";
  const digits = cleaned.replace(/\s/g, "");
  return digits.length >= 12 && digits.length <= 19 ? cleaned : null;
}

// ---------- Donation / provayder / yangilanish qoidalari (sof, testlanadi) ----------
export const PROVIDER_IDS = ["manual_card", "qr", "click", "payme", "uzum", "tbc"] as const;
export type ProviderId = (typeof PROVIDER_IDS)[number];
/** Merchant integratsiyasi YO'Q provayderlar: hech qachon avtomatik/qo'lda success bo'lmaydi. */
export const GATEWAY_IDS: readonly string[] = ["click", "payme", "uzum", "tbc"];
export const DONATION_STATUSES = ["pending", "success", "failed", "cancelled"] as const;

export const isProvider = (v: unknown): v is ProviderId => typeof v === "string" && (PROVIDER_IDS as readonly string[]).includes(v);
export const isGateway = (id: string): boolean => GATEWAY_IDS.includes(id);
export const REFERENCE_RE = /^PB-[0-9A-F]{16}$/;
export const isReference = (v: unknown): v is string => typeof v === "string" && REFERENCE_RE.test(v);
/** DB constraint'lari bilan bir xil: faqat kichik harfli https:// */
export const isHttpsStrict = (v: string): boolean => /^https:\/\/[^\s]+$/.test(v) && v.length <= 500;

export type ProviderStatus = "available" | "not_configured";
/** "available" = haqiqatan ishlatsa bo'ladi. Gateway'lar (Click/Payme/Uzum/TBC) har doim "not_configured". */
export function providerStatus(id: string, o: { cardSet: boolean; qrSet: boolean }): ProviderStatus {
  if (id === "manual_card") return o.cardSet ? "available" : "not_configured";
  if (id === "qr") return o.qrSet ? "available" : "not_configured";
  return "not_configured";
}

export type TransitionError = "status_final" | "invalid_transition" | "provider_not_configured";
/** Admin status o'zgartirishi. null = ruxsat. DB trigger bilan bir xil qoida (ikki qavat himoya). */
export function donationTransition(provider: string, from: string, to: string): TransitionError | null {
  if (!(DONATION_STATUSES as readonly string[]).includes(to) || to === "pending") return "invalid_transition";
  if (from !== "pending") return "status_final";
  if (to === "success" && isGateway(provider)) return "provider_not_configured";
  return null;
}

/** Donor ismi: boshqaruv belgilarisiz, bo'shliqlar yig'ilgan, 1..60. Yaroqsiz -> null. */
export function cleanDonorName(v: unknown): string | null {
  if (typeof v !== "string") return null;
  // deno-lint-ignore no-control-regex
  const s = v.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim();
  return s.length >= 1 && s.length <= 60 ? s : null;
}

export type UpdateDecision = { updateAvailable: boolean; force: boolean; optional: boolean; unsupported: boolean };
/** Android UpdateLogic.decide bilan bir xil: minimal versiyadan past ilova MAJBURIY yangilanadi. */
export function decideUpdate(current: number, latest: number, minSupported: number, forceFlag: boolean): UpdateDecision {
  const unsupported = current < minSupported;
  const updateAvailable = latest > current;
  const force = updateAvailable && (forceFlag || unsupported);
  return { updateAvailable, force, optional: updateAvailable && !force, unsupported };
}
