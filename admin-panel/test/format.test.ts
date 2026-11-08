// node --experimental-strip-types --test test/format.test.ts  (src/format.ts DOM'ga bog'liq emas)
import test from "node:test";
import assert from "node:assert/strict";
import { fmtAmount, fmtDate, fromLocalInput, isHttpsUrl, parsePresets, statusLabel, statusTone, toLocalInput, validCardInput } from "../src/format.ts";

test("fmtAmount", () => {
  assert.equal(fmtAmount(1234567), "1,234,567 UZS");
  assert.equal(fmtAmount("5000"), "5,000 UZS");
  assert.equal(fmtAmount(null), "—");
  assert.equal(fmtAmount("abc"), "—");
});
test("fmtDate", () => {
  assert.equal(fmtDate(null), "—");
  assert.equal(fmtDate("garbage"), "—");
  assert.notEqual(fmtDate("2026-11-08T10:00:00Z"), "—");
});
test("datetime-local aylanishi", () => {
  assert.equal(fromLocalInput(""), null);
  assert.equal(fromLocalInput("nope"), null);
  const iso = fromLocalInput("2030-05-06T07:08");
  assert.ok(iso && iso.endsWith("Z"));
  assert.equal(toLocalInput(iso), "2030-05-06T07:08");
  assert.equal(toLocalInput(null), "");
});
test("parsePresets: min, noyob, tartib, limit", () => {
  assert.deepEqual(parsePresets("10000, 1000; 5 000, 1000, 50, x"), [1000, 5000, 10000]);
  assert.deepEqual(parsePresets(""), []);
  assert.equal(parsePresets(Array.from({ length: 30 }, (_, i) => String(1000 + i)).join(",")).length, 12);
});
test("validCardInput", () => {
  assert.equal(validCardInput(""), "");
  assert.equal(validCardInput("  "), "");
  assert.equal(validCardInput("1234 5678 9012 3456"), "1234 5678 9012 3456");
  assert.equal(validCardInput("1234"), null);
  assert.equal(validCardInput("12345678901234567890"), null);
});
test("isHttpsUrl", () => {
  assert.ok(isHttpsUrl("https://example.com/a"));
  for (const b of ["http://example.com", "javascript:alert(1)", "https://a b", "ftp://x"]) assert.equal(isHttpsUrl(b), false);
});
test("status ranglari/yozuvlari: not_configured sariq, gateway success ko'rinmaydi", () => {
  assert.equal(statusTone("success"), "good");
  assert.equal(statusTone("pending"), "warn");
  assert.equal(statusTone("failed"), "bad");
  assert.equal(statusTone("not_configured"), "warn");
  assert.equal(statusLabel("not_configured"), "Not configured");
  assert.equal(statusLabel("cancelled"), "Cancelled");
});
