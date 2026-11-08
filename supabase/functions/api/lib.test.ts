// Ishga tushirish: node --experimental-strip-types --test supabase/functions/api/lib.test.ts   (yoki: deno test)
import test from "node:test";
import assert from "node:assert/strict";
import { clientIp, corsOrigin, decodeJwtPayload, isEmail, isOtp, isUuid, maskEmail, normalizeCard, redactDetails, sessionIdOf } from "./lib.ts";

const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString("base64url");
const jwt = (p: unknown) => `${b64({ alg: "HS256" })}.${b64(p)}.sig`;

test("OTP faqat 6 raqam", () => {
  assert.ok(isOtp("123456"));
  for (const bad of ["12345", "1234567", "12345a", " 123456", "", 123456, null, undefined]) assert.equal(isOtp(bad), false);
});

test("email/uuid validatsiya", () => {
  assert.ok(isEmail("a@b.co"));
  assert.equal(isEmail("a b@c.com"), false);
  assert.equal(isEmail("x".repeat(300) + "@a.com"), false);
  assert.ok(isUuid("123e4567-e89b-12d3-a456-426614174000"));
  assert.equal(isUuid("nope"), false);
});

test("maskEmail to'liq emailni oshkor qilmaydi", () => {
  const m = maskEmail("donyorabdullayev892@gmail.com");
  assert.equal(m, "d***@g***.com");
  assert.ok(!m.includes("abdullayev"));
});

test("JWT: session_id o'qiladi, buzuq token null", () => {
  const sid = "123e4567-e89b-12d3-a456-426614174000";
  assert.equal(sessionIdOf(jwt({ session_id: sid })), sid);
  assert.equal(sessionIdOf(jwt({ session_id: "x" })), null);
  assert.equal(sessionIdOf(jwt({})), null);
  assert.equal(decodeJwtPayload("a.b"), null);
  assert.equal(decodeJwtPayload("###.###.###"), null);
});

test("clientIp", () => {
  const h = (m: Record<string, string>) => ({ get: (k: string) => m[k] ?? null });
  assert.equal(clientIp(h({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" })), "1.2.3.4");
  assert.equal(clientIp(h({ "cf-connecting-ip": "9.9.9.9", "x-forwarded-for": "1.1.1.1" })), "9.9.9.9");
  assert.equal(clientIp(h({})), "unknown");
});

test("CORS: faqat allowlist, '*' hech qachon", () => {
  assert.equal(corsOrigin("https://admin.example.com", "https://admin.example.com, https://b.com"), "https://admin.example.com");
  assert.equal(corsOrigin("https://evil.com", "https://admin.example.com"), null);
  assert.equal(corsOrigin("https://x.com", "*"), null);
  assert.equal(corsOrigin(null, "https://a.com"), null);
  assert.equal(corsOrigin("https://a.com", ""), null);
});

test("audit: sezgir maydonlar qiymati yozilmaydi", () => {
  const r = redactDetails({ card: "SECRET-CARD-VALUE", password: "p", enabled: true, bank: "X", nested: { a: 1 }, presets: [1000, 5000] });
  assert.equal(r.card, "[changed]");
  assert.equal(r.password, "[changed]");
  assert.equal(r.enabled, true);
  assert.equal(r.bank, "X");
  assert.equal(r.nested, "[object]");
  assert.deepEqual(r.presets, [1000, 5000]);
  assert.ok(!JSON.stringify(r).includes("SECRET"));
});

test("normalizeCard", () => {
  assert.equal(normalizeCard(""), "");
  assert.equal(normalizeCard("abc"), "");
  assert.equal(normalizeCard("123456789012345"), "123456789012345");
  assert.equal(normalizeCard("1234"), null);
});

import { cleanDonorName, decideUpdate, donationTransition, isGateway, isHttpsStrict, isProvider, isReference, providerStatus } from "./lib.ts";

test("provayderlar: faqat 6 ta ma'lum provayder", () => {
  for (const ok of ["manual_card", "qr", "click", "payme", "uzum", "tbc"]) assert.ok(isProvider(ok));
  for (const bad of ["paypal", "", null, 1, "CLICK"]) assert.equal(isProvider(bad), false);
});

test("Click/Payme/Uzum/TBC har doim 'not_configured' (soxta ulanish yo'q)", () => {
  for (const g of ["click", "payme", "uzum", "tbc"]) {
    assert.ok(isGateway(g));
    assert.equal(providerStatus(g, { cardSet: true, qrSet: true }), "not_configured");
  }
});

test("Manual card / QR: sozlanmaguncha 'not_configured'", () => {
  assert.equal(providerStatus("manual_card", { cardSet: false, qrSet: true }), "not_configured");
  assert.equal(providerStatus("manual_card", { cardSet: true, qrSet: false }), "available");
  assert.equal(providerStatus("qr", { cardSet: true, qrSet: false }), "not_configured");
  assert.equal(providerStatus("qr", { cardSet: false, qrSet: true }), "available");
});

test("donation status o'tishlari", () => {
  assert.equal(donationTransition("manual_card", "pending", "success"), null);
  assert.equal(donationTransition("qr", "pending", "failed"), null);
  assert.equal(donationTransition("qr", "pending", "cancelled"), null);
  assert.equal(donationTransition("click", "pending", "success"), "provider_not_configured");
  assert.equal(donationTransition("tbc", "pending", "success"), "provider_not_configured");
  assert.equal(donationTransition("click", "pending", "failed"), null);
  for (const from of ["success", "failed", "cancelled"]) assert.equal(donationTransition("qr", from, "success"), "status_final");
  assert.equal(donationTransition("qr", "pending", "pending"), "invalid_transition");
  assert.equal(donationTransition("qr", "pending", "confirmed"), "invalid_transition");
});

test("donor ismi tozalanadi", () => {
  assert.equal(cleanDonorName("  Ali   Valiyev "), "Ali Valiyev");
  assert.equal(cleanDonorName("A\u0000B\nC"), "A B C");
  assert.equal(cleanDonorName(""), null);
  assert.equal(cleanDonorName("   "), null);
  assert.equal(cleanDonorName("x".repeat(61)), null);
  assert.equal(cleanDonorName(5), null);
});

test("reference va https tekshiruvi", () => {
  assert.ok(isReference("PB-0123456789ABCDEF"));
  for (const bad of ["PB-0123", "pb-0123456789abcdef", "PB-0123456789ABCDEG", null]) assert.equal(isReference(bad), false);
  assert.ok(isHttpsStrict("https://example.com/a.png"));
  for (const bad of ["http://x.com", "HTTPS://x.com", "javascript:alert(1)", "https://a b", "https://" + "x".repeat(500)]) assert.equal(isHttpsStrict(bad), false);
});

test("minimal versiya: eski ilova MAJBURIY yangilanadi", () => {
  assert.deepEqual(decideUpdate(3, 5, 4, false), { updateAvailable: true, force: true, optional: false, unsupported: true });
  assert.deepEqual(decideUpdate(4, 5, 4, false), { updateAvailable: true, force: false, optional: true, unsupported: false });
  assert.deepEqual(decideUpdate(4, 5, 0, true), { updateAvailable: true, force: true, optional: false, unsupported: false });
  assert.deepEqual(decideUpdate(5, 5, 0, true), { updateAvailable: false, force: false, optional: false, unsupported: false });
});

test("audit: karta/OTP/parol qiymati yozilmaydi", () => {
  const r = redactDetails({ card: "1111222233334444", otp: "123456", password: "x", token: "t", changed: ["card"], ok: true });
  assert.equal(r.card, "[changed]"); assert.equal(r.otp, "[changed]"); assert.equal(r.password, "[changed]"); assert.equal(r.token, "[changed]");
  assert.equal(JSON.stringify(r).includes("8600"), false);
});
