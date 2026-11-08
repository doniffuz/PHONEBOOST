import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { parsePresets, validCardInput } from "../format.js";
import { badge, busy, card, field, loading, notice, toggle } from "../ui.js";

interface Provider { id: string; enabled: boolean; status: string }
interface Settings {
  donation: {
    enabled: boolean; min_amount: number | string; presets: Array<number | string>; allow_custom: boolean; description: string;
    instructions: string; bank: string; holder: string; card: string; cardSet: boolean; qr_payload: string;
  } | null;
  providers: Provider[];
}
const LABEL: Record<string, string> = { manual_card: "Manual card transfer", qr: "QR code", click: "Click", payme: "Payme", uzum: "Uzum", tbc: "TBC" };

export async function renderDonationSettings(root: HTMLElement): Promise<void> {
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Donation Settings")), loading());
  const msg = h("div", {});
  let s: Settings;
  try { s = await api<Settings>("/admin/settings"); } catch (e) { mount(root, h("h1", {}, "Donation Settings"), notice("error", errorMessage(e))); return; }
  const d = s.donation;
  if (!d) { mount(root, h("h1", {}, "Donation Settings"), notice("error", "Donation config row is missing in the database.")); return; }

  const enabled = toggle("Donations enabled (visible in the app)", d.enabled);
  const allowCustom = toggle("Allow custom amount", d.allow_custom);
  const min = h("input", { type: "number", min: 1000, step: 1000, value: String(Number(d.min_amount)) });
  const presets = h("input", { type: "text", value: d.presets.map(Number).join(", ") });
  const description = h("textarea", { rows: 3, maxlength: 1000 }); description.value = d.description;
  const instructions = h("textarea", { rows: 3, maxlength: 1000 }); instructions.value = d.instructions;
  const bank = h("input", { type: "text", maxlength: 100, value: d.bank });
  const holder = h("input", { type: "text", maxlength: 100, value: d.holder });
  const cardIn = h("input", { type: "text", inputmode: "numeric", autocomplete: "off", maxlength: 32, placeholder: d.cardSet ? `${d.card} (leave empty to keep)` : "Card number for manual transfers" });
  let clearCard = false;
  const qr = h("textarea", { rows: 3, maxlength: 1000, placeholder: "Payment link or QR text (https://…). Never put card PIN/CVV here." }); qr.value = d.qr_payload;
  const provToggles = s.providers.map((p) => ({ p, t: toggle(LABEL[p.id] ?? p.id, p.enabled) }));
  const btn = h("button", { class: "btn primary", type: "submit" }, "Save settings");

  const form = h("form", { class: "stack", novalidate: true, onsubmit: (e: Event) => {
    e.preventDefault();
    const m = Number(min.value);
    if (!Number.isInteger(m) || m < 1000) { mount(msg, notice("error", "Minimum amount must be at least 1000.")); return; }
    const pr = parsePresets(presets.value);
    if (pr.length === 0) { mount(msg, notice("error", "Provide at least one preset amount (≥ 1000).")); return; }
    const donation: Record<string, unknown> = {
      enabled: enabled.input.checked, allowCustom: allowCustom.input.checked, minAmount: m, presets: pr,
      description: description.value, instructions: instructions.value, bank: bank.value, holder: holder.value, qrPayload: qr.value,
    };
    if (clearCard) donation.card = "";
    else if (cardIn.value.trim() !== "") {
      const c = validCardInput(cardIn.value);
      if (!c) { mount(msg, notice("error", "Card number must contain 12–19 digits.")); return; }
      donation.card = c;
    }
    busy(btn, async () => {
      try {
        await api("/admin/settings", { method: "POST", body: { donation, providers: provToggles.map(({ p, t }) => ({ id: p.id, enabled: t.input.checked })) } });
        cardIn.value = "";
        mount(msg, notice("ok", "Saved. Changes are live in the app."));
      } catch (err) { mount(msg, notice("error", errorMessage(err))); }
    });
  } },
    card("General", enabled.el, allowCustom.el,
      h("div", { class: "grid two" }, field("Minimum amount (UZS)", min), field("Preset amounts", presets, "Comma separated, ≥ 1000")),
      field("Description", description, "Shown above the payment options in the app"), field("Instructions", instructions)),
    card("Payment methods",
      h("p", { class: "muted" }, "Click, Payme, Uzum and TBC need a merchant integration with server-side secrets. None is connected, so they are shown as Not configured and can never be marked successful. No fake payments."),
      h("div", { class: "stack" }, provToggles.map(({ p, t }) => h("div", { class: "row between wrap provider" }, t.el, badge(p.status))))),
    card("Manual payment (bank card transfer)",
      h("p", { class: "muted" }, "The card number is shown to donors so they can transfer money. Never enter CVV, expiry or PIN. It is stored server-side and never written to audit logs."),
      h("div", { class: "grid two" }, field("Bank", bank), field("Card holder", holder)),
      field("Card number", cardIn, d.cardSet ? `Currently set: ${d.card}` : "Not set — manual card stays Not configured until you add it."),
      d.cardSet ? h("button", { type: "button", class: "btn ghost sm", onclick: (ev: Event) => { clearCard = !clearCard; (ev.currentTarget as HTMLElement).textContent = clearCard ? "Card will be removed on save (click to undo)" : "Remove saved card"; } }, "Remove saved card") : null),
    card("QR configuration", field("QR payload / link", qr, "Provide a payment link or text. QR stays Not configured while empty.")),
    btn);

  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Donation Settings")), msg, form);
}
