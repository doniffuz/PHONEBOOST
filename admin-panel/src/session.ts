// Sessiya brauzer sessionStorage'da (tab yopilsa o'chadi). Parol HECH QACHON saqlanmaydi.
export interface Session { accessToken: string; refreshToken: string | null; email: string; expiresAt: number }
const KEY = "pb_admin_session";
let mem: Session | null = null;

export function loadSession(): Session | null {
  if (mem) return mem;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const s = JSON.parse(raw) as Session;
    if (typeof s.accessToken === "string" && typeof s.email === "string") { mem = s; return s; }
  } catch { /* storage yopiq yoki buzuq */ }
  return null;
}
export function saveSession(s: Session): void {
  mem = s;
  try { sessionStorage.setItem(KEY, JSON.stringify(s)); } catch { /* xotirada qoladi */ }
}
export function clearSession(): void {
  mem = null;
  try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
}
