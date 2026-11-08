// Sof yordamchilar (DOM'ga bog'liq emas) — Node'da test qilinadi (test/format.test.ts).

export function fmtAmount(n: number | string | null | undefined, currency = "UZS"): string {
  if (n === null || n === undefined || n === "") return "—"; // Number(null) === 0: yo'q summani "0" deb ko'rsatmaymiz
  const v = Number(n);
  if (!Number.isFinite(v)) return "—";
  return `${new Intl.NumberFormat("en-US").format(v)} ${currency}`;
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const t = Date.parse(iso);
  return Number.isFinite(t) ? new Date(t).toLocaleString() : "—";
}

/** ISO -> <input type="datetime-local"> qiymati (mahalliy vaqt). */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return "";
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return "";
  const d = new Date(t);
  const p = (x: number) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** <input type="datetime-local"> -> ISO (UTC). Bo'sh/noto'g'ri -> null. */
export function fromLocalInput(v: string): string | null {
  if (!v) return null;
  const t = Date.parse(v);
  return Number.isFinite(t) ? new Date(t).toISOString() : null;
}

/** "1000, 5 000; 10000" -> [1000, 5000, 10000] (>= min, noyob, o'sish tartibida, 12 tagacha). */
export function parsePresets(v: string, min = 1000): number[] {
  const nums = v.split(/[,;\n]+/).map((s) => Number(s.replace(/\s/g, ""))).filter((n) => Number.isInteger(n) && n >= min && n <= 1_000_000_000);
  return [...new Set(nums)].sort((a, b) => a - b).slice(0, 12);
}

/** Karta kiritish: faqat raqam/bo'shliq, 12–19 raqam; bo'sh = "" (o'chirish); noto'g'ri = null. */
export function validCardInput(v: string): string | null {
  const cleaned = v.replace(/[^0-9 ]/g, "").trim();
  if (cleaned === "") return "";
  const digits = cleaned.replace(/\s/g, "").length;
  return digits >= 12 && digits <= 19 ? cleaned : null;
}

export function isHttpsUrl(v: string): boolean {
  return /^https:\/\/[^\s]+$/i.test(v) && v.length <= 500;
}

export type Tone = "good" | "warn" | "bad" | "info" | "muted";
export function statusTone(status: string): Tone {
  switch (status) {
    case "success": case "available": return "good";
    case "pending": case "not_configured": return "warn";
    case "failed": return "bad";
    case "cancelled": case "disabled": return "muted";
    default: return "info";
  }
}
export function statusLabel(status: string): string {
  return status === "not_configured" ? "Not configured" : status.charAt(0).toUpperCase() + status.slice(1).replace(/_/g, " ");
}
