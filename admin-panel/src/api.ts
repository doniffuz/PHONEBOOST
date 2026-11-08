import { clearSession, loadSession, saveSession, type Session } from "./session.js";

export class ApiError extends Error {
  constructor(public status: number, public code: string, public extra: Record<string, unknown> = {}) { super(code); }
}

const base = (): string => (window.PB_ADMIN_CONFIG?.apiBase ?? "").replace(/\/+$/, "");
let onLoggedOut: () => void = () => undefined;
export const setLogoutHandler = (fn: () => void): void => { onLoggedOut = fn; };

async function raw(path: string, init: { method?: string; body?: unknown; token?: string } = {}): Promise<unknown> {
  if (!base().startsWith("https://")) throw new ApiError(0, "not_configured");
  let res: Response;
  try {
    res = await fetch(base() + path, {
      method: init.method ?? "GET",
      headers: { ...(init.body !== undefined ? { "content-type": "application/json" } : {}), ...(init.token ? { authorization: `Bearer ${init.token}` } : {}) },
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
      cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer",
    });
  } catch { throw new ApiError(0, "network_error"); }
  let json: Record<string, unknown> = {};
  try { json = (await res.json()) as Record<string, unknown>; } catch { /* bo'sh javob */ }
  if (!res.ok) throw new ApiError(res.status, typeof json.error === "string" ? json.error : "server_error", json);
  return json;
}

// ---------- login ----------
export interface Challenge { challengeId: string; expiresIn: number; sentTo: string }
export const login = (email: string, password: string) => raw("/admin/login", { method: "POST", body: { email, password } }) as Promise<Challenge>;

export async function verifyOtp(challengeId: string, code: string): Promise<Session> {
  const r = (await raw("/admin/verify-otp", { method: "POST", body: { challengeId, code } })) as { accessToken: string; refreshToken: string | null; expiresIn: number; email: string };
  const s: Session = { accessToken: r.accessToken, refreshToken: r.refreshToken, email: r.email, expiresAt: Date.now() + r.expiresIn * 1000 };
  saveSession(s);
  return s;
}

let refreshing: Promise<boolean> | null = null;
async function refresh(): Promise<boolean> {
  const s = loadSession();
  if (!s?.refreshToken) return false;
  refreshing ??= (async () => {
    try {
      const r = (await raw("/admin/refresh", { method: "POST", body: { refreshToken: s.refreshToken } })) as { accessToken: string; refreshToken: string | null; expiresIn: number };
      saveSession({ ...s, accessToken: r.accessToken, refreshToken: r.refreshToken ?? s.refreshToken, expiresAt: Date.now() + r.expiresIn * 1000 });
      return true;
    } catch { return false; } finally { refreshing = null; }
  })();
  return refreshing;
}

/** Autentifikatsiyalangan admin so'rovi. 401 bo'lsa bir marta yangilaydi, bo'lmasa chiqaradi. */
export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  let s = loadSession();
  if (!s) { onLoggedOut(); throw new ApiError(401, "unauthorized"); }
  if (s.expiresAt - Date.now() < 60_000) { await refresh(); s = loadSession(); }
  if (!s) { onLoggedOut(); throw new ApiError(401, "unauthorized"); }
  try {
    return (await raw(path, { ...init, token: s.accessToken })) as T;
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      if (await refresh()) {
        const s2 = loadSession();
        if (s2) return (await raw(path, { ...init, token: s2.accessToken })) as T;
      }
      clearSession(); onLoggedOut();
    }
    throw e;
  }
}

export async function logout(): Promise<void> {
  try { await api("/admin/logout", { method: "POST", body: {} }); } catch { /* baribir chiqamiz */ }
  clearSession();
}

export function errorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return "Unexpected error.";
  const map: Record<string, string> = {
    not_configured: "API address is not configured (public/config.js).",
    network_error: "Cannot reach the server. Check your connection.",
    invalid_credentials: "Invalid email or password.",
    rate_limited: "Too many attempts. Please wait and try again.",
    challenge_expired: "The code expired or was already used. Sign in again.",
    invalid_code: "Incorrect code.",
    unauthorized: "Session expired. Please sign in again.",
    otp_send_failed: "Could not send the email code. Try again shortly.",
    bad_request: "Some fields are invalid.",
    provider_not_configured: "This payment provider is not configured (no merchant integration), so it cannot be enabled or marked successful.",
    status_final: "This donation already has a final status.",
    invalid_transition: "That status change is not allowed.",
    donation_disabled: "Donations are disabled.",
    server_error: "Server error. Try again.",
  };
  return map[e.code] ?? `Request failed (${e.code}).`;
}
